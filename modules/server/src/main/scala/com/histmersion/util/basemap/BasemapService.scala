package com.histmersion.util.basemap

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.{BackgroundJob, Deps}

import sttp.model.StatusCode

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.util.Using

/** The terrain base map on this machine (docs/research-terrain-basemap.md): where its two extracts are, the fetch job
  * that downloads them, their removal, and the tiles served to the web page.
  *
  * The extracts are a CACHE — fetched again at will — so they live in the XDG cache directory (`basemap.dir` moves
  * them), never in the data directory with the corpus. `http` and `freeSpace` are the seams a test substitutes for the
  * network and the disk.
  */
final class BasemapService(deps: Deps, http: () => RangeHttp, freeSpace: Path => Long = BasemapService.usable):

  private def cfg = deps.cfg.basemap
  private val slot = BackgroundJob.Slot()

  /** The open archives, by file, with the file stamp they were opened at. */
  private val open = ConcurrentHashMap[Path, (String, PmTilesFile)]()

  def dir: Path = BasemapService.dir(deps.cfg)

  def path(l: BasemapLayer): Path = dir.resolve(l.file)

  /** The unfinished download of a layer: its range requests, kept until it is assembled. */
  def work(l: BasemapLayer): Path = dir.resolve(s".${l.wire}.part")

  private def attribution(l: BasemapLayer): String = l match
    case BasemapLayer.Vector     => cfg.vector.attribution
    case BasemapLayer.Terrain    => cfg.terrain.attribution
    case BasemapLayer.Labels     => cfg.labels.attribution
    case BasemapLayer.Bathymetry => cfg.bathymetry.attribution

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  /** A stamp of the file as it is now (size, modification time): it changes with each fetch. */
  private def stamp(p: Path): Option[String] =
    if !Files.isRegularFile(p) then None
    else Some(s"${Files.size(p).toHexString}${Files.getLastModifiedTime(p).toMillis.toHexString}")

  /** The layer's archive, opened once and again only when the file changed. */
  def reader(l: BasemapLayer): Either[String, Option[PmTilesFile]] = readerAt(path(l))

  /** The archive at `p` (a layer's, a focus region's), opened once and again only when the file changed. */
  def readerAt(p: Path): Either[String, Option[PmTilesFile]] =
    stamp(p) match
      case None =>
        Option(open.remove(p)).foreach(_._2.close())
        Right(None)
      case Some(s) =>
        Option(open.get(p)) match
          case Some((at, f)) if at == s => Right(Some(f))
          case _                        =>
            synchronized {
              Option(open.get(p)) match
                case Some((at, f)) if at == s => Right(Some(f))
                case old                      =>
                  PmTilesFile.open(p).map { f =>
                    open.put(p, (s, f))
                    // an archive replaced while a request reads it: the old channel stays readable
                    // on macOS and Linux until closed; requests that hold it finish first
                    old.foreach(o => BasemapService.closeLater(o._2))
                    Some(f)
                  }
            }

  def info(l: BasemapLayer): BasemapLayerInfo =
    val p = path(l)
    val base = BasemapLayerInfo(l.wire, present = false, file = p.toString, attribution = attribution(l))
    reader(l) match
      case Left(e) =>
        base.copy(
          present = Files.isRegularFile(p),
          bytes = scala.util.Try(Files.size(p)).getOrElse(0L),
          error = Some(e)
        )
      case Right(None)    => base
      case Right(Some(f)) =>
        val h = f.header
        val mine = f.metadata.hcursor.downField("histmersion")
        val credit = if l != BasemapLayer.Bathymetry then base.attribution
        else
          BasemapService.seaCredit(
            f.metadata.hcursor.get[String]("attribution").toOption,
            cfg.bathymetry.attribution,
            cfg.bathymetry.onDemand.enabled
          )
        base.copy(
          present = true,
          attribution = credit,
          bytes = f.size,
          minZoom = Some(h.minZoom),
          maxZoom = Some(h.maxZoom),
          bbox = Some(s"${h.minLon},${h.minLat},${h.maxLon},${h.maxLat}"),
          tiles = Some(h.addressedTiles),
          tileType = Some(h.tileType.mime),
          source = mine.get[String]("build").toOption.orElse(mine.get[String]("source").toOption),
          fetchedAt = mine.get[String]("fetchedAt").toOption,
          // the relief's tiles also come from the focus regions (and, deeper, from both): their URLs change
          // when a region is added or replaced, so a browser never keeps a tile resampled before
          version = if l == BasemapLayer.Terrain then stamp(p).map(_ + focusStamp) else stamp(p),
          kept = Option.when(l == BasemapLayer.Vector)(
            Basemap.keptLine(mine.get[Boolean]("keepAll").getOrElse(false), mine.get[String]("drop").toOption)
          ),
          onDemand = onDemandOf(l).map(_.status),
          focus = if l == BasemapLayer.Terrain then focusInfo else Nil,
          dropped = if l != BasemapLayer.Vector then Nil
          else Basemap.droppedLayers(mine.get[Boolean]("keepAll").getOrElse(false), mine.get[String]("drop").toOption)
        )

  def status: BasemapStatus =
    val layers = BasemapLayer.values.toList.map(info)
    def ok(l: BasemapLayer) = layers.exists(i => i.layer == l.wire && i.present && i.error.isEmpty)
    val vector = layers.find(i => i.layer == BasemapLayer.Vector.wire && i.present && i.error.isEmpty)
    val states = BaseStyles.states(vector.map(_.dropped))
    BasemapStatus(
      available = ok(BasemapLayer.Vector),
      dir = dir.toString,
      bbox = cfg.bbox,
      layers = layers,
      fetchCommand = Basemap.FetchCommand,
      freeBytes = freeSpace(BasemapService.existing(dir)),
      running = slot.current,
      mapMaxZoom = cfg.mapMaxZoom,
      hints = BasemapService.hints(
        ok(BasemapLayer.Vector),
        ok(BasemapLayer.Labels),
        vector.exists(_.kept.exists(_.contains("before the drop list")))
      ) ++ BasemapService.lackingHint(states),
      styles = states,
      regions = cfg.terrain.focusRegions
        .flatMap(r => Basemap.focusRegion(r).toOption)
        .map((name, b) => BasemapRegion(name, s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"))
    )

  // ── the relief deeper than the extract: focus regions and on demand ────────────────────────────

  /** Where the focus regions' relief archives are. */
  def focusDir: Path = dir.resolve("terrain-focus")

  /** The focus regions' archives, by name. */
  def focusFiles: List[(String, Path)] =
    if !Files.isDirectory(focusDir) then Nil
    else
      Using
        .resource(Files.list(focusDir))(_.toArray.toList.map(_.asInstanceOf[Path]))
        .filter(p => p.getFileName.toString.endsWith(".pmtiles") && Files.isRegularFile(p))
        .map(p => (p.getFileName.toString.stripSuffix(".pmtiles"), p))
        .sortBy(_._1)

  def focusInfo: List[BasemapFocus] = focusFiles.flatMap { (name, p) =>
    readerAt(p).toOption.flatten.map { f =>
      val h = f.header
      BasemapFocus(
        name,
        p.toString,
        f.size,
        s"${h.minLon},${h.minLat},${h.maxLon},${h.maxLat}",
        h.minZoom,
        h.maxZoom,
        h.addressedTiles,
        f.metadata.hcursor.downField("histmersion").get[String]("fetchedAt").toOption
      )
    }
  }

  /** The focus regions' files as they are now, in a few characters ("" with none). */
  private def focusStamp: String =
    val all = focusFiles.flatMap((n, p) => stamp(p).map(n + _))
    if all.isEmpty then "" else "-" + Integer.toHexString(all.mkString("|").hashCode)

  // the on-demand layers of the directory configured now (`basemap.dir` may change with a reload)
  @volatile private var demands = Map.empty[BasemapLayer, OnDemandTiles]

  private def demandAt(l: BasemapLayer, name: String)(make: Path => OnDemandTiles): OnDemandTiles =
    val at = dir.resolve(name)
    demands
      .get(l)
      .filter(_.dir == at)
      .getOrElse(synchronized {
        demands.get(l).filter(_.dir == at).getOrElse {
          val d = make(at)
          demands = demands.updated(l, d)
          d
        }
      })

  /** The on-demand relief and its cache (`terrain-cache/` beside the extract). */
  def onDemand: OnDemandTiles =
    demandAt(BasemapLayer.Terrain, "terrain-cache")(at =>
      OnDemandTiles(at, () => cfg.terrain.onDemand, OnDemandTiles.Source.Planet(() => cfg.terrain.url), http)
    )

  /** The map outside the extract, or deeper (#3760): read from the planet build the extract came from, thinned as the
    * extract was, cached in `vector-cache/`.
    */
  def vectorOnDemand: OnDemandTiles =
    demandAt(BasemapLayer.Vector, "vector-cache")(at =>
      OnDemandTiles(
        at,
        () => cfg.vector.onDemand,
        OnDemandTiles.Source.Planet(() => vectorPlanet()),
        http,
        ext = "mvt",
        transform = b =>
          BasemapService.thin(
            b,
            if PmTiles.isGzip(b) then PmTiles.Compression.Gzip else PmTiles.Compression.None,
            vectorRules()
          )
      )
    )

  /** The sea floor outside the extract (#3760): one GET a tile, cached in `sea-cache/`. */
  def seaOnDemand: OnDemandTiles =
    demandAt(BasemapLayer.Bathymetry, "sea-cache")(at =>
      OnDemandTiles(
        at,
        () => cfg.bathymetry.onDemand,
        OnDemandTiles.Source.Template(() => cfg.bathymetry.url),
        http,
        ext = "png"
      )
    )

  /** A layer's on-demand tiles (none for the names). */
  def onDemandOf(l: BasemapLayer): Option[OnDemandTiles] = l match
    case BasemapLayer.Terrain    => Some(onDemand)
    case BasemapLayer.Vector     => Some(vectorOnDemand)
    case BasemapLayer.Bathymetry => Some(seaOnDemand)
    case BasemapLayer.Labels     => None

  /** What the vector extract was thinned with — so a tile read on demand matches it: its own drop list, or none when it
    * was kept whole; without an extract, the configured list.
    */
  private def vectorRules(): Map[String, Mvt.Rule] =
    val meta = reader(BasemapLayer.Vector).toOption.flatten.map(_.metadata.hcursor.downField("histmersion"))
    val keepAll = meta.flatMap(_.get[Boolean]("keepAll").toOption).getOrElse(false)
    val drop = meta.flatMap(_.get[String]("drop").toOption).getOrElse(cfg.vector.drop)
    if keepAll then Map.empty else BasemapService.mvtRules(Basemap.parseDrop(drop).toOption.getOrElse(Nil))

  // `var`: the newest build's key, asked of the provider once (when neither the extract nor the config names one)
  @volatile private var latestBuild: Option[String] = None

  /** The planet the vector tiles are read from: the build the extract came from, else the configured one, else the
    * provider's newest. "" when none can be named (the read then fails, as offline).
    */
  private def vectorPlanet(): String =
    val fromExtract = reader(BasemapLayer.Vector).toOption.flatten
      .flatMap(_.metadata.hcursor.downField("histmersion").get[String]("build").toOption)
      .filter(_.endsWith(".pmtiles"))
    val pinned = Option(cfg.vector.build.trim).filter(b => b.nonEmpty && !b.equalsIgnoreCase("latest"))
    fromExtract
      .orElse(pinned)
      .orElse(latestBuild)
      .orElse {
        val got =
          http().text(cfg.vector.buildsUrl).flatMap(BasemapService.builds).toOption.flatMap(_.lastOption.map(_._1))
        latestBuild = got
        got
      }
      .fold("")(b => cfg.vector.url.replace("{build}", b))

  /** A relief tile deeper than the extract (or outside it): a focus region's, else the on-demand one. */
  private def deeperRelief(z: Int, x: Long, y: Long, extractMax: Int): Either[String, Option[Array[Byte]]] =
    val focused = focusFiles.iterator
      .flatMap(f => readerAt(f._2).toOption.flatten)
      .filter(f => z >= f.header.minZoom && z <= f.header.maxZoom)
      .map(_.tile(z, x, y))
      .collectFirst { case Right(Some(b)) => b }
    focused match
      case Some(b) => Right(Some(b))
      case None    => if onDemand.serves(z, extractMax) then onDemand.tile(z, x, y) else Right(None)

  /** A relief tile no archive has, from the relief it holds (docs/research-map-styles.md §4.4): the nearest ancestor
    * that exists — a focus region's, else the extract's — cut and resampled in metres ([[TerrainResample]]) as a
    * terrarium PNG; `None` where no ancestor exists (outside every region).
    */
  private def fromAncestor(z: Int, x: Long, y: Long, extract: PmTilesFile): Option[Array[Byte]] =
    val key = s"${stamp(path(BasemapLayer.Terrain)).getOrElse("")}$focusStamp|$z/$x/$y"
    Option(resampled.get(key)).orElse {
      val focus = focusFiles.flatMap(f => readerAt(f._2).toOption.flatten.map(f._2 -> _))
      def at(az: Int, ax: Long, ay: Long): Option[(String, Array[Byte])] =
        focus.iterator
          .filter((_, f) => az >= f.header.minZoom && az <= f.header.maxZoom)
          .flatMap((p, f) =>
            f.tile(az, ax, ay).toOption.flatten.map(b => (s"$p|${stamp(p).getOrElse("")}|$az/$ax/$ay", b))
          )
          .nextOption()
          .orElse(
            Option
              .when(az <= extract.header.maxZoom)(())
              .flatMap(_ => extract.tile(az, ax, ay).toOption.flatten.map(b => (s"extract|$az/$ax/$ay", b)))
          )
      // at most AncestorDepth zooms up: a tile far from every region is not drawn from the world's few
      val found = (z - 1 to math.max(0, z - BasemapService.AncestorDepth) by -1).iterator
        .flatMap { az =>
          val d = z - az
          at(az, x >> d, y >> d).map(t => (az, t))
        }
        .nextOption()
      found.flatMap { case (az, (k, bytes)) =>
        val img = Option(decoded.get(k))
          .map(Right(_))
          .getOrElse(TerrainResample.decode(bytes).map { i => decoded.put(k, i); i })
        img match
          case Left(e) =>
            scribe.warn(s"relief tile $az (for $z/$x/$y) cannot be read: $e")
            None
          case Right(i) if i.width != i.height =>
            None
          case Right(i) =>
            val png = Png.encodeRgb(i.width, i.height, TerrainResample.resample(i.pixels, i.width, z - az, x, y))
            resampled.put(key, png)
            Some(png)
      }
    }

  /** The deepest relief zoom anything here holds — the extract, a focus region, the on-demand relief: what the page's
    * elevation source asks for at most, and where the cutting from ancestors stops.
    */
  private def deepestRelief(extractMax: Int): Int =
    val od = cfg.terrain.onDemand
    (extractMax :: (if od.enabled then List(od.maxZoom) else Nil) ++
      focusFiles.flatMap(f => readerAt(f._2).toOption.flatten.map(_.header.maxZoom))).max

  /** Relief tiles resampled lately, and the ancestors they were cut from (a view asks for neighbours). */
  private val resampled =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, Array[Byte]](64, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, Array[Byte]]): Boolean = size() > 96)
  private val decoded =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, Vp8l.Image](16, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, Vp8l.Image]): Boolean = size() > 8)

  /** Subset tiles cut lately, by (file stamp, layer, tile, subset): a pan asks for the same few again. */
  private val cut =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, Array[Byte]]): Boolean = size() > 512)

  /** One tile with its content type and encoding: as stored, or — a vector tile with `subset` — only the layers and
    * geometries asked for (and with `keys` only those properties); 204 where the extract (or the subset) has none.
    */
  def tile(
      layer: String,
      z: Int,
      x: Long,
      y: Long,
      subset: Option[String] = None,
      keys: Option[String] = None
  ): Either[ApiError, (Array[Byte], String, Option[String], String, StatusCode)] =
    val cache = s"public, max-age=${cfg.tileCacheSeconds}"
    // the subset at this zoom: a layer asked for only from a deeper zoom is left out here
    val sub = subset.map(_.trim).filter(_.nonEmpty).map(Basemap.subsetAt(_, z))
    val props = keys.map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet).filter(_.nonEmpty)
    for
      rules <- sub
        .fold(Right(None))(s => Basemap.parseSubset(s).map(Some(_)))
        .left
        .map(e => ApiError(s"layers: $e", Some(Write.CodeInvalid)))
      l <- BasemapLayer
        .parse(layer)
        .toRight(
          ApiError(s"no base-map layer '$layer' (vector, terrain, labels or bathymetry)", Some(Write.CodeNotFound))
        )
      f <- reader(l).left
        .map(e => ApiError(e))
        .flatMap(
          _.toRight(
            ApiError(
              s"the ${l.title} layer is not downloaded — run `${Basemap.FetchCommand}`",
              Some(Write.CodeNotFound)
            )
          )
        )
      stored <- f.tile(z, x, y).left.map(ApiError(_))
      // the relief deeper than the extract: a focus region's, or read now from the provider (no tile, and
      // not cached by the browser, when it cannot be read now — offline)
      deeper = Option
        .when(stored.isEmpty && l == BasemapLayer.Terrain && z > f.header.maxZoom)(
          deeperRelief(z, x, y, f.header.maxZoom)
        )
        // the map and the sea floor outside the extract, or deeper: read now from the provider (#3760)
        .orElse(
          Option
            .when(
              stored.isEmpty && (l == BasemapLayer.Vector || l == BasemapLayer.Bathymetry) &&
                (z > f.header.maxZoom || !BasemapService.inside(f.header, z, x, y))
            )(onDemandOf(l))
            .flatten
            .filter(_.servesAt(z))
            .map(_.tile(z, x, y))
        )
      found = stored.orElse(deeper.flatMap(_.toOption.flatten))
      // none of them has it: cut from the nearest ancestor (offline, the on-demand relief failing: not kept
      // by the browser, so the provider's own tile replaces it once it can be read)
      fallback = Option
        .when(found.isEmpty && deeper.isDefined && z <= deepestRelief(f.header.maxZoom))(fromAncestor(z, x, y, f))
        .flatten
      mime = f.header.tileType.mime
      out <- (found, rules.filter(_ => f.header.tileType == PmTiles.TileType.Mvt)) match
        case (None, _) if fallback.isDefined =>
          Right((fallback.get, "image/png", None, if deeper.exists(_.isLeft) then "no-store" else cache, StatusCode.Ok))
        case (None, _) if deeper.exists(_.isLeft) =>
          Right((Array.emptyByteArray, mime, None, "no-store", StatusCode.NoContent))
        case (None, _)       => Right((Array.emptyByteArray, mime, None, cache, StatusCode.NoContent))
        case (Some(b), None) =>
          // a tile read on demand is stored as its provider sends it (a vector one gzipped)
          val enc = Option.when(
            (f.header.tileCompression == PmTiles.Compression.Gzip || stored.isEmpty) && PmTiles.isGzip(b)
          )("gzip")
          Right((b, mime, enc, cache, StatusCode.Ok))
        case (Some(b), Some(rs)) =>
          val key =
            s"$layer|${stamp(path(l)).getOrElse("")}|$z/$x/$y|${sub.getOrElse("")}|${props.fold("")(_.toList.sorted.mkString(","))}"
          Option(cut.get(key))
            .map(Right(_))
            .getOrElse(
              BasemapService
                .subset(b, if PmTiles.isGzip(b) then PmTiles.Compression.Gzip else f.header.tileCompression, rs, props)
                .left
                .map(ApiError(_))
                .map { t => cut.put(key, t); t }
            )
            .map(t =>
              if t.isEmpty then (t, mime, None, cache, StatusCode.NoContent)
              else (t, mime, Some("gzip"), cache, StatusCode.Ok)
            )
    yield out

  // ── removing ───────────────────────────────────────────────────────────────────────────────────

  def remove(q: BasemapRemoveQuery): Either[ApiError, BasemapRemoved] =
    val layers = if q.layer.trim.equalsIgnoreCase("none") then Right(Nil) else BasemapLayer.parseMany(q.layer)
    val all = q.layer.trim.equalsIgnoreCase("all") || q.layer.trim.isEmpty
    val focus = q.focus.map(_.trim).filter(_.nonEmpty) match
      case Some("all") => Right(focusFiles.map(_._1))
      case Some(n)     =>
        Basemap
          .validFocusName(n)
          .flatMap(n =>
            Either.cond(
              focusFiles.exists(_._1 == n) || Files.isDirectory(focusWork(n)),
              List(n),
              s"no focus region '$n' (${focusFiles.map(_._1).mkString(", ")})"
            )
          )
      case None if all => Right(focusFiles.map(_._1))
      case None        => Right(Nil)
    layers.flatMap(ls => focus.map((ls, _))).left.map(e => ApiError(e, Some(Write.CodeInvalid))).flatMap { (ls, fs) =>
      if slot.isRunning then Left(BackgroundJob.busy("a base-map fetch", slot.current, Some(BasemapApi.Busy)))
      else
        def gone(f: Path, w: Path): List[(String, Long)] =
          Option(open.remove(f)).foreach(_._2.close())
          val bytes = if Files.isRegularFile(f) then Files.size(f) else 0L
          val file = Option.when(Files.deleteIfExists(f))((f.toString, bytes))
          val parts = BasemapService.deleteTree(w).map(b => (w.toString, b))
          file.toList ++ parts.toList
        val files = ls.flatMap(l => gone(path(l), work(l))) ++ fs
          .flatMap(n => gone(focusDir.resolve(s"$n.pmtiles"), focusWork(n)))
        val cached = Option
          .when(q.cache || all) {
            BasemapLayer.values.toList.flatMap(onDemandOf).flatMap { od =>
              val c = od.dir
              Option.when(Files.exists(c))((c.toString, od.cache.clear() + BasemapService.deleteTree(c).getOrElse(0L)))
            }
          }
          .toList
          .flatten
        val out = files ++ cached
        Right(BasemapRemoved(out.map(_._1), out.map(_._2).sum))
    }

  /** The unfinished download of a focus region. */
  def focusWork(name: String): Path = dir.resolve(s".terrain-focus-$name.part")

  // ── fetching ───────────────────────────────────────────────────────────────────────────────────

  /** What the next fetch of these options will do, decided before the job starts: refused here when an option is out of
    * range, so the person learns it at once.
    */
  final private case class Ask(
      layers: List[BasemapLayer],
      bbox: BBox,
      vectorZoom: Int,
      terrainZoom: Int,
      dryRun: Boolean,
      keepAll: Boolean,
      focus: Option[(String, BBox)] = None
  )

  private def ask(q: BasemapFetchQuery): Either[String, Ask] =
    def zoom(z: Int, what: String) =
      Either.cond(z >= 0 && z <= Basemap.MaxZoomCap, z, s"$what $z is outside 0–${Basemap.MaxZoomCap}")
    val asked = q.bbox.map(_.trim).filter(_.nonEmpty)
    for
      ls <- BasemapLayer.parseMany(q.layer)
      b <- Basemap.validBBox(asked.getOrElse(cfg.bbox))
      onlyTerrain = ls == List(BasemapLayer.Terrain)
      // the relief alone: --max-zoom is its depth too
      tzAsked = q.terrainMaxZoom.orElse(q.maxZoom.filter(_ => onlyTerrain))
      focus <- BasemapService.focusOf(q.focus, asked.map(_ => b), onlyTerrain, tzAsked, cfg.terrain)
      vz <- zoom(q.maxZoom.getOrElse(cfg.vector.maxZoom), "--max-zoom")
      tz <- zoom(
        tzAsked.getOrElse(if focus.isDefined then cfg.terrain.onDemand.maxZoom else cfg.terrain.maxZoom),
        "--terrain-max-zoom"
      )
      _ <- Either.cond(
        focus.isEmpty || tz > cfg.terrain.maxZoom,
        (),
        s"a focus region holds the zooms deeper than the extract's (${cfg.terrain.maxZoom}): ask for more than that"
      )
      _ <- Basemap.parseDrop(cfg.vector.drop)
    yield Ask(
      if focus.isDefined then List(BasemapLayer.Terrain) else ls,
      focus.fold(b)(_._2),
      vz,
      tz,
      q.dryRun,
      q.keepAll,
      focus
    )

  def fetch(q: BasemapFetchQuery): Either[ApiError, JobInfo] =
    deps.maintenance.guard("basemap/fetch"):
      ask(q).left.map(e => ApiError(e, Some(Write.CodeInvalid))).flatMap { a =>
        val what = a.focus match
          case Some((name, b)) =>
            s"${if a.dryRun then "dry run: " else ""}terrain base map — the relief of focus region '$name' " +
              s"(${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}), zoom ${cfg.terrain.maxZoom + 1}–${a.terrainZoom}"
          case None =>
            s"${if a.dryRun then "dry run: " else ""}terrain base map — ${a.layers.map(_.wire).mkString(" + ")}, " +
              s"bbox ${q.bbox.getOrElse(cfg.bbox)}, zoom ≤ " + a.layers
                .collect {
                  case BasemapLayer.Vector     => s"${a.vectorZoom} (vector)"
                  case BasemapLayer.Terrain    => s"${a.terrainZoom} (terrain)"
                  case BasemapLayer.Bathymetry =>
                    s"${cfg.bathymetry.extractMaxZoom} (sea floor, ${cfg.bathymetry.source.wire})"
                }
                .mkString(" / ")
        BackgroundJob.start(
          deps.ds,
          BasemapService.JobKind,
          Some(JobDetail(about = Some(what))),
          slot,
          "a base-map fetch",
          Some(BasemapApi.Busy),
          deps.pins,
          Some(deps.jobStops)
        )((_, note) => BackgroundJob.Outcome.of(run(a, note)))
      }

  /** The job: each layer in turn — planned, checked against the free disk, downloaded, assembled. */
  private def run(a: Ask, note: String => Unit): Either[String, String] =
    a.focus match
      case Some((name, _)) => focusFetch(a, name, note)
      case None            => layers(a, note)

  /** A focus region's relief: zooms deeper than the extract, into `terrain-focus/<name>.pmtiles`. */
  private def focusFetch(a: Ask, name: String, note: String => Unit): Either[String, String] =
    val h = http()
    val src = BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url)
    val extra = s"focus $name from ${cfg.terrain.maxZoom + 1}"
    for
      plan <- BasemapFetch.plan(h, src, a.bbox, a.terrainZoom, pace, note, minZoom = Some(cfg.terrain.maxZoom + 1))
      out <-
        if a.dryRun then Right(s"focus '$name': " + BasemapService.describe(BasemapLayer.Terrain, plan, dryRun = true))
        else
          Files.createDirectories(focusDir)
          download(
            BasemapLayer.Terrain,
            plan,
            Nil,
            extra,
            h,
            note,
            keepAll = false,
            out = Some(focusDir.resolve(s"$name.pmtiles")),
            workDir = Some(focusWork(name))
          ).map(s => s"focus '$name': $s")
    yield out

  private def layers(a: Ask, note: String => Unit): Either[String, String] =
    a.layers
      .foldLeft[Either[String, List[String]]](Right(Nil)) { (acc, l) =>
        acc.flatMap(done => one(a, l, s => note(s"${l.wire}: $s")).map(done :+ _))
      }
      .map(_.mkString("; "))

  private def pace = BasemapFetch.Pace(cfg.requestGapMs.toLong, cfg.maxRequestMb.toLong * 1024 * 1024, cfg.overfetch)

  private def minFree: Long = (cfg.minFreeGb * 1e9).toLong

  /** The source of a layer: the vector build named by the config ("latest": the newest in the provider's list — unless
    * an unfinished download of an earlier one is there to finish), or the terrain URL.
    */
  private def source(l: BasemapLayer, a: Ask, extra: String): Either[String, BasemapFetch.Source] = l match
    case BasemapLayer.Terrain    => Right(BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url))
    case BasemapLayer.Labels     => Left("the physical names are not read with range requests") // `labels` fetches them
    case BasemapLayer.Bathymetry => Left("the sea floor is not read with range requests") // `bathymetry` fetches it
    case BasemapLayer.Vector     =>
      val pinned = cfg.vector.build.trim
      resumable(l, a, extra) match
        case Some(src) if pinned.equalsIgnoreCase("latest") || src.label == pinned => Right(src)
        case _                                                                     =>
          val builds = http().text(cfg.vector.buildsUrl).flatMap(BasemapService.builds)
          val chosen =
            if pinned.equalsIgnoreCase("latest") || pinned.isEmpty then
              builds.flatMap(_.lastOption.toRight(s"${cfg.vector.buildsUrl} lists no build"))
            else builds.map(_.find(_._1 == pinned).getOrElse((pinned, -1L))).orElse(Right((pinned, -1L)))
          chosen.map((key, size) =>
            BasemapFetch.Source(cfg.vector.url.replace("{build}", key), key, Option.when(size > 0)(size))
          )

  /** The source an unfinished download of the same region and depth was reading. */
  private def resumable(l: BasemapLayer, a: Ask, extra: String): Option[BasemapFetch.Source] =
    val f = work(l).resolve("state.json")
    Option.when(Files.isRegularFile(f))(Files.readString(f)).flatMap(s => io.circe.parser.parse(s).toOption).flatMap {
      j =>
        val c = j.hcursor
        val same =
          c.get[Int]("maxZoom").toOption.contains(zoomOf(l, a)) && c.get[String]("extra").toOption.contains(extra) &&
            c.get[String]("bbox").toOption.flatMap(BBox.parse).exists(b => BasemapService.near(b, a.bbox))
        for
          url <- c.get[String]("url").toOption if same
          label <- c.get[String]("label").toOption.orElse(Some(url))
          size = c.get[Long]("size").toOption
        yield BasemapFetch.Source(url, label, size)
    }

  private def zoomOf(l: BasemapLayer, a: Ask): Int = l match
    case BasemapLayer.Vector     => a.vectorZoom
    case BasemapLayer.Terrain    => a.terrainZoom
    case BasemapLayer.Labels     => cfg.labels.maxZoom
    case BasemapLayer.Bathymetry => cfg.bathymetry.extractMaxZoom

  private def one(a: Ask, l: BasemapLayer, note: String => Unit): Either[String, String] =
    l match
      case BasemapLayer.Labels     => labels(a, note)
      case BasemapLayer.Bathymetry =>
        cfg.bathymetry.source match
          case SeaFloorSource.Etopo1 => bathymetry(a, note)
          case SeaFloorSource.Gebco  => gebco(a, note)
      case _ => ranged(a, l, note)

  /** A PMTiles layer (vector, terrain): planned, checked against the free disk, downloaded, assembled. */
  private def ranged(a: Ask, l: BasemapLayer, note: String => Unit): Either[String, String] =
    val rules = if a.keepAll then Nil else Basemap.parseDrop(cfg.vector.drop).toOption.getOrElse(Nil)
    val extra = if l != BasemapLayer.Vector then "" else if a.keepAll then "keep-all" else s"drop: ${cfg.vector.drop}"
    val h = http()
    for
      src <- source(l, a, extra)
      _ = note(s"reading ${src.label}")
      plan <- BasemapFetch.plan(h, src, a.bbox, zoomOf(l, a), pace, note)
      out <-
        if a.dryRun then Right(BasemapService.describe(l, plan, dryRun = true))
        else download(l, plan, rules, extra, h, note, a.keepAll)
    yield out

  private def download(
      l: BasemapLayer,
      plan: BasemapFetch.Plan,
      rules: List[Basemap.DropRule],
      extra: String,
      h: RangeHttp,
      note: String => Unit,
      keepAll: Boolean,
      out: Option[Path] = None,
      workDir: Option[Path] = None
  ): Either[String, String] =
    Files.createDirectories(dir)
    val work = workDir.getOrElse(this.work(l))
    val target = out.getOrElse(path(l))
    val state = BasemapFetch
      .stateOf(plan, extra)
      .deepMerge(io.circe.Json.obj("label" -> io.circe.Json.fromString(plan.source.label)))
    val had = BasemapFetch.prepare(work, state)
    val pendingBytes = BasemapFetch.pending(plan, work).map(_._2).sum
    // the local archive is at most the distinct contents the requests carry (the vector one is far
    // smaller once thinned); it is written beside the downloaded parts before they are removed
    val outBytes = plan.contentBytes
    def room(next: Long): Either[String, Unit] =
      val free = freeSpace(dir)
      val left = free - next - outBytes
      Either.cond(
        left >= minFree,
        (),
        s"refused: the ${l.title} layer needs ${Backup.humanBytes(pendingBytes + outBytes)} more on $dir " +
          s"(${Backup.humanBytes(free)} free), which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb). " +
          "Free some space, fetch a smaller region (--bbox) or fewer zooms (--max-zoom / --terrain-max-zoom), " +
          "or move the files with basemap.dir."
      )
    for
      _ <- room(pendingBytes)
      _ = if had > 0 then note(s"resuming: $had of ${plan.requests.size} parts are here already")
      _ <- BasemapFetch.download(h, plan, work, pace, note, room)
      rulesOf = BasemapService.mvtRules(rules)
      transform = l match
        case BasemapLayer.Vector if !keepAll =>
          (b: Array[Byte]) => BasemapService.thin(b, plan.header.tileCompression, rulesOf)
        case _ => (b: Array[Byte]) => Right(Some(b))
      meta = BasemapFetch.localMetadata(
        plan,
        rules.filter(_.whole).map(_.layer).toSet,
        attribution(l),
        Instant.now().toString,
        l,
        Option.when(l == BasemapLayer.Vector)((keepAll, cfg.vector.drop))
      )
      done <- BasemapFetch.assemble(plan, work, target, transform, BasemapFetch.localHeader(plan), meta, note)
    yield
      val _ = BasemapService.deleteTree(work)
      val _ = readerAt(target)
      s"${BasemapService.describe(l, plan, dryRun = false)}; stored ${done._2} tiles in ${Backup.humanBytes(done._1)} at $target"

  /** The sea floor: every tile of zooms 0…`bathymetry.max-zoom` over the region, one GET each (a public bucket of map
    * tiles: no range requests), kept in a work directory as they come — so an interrupted fetch resumes — then written
    * as `bathymetry.pmtiles`.
    */
  private def bathymetry(a: Ask, note: String => Unit): Either[String, String] =
    val bc = cfg.bathymetry
    // every tile of the region, in tile-id order (the archive's)
    val tiles = (0 to bc.maxZoom)
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(a.bbox, z)
        for x <- x0 to x1; y <- y0 to y1 yield (PmTiles.tileId(z, x, y), (z, x, y))
      }
      .sortBy(_._1)
    val ids = tiles.map(_._1)
    val each = 80L * 1024 // a tile, about (measured: 2026-10-20)
    if a.dryRun then
      Right(
        s"bathymetry (zoom 0–${bc.maxZoom}): would download ${ids.length} tiles, about ${Backup.humanBytes(ids.length * each)}"
      )
    else
      val need = ids.length * each * 2
      val free = freeSpace(BasemapService.existing(dir))
      if free - need < minFree then
        Left(
          s"refused: the sea floor needs about ${Backup.humanBytes(need)} on $dir (${Backup.humanBytes(free)} free), " +
            s"which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb)"
        )
      else
        val work = this.work(BasemapLayer.Bathymetry)
        Files.createDirectories(work)
        val h = http()
        val coords = tiles.map(_._2)
        val got = coords.zipWithIndex.foldLeft[Either[String, Int]](Right(0)) { case (acc, ((z, x, y), i)) =>
          acc.flatMap { n =>
            val f = work.resolve(s"$z-$x-$y.png")
            val none = work.resolve(s"$z-$x-$y.none")
            if Files.isRegularFile(f) || Files.isRegularFile(none) then Right(n)
            else
              if n > 0 then pace.sleep(bc.requestGapMs.toLong)
              if Thread.interrupted() then throw InterruptedException()
              if i % 50 == 0 then note(s"downloading ${i + 1} of ${ids.length} tiles")
              h.file(bc.url.replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString)).map {
                case Some(b) => Files.write(f, b); n + 1
                case None    => Files.write(none, Array.emptyByteArray); n + 1
              }
          }
        }
        got.map { _ =>
          note("writing bathymetry.pmtiles")
          val out = path(BasemapLayer.Bathymetry)
          val w = PmTilesWriter(out)
          var stored = 0L // tiles written
          ids.zip(coords).foreach { case (id, (z, x, y)) =>
            val f = work.resolve(s"$z-$x-$y.png")
            if Files.isRegularFile(f) then { w.add(id, Files.readAllBytes(f)); stored += 1 }
          }
          val header = PmTiles.Header(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            true,
            PmTiles.Compression.Gzip,
            PmTiles.Compression.None,
            PmTiles.TileType.Png,
            0,
            bc.maxZoom,
            a.bbox.minLon,
            a.bbox.minLat,
            a.bbox.maxLon,
            a.bbox.maxLat,
            math.min(bc.maxZoom, 5),
            (a.bbox.minLon + a.bbox.maxLon) / 2,
            (a.bbox.minLat + a.bbox.maxLat) / 2
          )
          val meta = io.circe.Json.obj(
            "name" -> io.circe.Json.fromString("histmersion sea floor"),
            "attribution" -> io.circe.Json.fromString(bc.attribution),
            "histmersion" -> io.circe.Json.obj(
              "layer" -> io.circe.Json.fromString(BasemapLayer.Bathymetry.wire),
              "source" -> io.circe.Json.fromString(bc.url),
              "build" -> io.circe.Json.fromString("ETOPO1 (Mapzen terrain tiles)"),
              "bbox" -> io.circe.Json
                .fromString(s"${a.bbox.minLon},${a.bbox.minLat},${a.bbox.maxLon},${a.bbox.maxLat}"),
              "maxZoom" -> io.circe.Json.fromInt(bc.maxZoom),
              "fetchedAt" -> io.circe.Json.fromString(Instant.now().toString)
            )
          )
          val bytes = w.finish(header, meta)
          val _ = BasemapService.deleteTree(work)
          val _ = reader(BasemapLayer.Bathymetry)
          s"bathymetry (zoom 0–${bc.maxZoom}): $stored tiles, ${Backup.humanBytes(bytes)} at $out"
        }

  /** The sea floor from the GEBCO grid ([[GebcoSeaFloor]]): the GeoTIFF rows the region needs read by range into the
    * work directory (resumed when interrupted), then made into the tiles of zooms 0…`bathymetry.gebco.max-zoom`,
    * written as `bathymetry.pmtiles`.
    */
  private def gebco(a: Ask, note: String => Unit): Either[String, String] =
    val bc = cfg.bathymetry
    val sea = GebcoSeaFloor(bc.gebco, bc.url, http(), cfg.maxRequestMb.toLong * 1024 * 1024)
    note("reading the GEBCO GeoTIFFs' headers")
    sea.plan(a.bbox, bc.gebco.maxZoom).flatMap { plan =>
      if a.dryRun then Right(sea.describe(plan))
      else
        val free = freeSpace(BasemapService.existing(dir))
        if free - sea.needs(plan) < minFree then
          Left(
            s"refused: the GEBCO sea floor needs about ${Backup.humanBytes(sea.needs(plan))} on $dir " +
              s"(${Backup.humanBytes(free)} free), which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb). " +
              "Fetch a smaller region (--bbox), or the ETOPO1 sea floor (basemap.bathymetry.source = etopo1)."
          )
        else
          val work = this.work(BasemapLayer.Bathymetry)
          for
            _ <- sea.download(plan, work, cfg.requestGapMs.toLong, note)
            out = path(BasemapLayer.Bathymetry)
            w = PmTilesWriter(out)
            n <- sea.build(plan, work, w.add, note)
          yield
            val header = PmTiles.Header(
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              true,
              PmTiles.Compression.Gzip,
              PmTiles.Compression.None,
              PmTiles.TileType.Png,
              0,
              bc.gebco.maxZoom,
              a.bbox.minLon,
              a.bbox.minLat,
              a.bbox.maxLon,
              a.bbox.maxLat,
              math.min(bc.gebco.maxZoom, 5),
              (a.bbox.minLon + a.bbox.maxLon) / 2,
              (a.bbox.minLat + a.bbox.maxLat) / 2
            )
            val meta = io.circe.Json.obj(
              "name" -> io.circe.Json.fromString("histmersion sea floor"),
              "attribution" -> io.circe.Json.fromString(bc.gebco.attribution),
              "histmersion" -> io.circe.Json.obj(
                "layer" -> io.circe.Json.fromString(BasemapLayer.Bathymetry.wire),
                "source" -> io.circe.Json.fromString(bc.gebco.url),
                "build" -> io.circe.Json.fromString("GEBCO grid"),
                "bbox" -> io.circe.Json
                  .fromString(s"${a.bbox.minLon},${a.bbox.minLat},${a.bbox.maxLon},${a.bbox.maxLat}"),
                "maxZoom" -> io.circe.Json.fromInt(bc.gebco.maxZoom),
                "fetchedAt" -> io.circe.Json.fromString(Instant.now().toString)
              )
            )
            val bytes = w.finish(header, meta)
            val _ = BasemapService.deleteTree(work)
            val _ = reader(BasemapLayer.Bathymetry)
            s"bathymetry from GEBCO (zoom 0–${bc.gebco.maxZoom}): $n tiles from ${Backup.humanBytes(plan.readBytes)} of the grid, " +
              s"${Backup.humanBytes(bytes)} at $out"
    }

  /** The physical names: Natural Earth's label files read whole (a few MB each), cut to the region and written as
    * `labels.pmtiles` (about 1.4 MB for the default region) — no range requests, nothing to resume.
    */
  private def labels(a: Ask, note: String => Unit): Either[String, String] =
    val lc = cfg.labels
    val files = lc.files.map(f => f -> lc.url.replace("{file}", f))
    if a.dryRun then
      Right(
        s"labels (Natural Earth, zoom 0–${lc.maxZoom}): would download ${files.size} files — ${lc.files.mkString(", ")} — " +
          "about 20 MB, of which about 1.4 MB is kept"
      )
    else
      val free = freeSpace(BasemapService.existing(dir))
      val need = 64L * 1024 * 1024
      if free - need < minFree then
        Left(
          s"refused: the physical names need about ${Backup.humanBytes(need)} on $dir (${Backup.humanBytes(free)} free), " +
            s"which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb)"
        )
      else
        val h = http()
        val read = files.foldLeft[Either[String, List[BasemapLabels.Label]]](Right(Nil)) { case (acc, (f, url)) =>
          acc.flatMap { got =>
            note(s"reading $f")
            if got.nonEmpty then pace.sleep(pace.gapMs)
            h.text(url)
              .flatMap(t => io.circe.parser.parse(t).left.map(e => s"$url is not JSON: ${e.getMessage}"))
              .flatMap(BasemapLabels.parse(f, _))
              .map(got ++ _)
          }
        }
        read.map { all =>
          val kept = all.filter(BasemapLabels.within(_, a.bbox))
          note(s"${kept.size} of ${all.size} names are in the region; writing")
          Files.createDirectories(dir)
          val (tiles, bytes) = BasemapLabels.write(
            path(BasemapLayer.Labels),
            kept,
            a.bbox,
            lc.maxZoom,
            BasemapService.labelsMetadata(lc, a.bbox, kept.size, Instant.now().toString)
          )
          val _ = reader(BasemapLayer.Labels)
          s"labels (Natural Earth): ${kept.size} names in $tiles tiles, ${Backup.humanBytes(bytes)} at ${path(BasemapLayer.Labels)}"
        }

object BasemapService:

  /** The sea floor's credit line: the extract's own (GEBCO or ETOPO1), and ETOPO1's as well when the sea outside the
    * extract is read from it on demand and the extract is another's.
    */
  def seaCredit(extract: Option[String], etopo1: String, onDemand: Boolean): String =
    extract.filter(_.trim.nonEmpty) match
      case Some(e) if e != etopo1 && onDemand => s"$e; outside it, $etopo1"
      case Some(e)                            => e
      case None                               => etopo1

  /** How many zooms up a relief tile is looked for to cut a missing one from: the extract's deepest (8) to a focus
    * region's (12) is 4; more is a tile of the whole world stretched over a valley.
    */
  val AncestorDepth = 6

  /** The focus region a fetch asks for: `--focus <name>` (one of `basemap.terrain.focus-regions`, or a new name with
    * `--bbox`), or — the relief alone, with a region and deeper than the extract — one named after its box, so a deep
    * regional fetch never replaces the broad extract. Pure.
    */
  def focusOf(
      focus: Option[String],
      bbox: Option[BBox],
      onlyTerrain: Boolean,
      terrainZoom: Option[Int],
      t: BasemapTerrainConfig
  ): Either[String, Option[(String, BBox)]] =
    val named = t.focusRegions.flatMap(r => Basemap.focusRegion(r).toOption)
    focus.map(_.trim).filter(_.nonEmpty) match
      case Some(f) =>
        Basemap.validFocusName(f).flatMap { n =>
          bbox
            .orElse(named.find(_._1 == n).map(_._2))
            .map(b => Some((n, b)))
            .toRight(
              s"no focus region '$n' in basemap.terrain.focus-regions (${named.map(_._1).mkString(", ")}): give its --bbox"
            )
        }
      case None =>
        Right(bbox.filter(_ => onlyTerrain && terrainZoom.exists(_ > t.maxZoom)).map(b => (Basemap.focusNameOf(b), b)))

  /** What a fetch would add: the physical names to an extract that has the map but not them. Pure. */
  def hints(vector: Boolean, labels: Boolean, narrow: Boolean = false): List[String] =
    Option
      .when(narrow)(
        s"The vector layer was fetched before the base map kept land use and natural points: a style " +
          s"can draw only coast, water and land cover from it. One `${Basemap.FetchCommand} --layer vector` stores the broader " +
          "extract (or `" + Basemap.FetchCommand + "` for everything); restyling never needs a download after that."
      )
      .toList ++
      Option
        .when(vector && !labels)(
          s"The physical names (mountain ranges, deserts, peninsulas, lakes, major rivers, peaks) " +
            s"are not downloaded: `${Basemap.FetchCommand} --layer labels` adds them (about 20 MB read, about 1.4 MB kept)."
        )
        .toList

  /** What to run when some styles lack layers the download left out. Pure. */
  def lackingHint(states: List[BasemapStyleState]): List[String] =
    val lacking = states.filter(_.missing.nonEmpty)
    Option
      .when(lacking.nonEmpty) {
        val layers = lacking.flatMap(_.missing).distinct.sorted
        s"${names(lacking.map(_.name))} ${if lacking.size == 1 then "needs" else "need"} ${names(layers)}, which this download " +
          s"left out (basemap.vector.drop): `${Basemap.KeepAllCommand}` stores the vector layer whole (about 1.5 GB at zoom 10; " +
          "`--dry-run` first says how much), or take those layers out of basemap.vector.drop and run `" +
          Basemap.FetchCommand + " --layer vector`."
      }
      .toList

  /** "a", "a and b", "a, b and c". Pure. */
  def names(xs: List[String]): String = xs match
    case Nil        => ""
    case one :: Nil => one
    case _          => xs.init.mkString(", ") + " and " + xs.last

  /** The names archive's metadata: its layers, credit, and where and when it came from. Pure. */
  def labelsMetadata(lc: BasemapLabelsConfig, b: BBox, names: Int, fetchedAt: String): io.circe.Json =
    import io.circe.Json
    val build = "/(v\\d+\\.\\d+(\\.\\d+)?)/".r
      .findFirstMatchIn(lc.url)
      .map(m => s"Natural Earth ${m.group(1)}")
      .getOrElse("Natural Earth")
    Json.obj(
      "name" -> Json.fromString("histmersion physical names"),
      "vector_layers" -> Json
        .arr(Json.obj("id" -> Json.fromString("physical")), Json.obj("id" -> Json.fromString("rivers"))),
      "attribution" -> Json.fromString(lc.attribution),
      "histmersion" -> Json.obj(
        "layer" -> Json.fromString(BasemapLayer.Labels.wire),
        "source" -> Json.fromString(lc.url),
        "build" -> Json.fromString(build),
        "bbox" -> Json.fromString(s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"),
        "maxZoom" -> Json.fromInt(lc.maxZoom),
        "names" -> Json.fromInt(names),
        "fetchedAt" -> Json.fromString(fetchedAt)
      )
    )

  val JobKind = com.histmersion.api.JobKind.BasemapFetch.wire

  /** Where the extracts live: `basemap.dir`, else `$XDG_CACHE_HOME/histmersion/basemap` (default
    * `~/.cache/histmersion/basemap`), else under the data directory.
    */
  def dir(cfg: AppConfig, env: String => Option[String] = sys.env.get): Path =
    Option(cfg.basemap.dir)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home"))))
      .orElse(UserPaths.cacheDir(env).map(_.resolve("basemap")))
      .getOrElse(Path.of(cfg.init.dataDir).resolve("basemap"))
      .toAbsolutePath
      .normalize

  /** Free space for this process on the volume of `p`. */
  def usable(p: Path): Long = scala.util.Try(Files.getFileStore(existing(p)).getUsableSpace).getOrElse(0L)

  /** `p`, or the nearest of its parents that exists (the directory may not be made yet). */
  def existing(p: Path): Path =
    Iterator.iterate(p.toAbsolutePath)(_.getParent).takeWhile(_ != null).find(Files.exists(_)).getOrElse(p)

  /** Whether tile `z/x/y` lies in an archive's bounds (any part of it). Pure. */
  def inside(h: PmTiles.Header, z: Int, x: Long, y: Long): Boolean =
    val b = BBox(h.minLon, h.minLat, h.maxLon, h.maxLat)
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
    x >= x0 && x <= x1 && y >= y0 && y <= y1

  /** Two regions the same to within rounding. Pure. */
  def near(a: BBox, b: BBox): Boolean =
    List(a.minLon - b.minLon, a.minLat - b.minLat, a.maxLon - b.maxLon, a.maxLat - b.maxLat).forall(d =>
      math.abs(d) < 1e-6
    )

  /** The provider's builds, oldest first: `[{key, size}, …]` → `(key, size)`. Pure. */
  def builds(json: String): Either[String, List[(String, Long)]] =
    io.circe.parser.parse(json).left.map(e => s"the list of builds is not JSON: ${e.getMessage}").flatMap { j =>
      j.asArray
        .toRight("the list of builds is not a JSON array")
        .map(
          _.toList
            .flatMap { b =>
              b.hcursor
                .get[String]("key")
                .toOption
                .filter(_.endsWith(".pmtiles"))
                .map(k => (k, b.hcursor.get[Long]("size").getOrElse(-1L)))
            }
            .sortBy(_._1)
        )
    }

  /** A stored vector tile cut to `rules` (layer → geometries and kinds; none = all) and, given `props`, to those
    * properties; gzip-compressed; empty when nothing is left. Pure.
    */
  def subset(
      stored: Array[Byte],
      c: PmTiles.Compression,
      rules: Map[String, Basemap.Read],
      props: Option[Set[String]] = None
  ): Either[String, Array[Byte]] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw
      .flatMap(Mvt.filter(_, rules.map((l, r) => l -> Mvt.Rule(keep = r.kinds, geoms = r.geoms, props = props))))
      .map(t => if t.isEmpty then t else PmTiles.gzip(t))

  /** The drop list as the MVT filter's rules: a layer dropped whole, only some kinds, or all but some. Pure. */
  def mvtRules(rules: List[Basemap.DropRule]): Map[String, Mvt.Rule] =
    rules
      .map(r => r.layer -> (if r.whole then Mvt.Rule(none = true) else Mvt.Rule(keep = r.spare, drop = r.drop)))
      .toMap

  /** A vector tile without the dropped layers and kinds (every other layer kept as it is): stored again gzip-compressed
    * (as the source stores it), or dropped when nothing is left. Pure.
    */
  def thin(
      stored: Array[Byte],
      c: PmTiles.Compression,
      rules: Map[String, Mvt.Rule]
  ): Either[String, Option[Array[Byte]]] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw.flatMap(Mvt.filter(_, rules, others = true)).map { t =>
      Option.when(t.nonEmpty)(if c == PmTiles.Compression.Gzip then PmTiles.gzip(t) else t)
    }

  /** One line for a planned layer: requests and bytes. Pure. */
  def describe(l: BasemapLayer, p: BasemapFetch.Plan, dryRun: Boolean): String =
    val verb = if dryRun then "would download" else "downloaded"
    s"${l.wire} (${p.source.label}, zoom ${p.header.minZoom}–${p.maxZoom}): ${p.tiles.size} tiles, $verb " +
      s"${Backup.humanBytes(p.requestBytes)} in ${p.requests.size} requests (+${p.directoryRequests} for the directories)" +
      // a download ahead says what each zoom costs, so a smaller --max-zoom can be chosen (#3760)
      (if dryRun then s"; by zoom — ${BasemapFetch.perZoomLine(BasemapFetch.perZoom(p))}" else "")

  /** Delete a directory tree; answers the bytes it held (`None` when there was none). */
  def deleteTree(p: Path): Option[Long] =
    if !Files.exists(p) then None
    else
      var bytes = 0L // summed while walking
      Using.resource(Files.walk(p)) { s =>
        s.sorted(java.util.Comparator.reverseOrder()).forEach { f =>
          if Files.isRegularFile(f) then bytes += Files.size(f)
          val _ = Files.deleteIfExists(f)
        }
      }
      Some(bytes)

  /** Close a replaced archive a little later, after the requests reading it are done. */
  private def closeLater(f: PmTilesFile): Unit =
    val t = Thread.ofVirtual().unstarted { () => Thread.sleep(30000); f.close() }
    t.start()
