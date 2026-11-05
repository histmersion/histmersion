package com.histmersion.util.basemap

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{BBox, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.{Location, Storage, Store}

import sttp.model.StatusCode

import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.{Basemap, BasemapApi, BaseStyles}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api

/** The terrain base map on this machine (docs/research-terrain-basemap.md): where its two extracts are, the fetch job
  * that downloads them, their removal, and the tiles served to the web page.
  *
  * The extracts are a CACHE — fetched again at will — so their store ([[Location.Basemap]]) is in the XDG cache
  * directory (`basemap.dir` moves it), never in the data directory with the corpus. `config` and the store are read at
  * each use, so a reload is followed; `seaFloor` is the grid the settings choose for the sea floor. `http` and
  * `freeSpace` are the seams a test substitutes for the network and the disk.
  */
final class BasemapService(
    config: () => BasemapConfig,
    storage: Storage,
    seaFloor: () => SeaFloorGrid,
    jobs: BasemapJobs,
    http: () => RangeHttp,
    freeSpace: Store => BasemapService.FreeSpaceResult = _.usableSpace.value
)(using clock: java.time.Clock):

  private def cfg = config()

  /** The open archives, by where their file is, with the file stamp they were opened at. */
  private val open = ConcurrentHashMap[String, (String, PmTiles.File)]()

  /** The base map's files, where the configuration puts them now. */
  def files: Store = storage.store(Location.Basemap)

  def key(l: Basemap.Layer): Store.Key = Store.Key.applyUnsafe(l.file)

  /** The unfinished download of a layer: its range requests, kept until it is assembled. */
  def work(l: Basemap.Layer): Store.Key = Store.Key.applyUnsafe(s".${l.wire}.part")

  private def done(written: Either[Store.Error, ?]): Unit = written.left.foreach(e => throw IOException(e.message))

  private def attribution(l: Basemap.Layer): com.histmersion.util.basemap.BasemapService.AttributionResult = l match
    case Basemap.Layer.Vector     => cfg.vector.attribution
    case Basemap.Layer.Terrain    => cfg.terrain.attribution
    case Basemap.Layer.Labels     => cfg.labels.attribution
    case Basemap.Layer.Bathymetry => cfg.bathymetry.attribution

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  /** A stamp of the file as it is now (size, modification time): it changes with each fetch. */
  private def stamp(k: Store.Key, store: Store = files): Option[com.histmersion.util.basemap.BasemapService.StampResult] =
    store.stat(k).filter(_.isFile).map(f => s"${f.size.value.toHexString}${f.modified.toEpochMilli.toHexString}")

  /** The layer's archive, opened once and again only when the file changed. */
  def reader(l: Basemap.Layer): Either[com.histmersion.util.basemap.BasemapService.ReaderResultPart1, Option[PmTiles.File]] = readerAt(key(l))

  /** The archive `k` (a layer's, a focus region's), opened once and again only when the file changed. */
  def readerAt(k: Store.Key): Either[com.histmersion.util.basemap.BasemapService.ReaderAtResultPart1, Option[PmTiles.File]] =
    val store = files
    val p = store.where(k)
    stamp(k, store) match
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
                  store
                    .get(k)
                    .fold[Either[String, Option[PmTiles.File]]](Right(None))(PmTiles.File.open(_).map(Some(_)))
                    .map { opened =>
                      opened.foreach(f => open.put(p, (s, f)))
                      // an archive replaced while a request reads it: the old channel stays readable
                      // on macOS and Linux until closed; requests that hold it finish first
                      old.foreach(o => BasemapService.closeLater(o._2))
                      opened
                    }
            }

  def info(l: Basemap.Layer): Basemap.LayerInfo =
    val p = key(l)
    val base = Basemap.LayerInfo(l.wire, present = false, file = files.where(p), attribution = attribution(l))
    reader(l) match
      case Left(e) =>
        val now = files.stat(p)
        base.modify(
          _.present).setTo(now.exists(_.isFile)).modify(_.bytes).setTo(now.fold(0L)(_.size.value)).modify(_.error).setTo(Some(e)
        )
      case Right(None)    => base
      case Right(Some(f)) =>
        val h = f.header
        val mine = f.metadata.hcursor.downField("histmersion")
        val credit = if l != Basemap.Layer.Bathymetry then base.attribution
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
          version = if l == Basemap.Layer.Terrain then stamp(p).map(_ + focusStamp) else stamp(p),
          kept = Option.when(l == Basemap.Layer.Vector)(
            Basemap.keptLine(mine.get[Boolean]("keepAll").getOrElse(false), mine.get[String]("drop").toOption)
          ),
          onDemand = onDemandOf(l).map(_.status),
          focus = if l == Basemap.Layer.Terrain then focusInfo else Nil,
          dropped = if l != Basemap.Layer.Vector then Nil
          else Basemap.droppedLayers(mine.get[Boolean]("keepAll").getOrElse(false), mine.get[String]("drop").toOption)
        )

  def status: Basemap.Status =
    val layers = Basemap.Layer.values.toList.map(info)
    def ok(l: Basemap.Layer) = layers.exists(i => i.layer == l.wire && i.present && i.error.isEmpty)
    val vector = layers.find(i => i.layer == Basemap.Layer.Vector.wire && i.present && i.error.isEmpty)
    val states = BaseStyles.states(vector.map(_.dropped))
    Basemap.Status(
      available = ok(Basemap.Layer.Vector),
      dir = files.where(),
      bbox = cfg.bbox,
      layers = layers,
      fetchCommand = Basemap.FetchCommand,
      freeBytes = freeSpace(files),
      running = jobs.running,
      mapMaxZoom = cfg.mapMaxZoom,
      hints = BasemapService.hints(
        ok(Basemap.Layer.Vector),
        ok(Basemap.Layer.Labels),
        vector.exists(_.kept.exists(_.contains("before the drop list")))
      ) ++ BasemapService.lackingHint(states),
      styles = states,
      regions = cfg.terrain.focusRegions
        .flatMap(r => Basemap.focusRegion(r).toOption)
        .map((name, b) => Basemap.Region(name, s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"))
    )

  // ── the relief deeper than the extract: focus regions and on demand ────────────────────────────

  /** Where the focus regions' relief archives are. */
  def focusDir: Store.Key = BasemapService.FocusDir

  /** The focus region `name`'s archive. */
  def focusKey(name: com.histmersion.util.basemap.BasemapService.FocusKeyName): Store.Key = focusDir.child(s"$name.pmtiles")

  /** The focus regions' archives, by name. */
  def focusFiles: List[(com.histmersion.util.basemap.BasemapService.FocusFilesResultPart1, Store.Key)] =
    files
      .list(focusDir)
      .filter(f => f.isFile && f.key.name.endsWith(".pmtiles"))
      .map(f => (f.key.name.stripSuffix(".pmtiles"), f.key))
      .sortBy(_._1)

  def focusInfo: List[Basemap.Focus] = focusFiles.flatMap { (name, p) =>
    readerAt(p).toOption.flatten.map { f =>
      val h = f.header
      Basemap.Focus(
        name,
        files.where(p),
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
  private def focusStamp: com.histmersion.util.basemap.BasemapService.FocusStampResult =
    val all = focusFiles.flatMap((n, p) => stamp(p).map(n + _))
    if all.isEmpty then "" else "-" + Integer.toHexString(all.mkString("|").hashCode)

  // the on-demand layers of the directory configured now (`basemap.dir` may change with a reload)
  @volatile private var demands = Map.empty[Basemap.Layer, OnDemandTiles]

  private def demandAt(l: Basemap.Layer, name: com.histmersion.util.basemap.BasemapService.DemandAtName)(make: Store => OnDemandTiles): OnDemandTiles =
    val at = files.sub(Store.Key.applyUnsafe(name))
    demands
      .get(l)
      .filter(_.store.where() == at.where())
      .getOrElse(synchronized {
        demands.get(l).filter(_.store.where() == at.where()).getOrElse {
          val d = make(at)
          demands = demands.updated(l, d)
          d
        }
      })

  /** The on-demand relief and its cache (`terrain-cache/` beside the extract). */
  def onDemand: OnDemandTiles =
    demandAt(Basemap.Layer.Terrain, "terrain-cache")(at =>
      OnDemandTiles(at, () => cfg.terrain.onDemand, OnDemandTiles.Source.Planet(() => cfg.terrain.url), http)(using clock)
    )

  /** The map outside the extract, or deeper (#3760): read from the planet build the extract came from, thinned as the
    * extract was, cached in `vector-cache/`.
    */
  def vectorOnDemand: OnDemandTiles =
    demandAt(Basemap.Layer.Vector, "vector-cache")(at =>
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
      )(using clock)
    )

  /** The sea floor outside the extract (#3760): one GET a tile, cached in `sea-cache/`. */
  def seaOnDemand: OnDemandTiles =
    demandAt(Basemap.Layer.Bathymetry, "sea-cache")(at =>
      OnDemandTiles(
        at,
        () => cfg.bathymetry.onDemand,
        OnDemandTiles.Source.Template(() => cfg.bathymetry.url),
        http,
        ext = "png"
      )(using clock)
    )

  /** A layer's on-demand tiles (none for the names). */
  def onDemandOf(l: Basemap.Layer): Option[OnDemandTiles] = l match
    case Basemap.Layer.Terrain    => Some(onDemand)
    case Basemap.Layer.Vector     => Some(vectorOnDemand)
    case Basemap.Layer.Bathymetry => Some(seaOnDemand)
    case Basemap.Layer.Labels     => None

  /** What the vector extract was thinned with — so a tile read on demand matches it: its own drop list, or none when it
    * was kept whole; without an extract, the configured list.
    */
  private def vectorRules(): Map[com.histmersion.util.basemap.BasemapService.VectorRulesResultPart1, Mvt.Rule] =
    val meta = reader(Basemap.Layer.Vector).toOption.flatten.map(_.metadata.hcursor.downField("histmersion"))
    val keepAll = meta.flatMap(_.get[Boolean]("keepAll").toOption).getOrElse(false)
    val drop = meta.flatMap(_.get[String]("drop").toOption).getOrElse(cfg.vector.drop)
    if keepAll then Map.empty else BasemapService.mvtRules(Basemap.parseDrop(drop).toOption.getOrElse(Nil))

  // `var`: the newest build's key, asked of the provider once (when neither the extract nor the config names one)
  @volatile private var latestBuild: Option[com.histmersion.util.basemap.BasemapService.LatestBuild] = None

  /** The planet the vector tiles are read from: the build the extract came from, else the configured one, else the
    * provider's newest. "" when none can be named (the read then fails, as offline).
    */
  private def vectorPlanet(): com.histmersion.util.basemap.BasemapService.VectorPlanetResult =
    val fromExtract = reader(Basemap.Layer.Vector).toOption.flatten
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
  private def deeperRelief(z: com.histmersion.util.basemap.BasemapService.DeeperReliefZ, x: com.histmersion.util.basemap.BasemapService.DeeperReliefX, y: com.histmersion.util.basemap.BasemapService.DeeperReliefY, extractMax: com.histmersion.util.basemap.BasemapService.DeeperReliefExtractMax): Either[com.histmersion.util.basemap.BasemapService.DeeperReliefResultPart1, Option[com.histmersion.util.basemap.BasemapService.DeeperReliefResultPart2]] =
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
  private def fromAncestor(z: com.histmersion.util.basemap.BasemapService.FromAncestorZ, x: com.histmersion.util.basemap.BasemapService.FromAncestorX, y: com.histmersion.util.basemap.BasemapService.FromAncestorY, extract: PmTiles.File): Option[com.histmersion.util.basemap.BasemapService.FromAncestorResult] =
    val key = s"${stamp(this.key(Basemap.Layer.Terrain)).getOrElse("")}$focusStamp|$z/$x/$y"
    Option(resampled.get(key)).orElse {
      val focus = focusFiles.flatMap(f => readerAt(f._2).toOption.flatten.map(f._2 -> _))
      def at(az: Int, ax: Long, ay: Long): Option[(String, Array[Byte])] =
        focus.iterator
          .filter((_, f) => az >= f.header.minZoom && az <= f.header.maxZoom)
          .flatMap((p, f) =>
            f.tile(az, ax, ay).toOption.flatten.map(b => (s"${f.where}|${stamp(p).getOrElse("")}|$az/$ax/$ay", b))
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
  private def deepestRelief(extractMax: com.histmersion.util.basemap.BasemapService.DeepestReliefExtractMax): com.histmersion.util.basemap.BasemapService.DeepestReliefResult =
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
      layer: com.histmersion.util.basemap.BasemapService.TileLayer,
      z: com.histmersion.util.basemap.BasemapService.TileZ,
      x: com.histmersion.util.basemap.BasemapService.TileX,
      y: com.histmersion.util.basemap.BasemapService.TileY,
      subset: Option[com.histmersion.util.basemap.BasemapService.TileSubset] = None,
      keys: Option[com.histmersion.util.basemap.BasemapService.TileKeys] = None
  ): Either[Api.Error, (com.histmersion.util.basemap.BasemapService.TileResultPart2Part1, com.histmersion.util.basemap.BasemapService.TileResultPart2Part2, Option[com.histmersion.util.basemap.BasemapService.TileResultPart2Part3], com.histmersion.util.basemap.BasemapService.TileResultPart2Part4, StatusCode)] =
    val cache = s"public, max-age=${cfg.tileCacheSeconds}"
    // the subset at this zoom: a layer asked for only from a deeper zoom is left out here
    val sub = subset.map(_.trim).filter(_.nonEmpty).map(Basemap.subsetAt(_, z))
    val props = keys.map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet).filter(_.nonEmpty)
    for
      rules <- sub
        .fold(Right(None))(s => Basemap.parseSubset(s).map(Some(_)))
        .left
        .map(e => Api.Error(s"layers: $e", Some(Write.CodeInvalid)))
      l <- Basemap.Layer
        .parse(layer)
        .toRight(
          Api.Error(s"no base-map layer '$layer' (vector, terrain, labels or bathymetry)", Some(Write.CodeNotFound))
        )
      f <- reader(l).left
        .map(e => Api.Error(e))
        .flatMap(
          _.toRight(
            Api.Error(
              s"the ${l.title} layer is not downloaded — run `${Basemap.FetchCommand}`",
              Some(Write.CodeNotFound)
            )
          )
        )
      stored <- f.tile(z, x, y).left.map(Api.Error(_))
      // the relief deeper than the extract: a focus region's, or read now from the provider (no tile, and
      // not cached by the browser, when it cannot be read now — offline)
      deeper = Option
        .when(stored.isEmpty && l == Basemap.Layer.Terrain && z > f.header.maxZoom)(
          deeperRelief(z, x, y, f.header.maxZoom)
        )
        // the map and the sea floor outside the extract, or deeper: read now from the provider (#3760)
        .orElse(
          Option
            .when(
              stored.isEmpty && (l == Basemap.Layer.Vector || l == Basemap.Layer.Bathymetry) &&
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
            s"$layer|${stamp(this.key(l)).getOrElse("")}|$z/$x/$y|${sub.getOrElse("")}|${props.fold("")(_.toList.sorted.mkString(","))}"
          Option(cut.get(key))
            .map(Right(_))
            .getOrElse(
              BasemapService
                .subset(b, if PmTiles.isGzip(b) then PmTiles.Compression.Gzip else f.header.tileCompression, rs, props)
                .left
                .map(Api.Error(_))
                .map { t => cut.put(key, t); t }
            )
            .map(t =>
              if t.isEmpty then (t, mime, None, cache, StatusCode.NoContent)
              else (t, mime, Some("gzip"), cache, StatusCode.Ok)
            )
    yield out

  // ── removing ───────────────────────────────────────────────────────────────────────────────────

  def remove(q: Basemap.RemoveQuery): Either[Api.Error, Basemap.Removed] =
    val layers = if q.layer.trim.equalsIgnoreCase("none") then Right(Nil) else Basemap.Layer.parseMany(q.layer)
    val all = q.layer.trim.equalsIgnoreCase("all") || q.layer.trim.isEmpty
    val focus = q.focus.map(_.trim).filter(_.nonEmpty) match
      case Some("all") => Right(focusFiles.map(_._1))
      case Some(n)     =>
        Basemap
          .validFocusName(n)
          .flatMap(n =>
            Either.cond(
              focusFiles.exists(_._1 == n) || files.isDirectory(focusWork(n)),
              List(n),
              s"no focus region '$n' (${focusFiles.map(_._1).mkString(", ")})"
            )
          )
      case None if all => Right(focusFiles.map(_._1))
      case None        => Right(Nil)
    layers.flatMap(ls => focus.map((ls, _))).left.map(e => Api.Error(e, Some(Write.CodeInvalid))).flatMap { (ls, fs) =>
      if jobs.running.isDefined then Left(jobs.busy("a base-map fetch", Some(BasemapApi.Busy)))
      else
        val store = files
        def gone(f: Store.Key, w: Store.Key): List[(String, Long)] =
          Option(open.remove(store.where(f))).foreach(_._2.close())
          val bytes = store.stat(f).filter(_.isFile).fold(0L)(_.size.value)
          val file = Option.when(store.delete(f))((store.where(f), bytes))
          val parts = store.deleteTree(w).map(b => (store.where(w), b.value))
          file.toList ++ parts.toList
        val removed = ls.flatMap(l => gone(key(l), work(l))) ++ fs.flatMap(n => gone(focusKey(n), focusWork(n)))
        val cached = Option
          .when(q.cache || all) {
            Basemap.Layer.values.toList.flatMap(onDemandOf).flatMap { od =>
              val c = od.store
              Option.when(c.exists(Store.Key.Root))(
                (c.where(), od.cache.clear() + c.deleteTree(Store.Key.Root).fold(0L)(_.value))
              )
            }
          }
          .toList
          .flatten
        val out = removed ++ cached
        Right(Basemap.Removed(out.map(_._1), out.map(_._2).sum))
    }

  /** The unfinished download of a focus region. */
  def focusWork(name: com.histmersion.util.basemap.BasemapService.FocusWorkName): Store.Key = Store.Key.applyUnsafe(s".terrain-focus-$name.part")

  // ── fetching ───────────────────────────────────────────────────────────────────────────────────

  /** What the next fetch of these options will do, decided before the job starts: refused here when an option is out of
    * range, so the person learns it at once.
    */
  final private case class Ask(
      layers: List[Basemap.Layer],
      bbox: BBox,
      vectorZoom: Ask.VectorZoom,
      terrainZoom: Ask.TerrainZoom,
      dryRun: Ask.DryRun,
      keepAll: Ask.KeepAll,
      focus: Option[(Ask.FocusPart1, BBox)] = None
  )

  object Ask:
    type VectorZoom = VectorZoom.T
    object VectorZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TerrainZoom = TerrainZoom.T
    object TerrainZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepAll = KeepAll.T
    object KeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusPart1 = FocusPart1.T
    object FocusPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def ask(q: Basemap.FetchQuery): Either[com.histmersion.util.basemap.BasemapService.AskResultPart1, Ask] =
    def zoom(z: Int, what: String) =
      Either.cond(z >= 0 && z <= Basemap.MaxZoomCap, z, s"$what $z is outside 0–${Basemap.MaxZoomCap}")
    val asked = q.bbox.map(_.trim).filter(_.nonEmpty)
    for
      ls <- Basemap.Layer.parseMany(q.layer)
      b <- Basemap.validBBox(asked.getOrElse(cfg.bbox))
      onlyTerrain = ls == List(Basemap.Layer.Terrain)
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
      if focus.isDefined then List(Basemap.Layer.Terrain) else ls,
      focus.fold(b)(_._2),
      vz,
      tz,
      q.dryRun,
      q.keepAll,
      focus
    )

  def fetch(q: Basemap.FetchQuery): Either[Api.Error, JobInfo] =
    jobs.guarded("basemap/fetch"):
      ask(q).left.map(e => Api.Error(e, Some(Write.CodeInvalid))).flatMap { a =>
        val what = a.focus match
          case Some((name, b)) =>
            s"${if a.dryRun then "dry run: " else ""}terrain base map — the relief of focus region '$name' " +
              s"(${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}), zoom ${cfg.terrain.maxZoom + 1}–${a.terrainZoom}"
          case None =>
            s"${if a.dryRun then "dry run: " else ""}terrain base map — ${a.layers.map(_.wire).mkString(" + ")}, " +
              s"bbox ${q.bbox.getOrElse(cfg.bbox)}, zoom ≤ " + a.layers
                .collect {
                  case Basemap.Layer.Vector     => s"${a.vectorZoom} (vector)"
                  case Basemap.Layer.Terrain    => s"${a.terrainZoom} (terrain)"
                  case Basemap.Layer.Bathymetry =>
                    s"${cfg.bathymetry.extractMaxZoom} (sea floor, ${seaFloor().wire})"
                }
                .mkString(" / ")
        jobs.start(BasemapService.JobKind, what, "a base-map fetch", Some(BasemapApi.Busy))(note => run(a, note))
      }

  /** The job: each layer in turn — planned, checked against the free disk, downloaded, assembled. */
  private def run(a: Ask, note: com.histmersion.util.basemap.BasemapService.RunNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.RunResultPart1, com.histmersion.util.basemap.BasemapService.RunResultPart2] =
    a.focus match
      case Some((name, _)) => focusFetch(a, name, note)
      case None            => layers(a, note)

  /** A focus region's relief: zooms deeper than the extract, into `terrain-focus/<name>.pmtiles`. */
  private def focusFetch(a: Ask, name: com.histmersion.util.basemap.BasemapService.FocusFetchName, note: com.histmersion.util.basemap.BasemapService.FocusFetchNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.FocusFetchResultPart1, com.histmersion.util.basemap.BasemapService.FocusFetchResultPart2] =
    val h = http()
    val src = BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url)
    val extra = s"focus $name from ${cfg.terrain.maxZoom + 1}"
    for
      plan <- BasemapFetch.plan(h, src, a.bbox, a.terrainZoom, pace, note, minZoom = Some(cfg.terrain.maxZoom + 1))
      out <-
        if a.dryRun then Right(s"focus '$name': " + BasemapService.describe(Basemap.Layer.Terrain, plan, dryRun = true))
        else
          done(files.makeDirectory(focusDir))
          download(
            Basemap.Layer.Terrain,
            plan,
            Nil,
            extra,
            h,
            note,
            keepAll = false,
            out = Some(focusKey(name)),
            workDir = Some(focusWork(name))
          ).map(s => s"focus '$name': $s")
    yield out

  private def layers(a: Ask, note: com.histmersion.util.basemap.BasemapService.LayersNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.LayersResultPart1, com.histmersion.util.basemap.BasemapService.LayersResultPart2] =
    a.layers
      .foldLeft[Either[String, List[String]]](Right(Nil)) { (acc, l) =>
        acc.flatMap(done => one(a, l, s => note(s"${l.wire}: $s")).map(done :+ _))
      }
      .map(_.mkString("; "))

  private def pace = BasemapFetch.Pace(cfg.requestGapMs.toLong, cfg.maxRequestMb.toLong * 1024 * 1024, cfg.overfetch)

  private def minFree: com.histmersion.util.basemap.BasemapService.MinFreeResult = (cfg.minFreeGb * 1e9).toLong

  /** The source of a layer: the vector build named by the config ("latest": the newest in the provider's list — unless
    * an unfinished download of an earlier one is there to finish), or the terrain URL.
    */
  private def source(l: Basemap.Layer, a: Ask, extra: com.histmersion.util.basemap.BasemapService.SourceExtra): Either[com.histmersion.util.basemap.BasemapService.SourceResultPart1, BasemapFetch.Source] = l match
    case Basemap.Layer.Terrain => Right(BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url))
    case Basemap.Layer.Labels  => Left("the physical names are not read with range requests") // `labels` fetches them
    case Basemap.Layer.Bathymetry => Left("the sea floor is not read with range requests") // `bathymetry` fetches it
    case Basemap.Layer.Vector     =>
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
  private def resumable(l: Basemap.Layer, a: Ask, extra: com.histmersion.util.basemap.BasemapService.ResumableExtra): Option[BasemapFetch.Source] =
    files.get(work(l).child("state.json")).map(_.text()).flatMap(s => io.circe.parser.parse(s).toOption).flatMap { j =>
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

  private def zoomOf(l: Basemap.Layer, a: Ask): com.histmersion.util.basemap.BasemapService.ZoomOfResult = l match
    case Basemap.Layer.Vector     => a.vectorZoom
    case Basemap.Layer.Terrain    => a.terrainZoom
    case Basemap.Layer.Labels     => cfg.labels.maxZoom
    case Basemap.Layer.Bathymetry => cfg.bathymetry.extractMaxZoom

  private def one(a: Ask, l: Basemap.Layer, note: com.histmersion.util.basemap.BasemapService.OneNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.OneResultPart1, com.histmersion.util.basemap.BasemapService.OneResultPart2] =
    l match
      case Basemap.Layer.Labels     => labels(a, note)
      case Basemap.Layer.Bathymetry =>
        seaFloor() match
          case SeaFloorGrid.Etopo1 => bathymetry(a, note)
          case SeaFloorGrid.Gebco  => gebco(a, note)
      case _ => ranged(a, l, note)

  /** A PMTiles layer (vector, terrain): planned, checked against the free disk, downloaded, assembled. */
  private def ranged(a: Ask, l: Basemap.Layer, note: com.histmersion.util.basemap.BasemapService.RangedNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.RangedResultPart1, com.histmersion.util.basemap.BasemapService.RangedResultPart2] =
    val rules = if a.keepAll then Nil else Basemap.parseDrop(cfg.vector.drop).toOption.getOrElse(Nil)
    val extra = if l != Basemap.Layer.Vector then "" else if a.keepAll then "keep-all" else s"drop: ${cfg.vector.drop}"
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
      l: Basemap.Layer,
      plan: BasemapFetch.Plan,
      rules: List[Basemap.DropRule],
      extra: com.histmersion.util.basemap.BasemapService.DownloadExtra,
      h: RangeHttp,
      note: com.histmersion.util.basemap.BasemapService.DownloadNoteArg1 => Unit,
      keepAll: com.histmersion.util.basemap.BasemapService.DownloadKeepAll,
      out: Option[Store.Key] = None,
      workDir: Option[Store.Key] = None
  ): Either[com.histmersion.util.basemap.BasemapService.DownloadResultPart1, com.histmersion.util.basemap.BasemapService.DownloadResultPart2] =
    val store = files
    done(store.makeDirectory())
    val workKey = workDir.getOrElse(this.work(l))
    val work = store.sub(workKey)
    val target = out.getOrElse(key(l))
    val state = BasemapFetch
      .stateOf(plan, extra)
      .deepMerge(io.circe.Json.obj("label" -> io.circe.Json.fromString(plan.source.label)))
    val had = BasemapFetch.prepare(work, state)
    val pendingBytes = BasemapFetch.pending(plan, work).map(_._2).sum
    // the local archive is at most the distinct contents the requests carry (the vector one is far
    // smaller once thinned); it is written beside the downloaded parts before they are removed
    val outBytes = plan.contentBytes
    def room(next: Long): Either[String, Unit] =
      val free = freeSpace(store)
      val left = free - next - outBytes
      Either.cond(
        left >= minFree,
        (),
        s"refused: the ${l.title} layer needs ${Backup.humanBytes(pendingBytes + outBytes)} more on ${store.where()} " +
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
        case Basemap.Layer.Vector if !keepAll =>
          (b: Array[Byte]) => BasemapService.thin(b, plan.header.tileCompression, rulesOf)
        case _ => (b: Array[Byte]) => Right(Some(b))
      meta = BasemapFetch.localMetadata(
        plan,
        rules.filter(_.whole).map(_.layer).toSet,
        attribution(l),
        Instant.now(clock).toString,
        l,
        Option.when(l == Basemap.Layer.Vector)((keepAll, cfg.vector.drop))
      )
      written <- BasemapFetch.assemble(plan, work, store, target, transform, BasemapFetch.localHeader(plan), meta, note)
    yield
      val _ = store.deleteTree(workKey)
      val _ = readerAt(target)
      s"${BasemapService.describe(l, plan, dryRun = false)}; stored ${written._2} tiles in ${Backup.humanBytes(written._1)} at ${store.where(target)}"

  /** The sea floor: every tile of zooms 0…`bathymetry.max-zoom` over the region, one GET each (a public bucket of map
    * tiles: no range requests), kept in a work directory as they come — so an interrupted fetch resumes — then written
    * as `bathymetry.pmtiles`.
    */
  private def bathymetry(a: Ask, note: com.histmersion.util.basemap.BasemapService.BathymetryNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.BathymetryResultPart1, com.histmersion.util.basemap.BasemapService.BathymetryResultPart2] =
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
      val store = files
      val free = freeSpace(store)
      if free - need < minFree then
        Left(
          s"refused: the sea floor needs about ${Backup.humanBytes(need)} on ${store.where()} (${Backup.humanBytes(free)} free), " +
            s"which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb)"
        )
      else
        val work = store.sub(this.work(Basemap.Layer.Bathymetry))
        done(work.makeDirectory())
        val h = http()
        val coords = tiles.map(_._2)
        val got = coords.zipWithIndex.foldLeft[Either[String, Int]](Right(0)) { case (acc, ((z, x, y), i)) =>
          acc.flatMap { n =>
            val f = Store.Key.applyUnsafe(s"$z-$x-$y.png")
            val none = Store.Key.applyUnsafe(s"$z-$x-$y.none")
            if work.isFile(f) || work.isFile(none) then Right(n)
            else
              if n > 0 then pace.sleep(bc.requestGapMs.toLong)
              if Thread.interrupted() then throw InterruptedException()
              if i % 50 == 0 then note(s"downloading ${i + 1} of ${ids.length} tiles")
              h.file(bc.url.replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString)).map {
                case Some(b) => done(work.write(f, b)); n + 1
                case None    => done(work.write(none, Array.emptyByteArray)); n + 1
              }
          }
        }
        got.map { _ =>
          note("writing bathymetry.pmtiles")
          val out = key(Basemap.Layer.Bathymetry)
          val w = PmTilesWriter(store, out)
          var stored = 0L // tiles written
          ids.zip(coords).foreach { case (id, (z, x, y)) =>
            work.get(Store.Key.applyUnsafe(s"$z-$x-$y.png")).foreach { f => w.add(id, f.bytes()); stored += 1 }
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
              "layer" -> io.circe.Json.fromString(Basemap.Layer.Bathymetry.wire),
              "source" -> io.circe.Json.fromString(bc.url),
              "build" -> io.circe.Json.fromString("ETOPO1 (Mapzen terrain tiles)"),
              "bbox" -> io.circe.Json
                .fromString(s"${a.bbox.minLon},${a.bbox.minLat},${a.bbox.maxLon},${a.bbox.maxLat}"),
              "maxZoom" -> io.circe.Json.fromInt(bc.maxZoom),
              "fetchedAt" -> io.circe.Json.fromString(Instant.now(clock).toString)
            )
          )
          val bytes = w.finish(header, meta)
          val _ = store.deleteTree(this.work(Basemap.Layer.Bathymetry))
          val _ = reader(Basemap.Layer.Bathymetry)
          s"bathymetry (zoom 0–${bc.maxZoom}): $stored tiles, ${Backup.humanBytes(bytes)} at ${store.where(out)}"
        }

  /** The sea floor from the GEBCO grid ([[GebcoSeaFloor]]): the GeoTIFF rows the region needs read by range into the
    * work directory (resumed when interrupted), then made into the tiles of zooms 0…`bathymetry.gebco.max-zoom`,
    * written as `bathymetry.pmtiles`.
    */
  private def gebco(a: Ask, note: com.histmersion.util.basemap.BasemapService.GebcoNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.GebcoResultPart1, com.histmersion.util.basemap.BasemapService.GebcoResultPart2] =
    val bc = cfg.bathymetry
    val sea = GebcoSeaFloor(bc.gebco, bc.url, http(), cfg.maxRequestMb.toLong * 1024 * 1024)
    note("reading the GEBCO GeoTIFFs' headers")
    sea.plan(a.bbox, bc.gebco.maxZoom).flatMap { plan =>
      if a.dryRun then Right(sea.describe(plan))
      else
        val store = files
        val free = freeSpace(store)
        if free - sea.needs(plan) < minFree then
          Left(
            s"refused: the GEBCO sea floor needs about ${Backup.humanBytes(sea.needs(plan))} on ${store.where()} " +
              s"(${Backup.humanBytes(free)} free), which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb). " +
              "Fetch a smaller region (--bbox), or the ETOPO1 sea floor (basemap.bathymetry.source = etopo1)."
          )
        else
          val work = store.sub(this.work(Basemap.Layer.Bathymetry))
          for
            _ <- sea.download(plan, work, cfg.requestGapMs.toLong, note)
            out = key(Basemap.Layer.Bathymetry)
            w = PmTilesWriter(store, out)
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
                "layer" -> io.circe.Json.fromString(Basemap.Layer.Bathymetry.wire),
                "source" -> io.circe.Json.fromString(bc.gebco.url),
                "build" -> io.circe.Json.fromString("GEBCO grid"),
                "bbox" -> io.circe.Json
                  .fromString(s"${a.bbox.minLon},${a.bbox.minLat},${a.bbox.maxLon},${a.bbox.maxLat}"),
                "maxZoom" -> io.circe.Json.fromInt(bc.gebco.maxZoom),
                "fetchedAt" -> io.circe.Json.fromString(Instant.now(clock).toString)
              )
            )
            val bytes = w.finish(header, meta)
            val _ = store.deleteTree(this.work(Basemap.Layer.Bathymetry))
            val _ = reader(Basemap.Layer.Bathymetry)
            s"bathymetry from GEBCO (zoom 0–${bc.gebco.maxZoom}): $n tiles from ${Backup.humanBytes(plan.readBytes)} of the grid, " +
              s"${Backup.humanBytes(bytes)} at ${store.where(out)}"
    }

  /** The physical names: Natural Earth's label files read whole (a few MB each), cut to the region and written as
    * `labels.pmtiles` (about 1.4 MB for the default region) — no range requests, nothing to resume.
    */
  private def labels(a: Ask, note: com.histmersion.util.basemap.BasemapService.LabelsNoteArg1 => Unit): Either[com.histmersion.util.basemap.BasemapService.LabelsResultPart1, com.histmersion.util.basemap.BasemapService.LabelsResultPart2] =
    val lc = cfg.labels
    val files = lc.files.map(f => f -> lc.url.replace("{file}", f))
    if a.dryRun then
      Right(
        s"labels (Natural Earth, zoom 0–${lc.maxZoom}): would download ${files.size} files — ${lc.files.mkString(", ")} — " +
          "about 20 MB, of which about 1.4 MB is kept"
      )
    else
      val store = this.files
      val free = freeSpace(store)
      val need = 64L * 1024 * 1024
      if free - need < minFree then
        Left(
          s"refused: the physical names need about ${Backup.humanBytes(need)} on ${store.where()} (${Backup.humanBytes(free)} free), " +
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
          done(store.makeDirectory())
          val (tiles, bytes) = BasemapLabels.write(
            store,
            key(Basemap.Layer.Labels),
            kept,
            a.bbox,
            lc.maxZoom,
            BasemapService.labelsMetadata(lc, a.bbox, kept.size, Instant.now(clock).toString)
          )
          val _ = reader(Basemap.Layer.Labels)
          s"labels (Natural Earth): ${kept.size} names in $tiles tiles, ${Backup.humanBytes(bytes)} at ${store.where(key(Basemap.Layer.Labels))}"
        }

object BasemapService:

  /** The sea floor's credit line: the extract's own (GEBCO or ETOPO1), and ETOPO1's as well when the sea outside the
    * extract is read from it on demand and the extract is another's.
    */
  type DeeperReliefResultPart2 = DeeperReliefResultPart2.T
  object DeeperReliefResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromAncestorResult = FromAncestorResult.T
  object FromAncestorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart2Part1 = TileResultPart2Part1.T
  object TileResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetResultPart2 = SubsetResultPart2.T
  object SubsetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetStored = SubsetStored.T
  object SubsetStored extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinResultPart2 = ThinResultPart2.T
  object ThinResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinStored = ThinStored.T
  object ThinStored extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FreeSpaceResult = FreeSpaceResult.T
  object FreeSpaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type AttributionResult = AttributionResult.T
  object AttributionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StampResult = StampResult.T
  object StampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReaderResultPart1 = ReaderResultPart1.T
  object ReaderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReaderAtResultPart1 = ReaderAtResultPart1.T
  object ReaderAtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusKeyName = FocusKeyName.T
  object FocusKeyName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusFilesResultPart1 = FocusFilesResultPart1.T
  object FocusFilesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusStampResult = FocusStampResult.T
  object FocusStampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DemandAtName = DemandAtName.T
  object DemandAtName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VectorRulesResultPart1 = VectorRulesResultPart1.T
  object VectorRulesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatestBuild = LatestBuild.T
  object LatestBuild extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VectorPlanetResult = VectorPlanetResult.T
  object VectorPlanetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefResultPart1 = DeeperReliefResultPart1.T
  object DeeperReliefResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefZ = DeeperReliefZ.T
  object DeeperReliefZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefX = DeeperReliefX.T
  object DeeperReliefX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefY = DeeperReliefY.T
  object DeeperReliefY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefExtractMax = DeeperReliefExtractMax.T
  object DeeperReliefExtractMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromAncestorZ = FromAncestorZ.T
  object FromAncestorZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromAncestorX = FromAncestorX.T
  object FromAncestorX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromAncestorY = FromAncestorY.T
  object FromAncestorY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeepestReliefResult = DeepestReliefResult.T
  object DeepestReliefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeepestReliefExtractMax = DeepestReliefExtractMax.T
  object DeepestReliefExtractMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart2Part2 = TileResultPart2Part2.T
  object TileResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart2Part3 = TileResultPart2Part3.T
  object TileResultPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResultPart2Part4 = TileResultPart2Part4.T
  object TileResultPart2Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileLayer = TileLayer.T
  object TileLayer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileZ = TileZ.T
  object TileZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileSubset = TileSubset.T
  object TileSubset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileKeys = TileKeys.T
  object TileKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusWorkName = FocusWorkName.T
  object FocusWorkName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AskResultPart1 = AskResultPart1.T
  object AskResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart2 = RunResultPart2.T
  object RunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunNoteArg1 = RunNoteArg1.T
  object RunNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusFetchResultPart1 = FocusFetchResultPart1.T
  object FocusFetchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusFetchResultPart2 = FocusFetchResultPart2.T
  object FocusFetchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusFetchName = FocusFetchName.T
  object FocusFetchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusFetchNoteArg1 = FocusFetchNoteArg1.T
  object FocusFetchNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersResultPart1 = LayersResultPart1.T
  object LayersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersResultPart2 = LayersResultPart2.T
  object LayersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayersNoteArg1 = LayersNoteArg1.T
  object LayersNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinFreeResult = MinFreeResult.T
  object MinFreeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResultPart1 = SourceResultPart1.T
  object SourceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceExtra = SourceExtra.T
  object SourceExtra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumableExtra = ResumableExtra.T
  object ResumableExtra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomOfResult = ZoomOfResult.T
  object ZoomOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneResultPart1 = OneResultPart1.T
  object OneResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneResultPart2 = OneResultPart2.T
  object OneResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneNoteArg1 = OneNoteArg1.T
  object OneNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedResultPart1 = RangedResultPart1.T
  object RangedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedResultPart2 = RangedResultPart2.T
  object RangedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangedNoteArg1 = RangedNoteArg1.T
  object RangedNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart2 = DownloadResultPart2.T
  object DownloadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadExtra = DownloadExtra.T
  object DownloadExtra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadNoteArg1 = DownloadNoteArg1.T
  object DownloadNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadKeepAll = DownloadKeepAll.T
  object DownloadKeepAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BathymetryResultPart1 = BathymetryResultPart1.T
  object BathymetryResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BathymetryResultPart2 = BathymetryResultPart2.T
  object BathymetryResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BathymetryNoteArg1 = BathymetryNoteArg1.T
  object BathymetryNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GebcoResultPart1 = GebcoResultPart1.T
  object GebcoResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GebcoResultPart2 = GebcoResultPart2.T
  object GebcoResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GebcoNoteArg1 = GebcoNoteArg1.T
  object GebcoNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsResultPart1 = LabelsResultPart1.T
  object LabelsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsResultPart2 = LabelsResultPart2.T
  object LabelsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsNoteArg1 = LabelsNoteArg1.T
  object LabelsNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaCreditResult = SeaCreditResult.T
  object SeaCreditResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaCreditExtract = SeaCreditExtract.T
  object SeaCreditExtract extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaCreditEtopo1 = SeaCreditEtopo1.T
  object SeaCreditEtopo1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaCreditOnDemand = SeaCreditOnDemand.T
  object SeaCreditOnDemand extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusOfResultPart1 = FocusOfResultPart1.T
  object FocusOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusOfResultPart2Part1 = FocusOfResultPart2Part1.T
  object FocusOfResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusOfFocus = FocusOfFocus.T
  object FocusOfFocus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusOfOnlyTerrain = FocusOfOnlyTerrain.T
  object FocusOfOnlyTerrain extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusOfTerrainZoom = FocusOfTerrainZoom.T
  object FocusOfTerrainZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsResult = HintsResult.T
  object HintsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsVector = HintsVector.T
  object HintsVector extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsLabels = HintsLabels.T
  object HintsLabels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsNarrow = HintsNarrow.T
  object HintsNarrow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LackingHintResult = LackingHintResult.T
  object LackingHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesResult = NamesResult.T
  object NamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamesXs = NamesXs.T
  object NamesXs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsMetadataNames = LabelsMetadataNames.T
  object LabelsMetadataNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsMetadataFetchedAt = LabelsMetadataFetchedAt.T
  object LabelsMetadataFetchedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideResult = InsideResult.T
  object InsideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideZ = InsideZ.T
  object InsideZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideX = InsideX.T
  object InsideX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsideY = InsideY.T
  object InsideY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearResult = NearResult.T
  object NearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildsResultPart1 = BuildsResultPart1.T
  object BuildsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildsResultPart2Part1 = BuildsResultPart2Part1.T
  object BuildsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildsResultPart2Part2 = BuildsResultPart2Part2.T
  object BuildsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BuildsJson = BuildsJson.T
  object BuildsJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetResultPart1 = SubsetResultPart1.T
  object SubsetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetRulesPart1 = SubsetRulesPart1.T
  object SubsetRulesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubsetProps = SubsetProps.T
  object SubsetProps extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MvtRulesResultPart1 = MvtRulesResultPart1.T
  object MvtRulesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinResultPart1 = ThinResultPart1.T
  object ThinResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinRulesPart1 = ThinRulesPart1.T
  object ThinRulesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeDryRun = DescribeDryRun.T
  object DescribeDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def seaCredit(extract: Option[com.histmersion.util.basemap.BasemapService.SeaCreditExtract], etopo1: com.histmersion.util.basemap.BasemapService.SeaCreditEtopo1, onDemand: com.histmersion.util.basemap.BasemapService.SeaCreditOnDemand): com.histmersion.util.basemap.BasemapService.SeaCreditResult =
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
      focus: Option[com.histmersion.util.basemap.BasemapService.FocusOfFocus],
      bbox: Option[BBox],
      onlyTerrain: com.histmersion.util.basemap.BasemapService.FocusOfOnlyTerrain,
      terrainZoom: Option[com.histmersion.util.basemap.BasemapService.FocusOfTerrainZoom],
      t: BasemapTerrainConfig
  ): Either[com.histmersion.util.basemap.BasemapService.FocusOfResultPart1, Option[(com.histmersion.util.basemap.BasemapService.FocusOfResultPart2Part1, BBox)]] =
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
  def hints(vector: com.histmersion.util.basemap.BasemapService.HintsVector, labels: com.histmersion.util.basemap.BasemapService.HintsLabels, narrow: com.histmersion.util.basemap.BasemapService.HintsNarrow = false): List[com.histmersion.util.basemap.BasemapService.HintsResult] =
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
  def lackingHint(states: List[Basemap.StyleState]): List[com.histmersion.util.basemap.BasemapService.LackingHintResult] =
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
  def names(xs: List[com.histmersion.util.basemap.BasemapService.NamesXs]): com.histmersion.util.basemap.BasemapService.NamesResult = xs match
    case Nil        => ""
    case one :: Nil => one
    case _          => xs.init.mkString(", ") + " and " + xs.last

  /** The names archive's metadata: its layers, credit, and where and when it came from. Pure. */
  def labelsMetadata(lc: BasemapLabelsConfig, b: BBox, names: com.histmersion.util.basemap.BasemapService.LabelsMetadataNames, fetchedAt: com.histmersion.util.basemap.BasemapService.LabelsMetadataFetchedAt): io.circe.Json =
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
        "layer" -> Json.fromString(Basemap.Layer.Labels.wire),
        "source" -> Json.fromString(lc.url),
        "build" -> Json.fromString(build),
        "bbox" -> Json.fromString(s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"),
        "maxZoom" -> Json.fromInt(lc.maxZoom),
        "names" -> Json.fromInt(names),
        "fetchedAt" -> Json.fromString(fetchedAt)
      )
    )

  val JobKind = com.histmersion.api.basemap.BasemapJobKinds.BasemapFetch.kind.value

  /** Where the focus regions' relief archives are, in the base map's store. */
  val FocusDir: Store.Key = Store.Key.applyUnsafe("terrain-focus")

  /** Whether tile `z/x/y` lies in an archive's bounds (any part of it). Pure. */
  def inside(h: PmTiles.Header, z: com.histmersion.util.basemap.BasemapService.InsideZ, x: com.histmersion.util.basemap.BasemapService.InsideX, y: com.histmersion.util.basemap.BasemapService.InsideY): com.histmersion.util.basemap.BasemapService.InsideResult =
    val b = BBox(h.minLon, h.minLat, h.maxLon, h.maxLat)
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
    x >= x0 && x <= x1 && y >= y0 && y <= y1

  /** Two regions the same to within rounding. Pure. */
  def near(a: BBox, b: BBox): com.histmersion.util.basemap.BasemapService.NearResult =
    List(a.minLon - b.minLon, a.minLat - b.minLat, a.maxLon - b.maxLon, a.maxLat - b.maxLat).forall(d =>
      math.abs(d) < 1e-6
    )

  /** The provider's builds, oldest first: `[{key, size}, …]` → `(key, size)`. Pure. */
  def builds(json: com.histmersion.util.basemap.BasemapService.BuildsJson): Either[com.histmersion.util.basemap.BasemapService.BuildsResultPart1, List[(com.histmersion.util.basemap.BasemapService.BuildsResultPart2Part1, com.histmersion.util.basemap.BasemapService.BuildsResultPart2Part2)]] =
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
      stored: com.histmersion.util.basemap.BasemapService.SubsetStored,
      c: PmTiles.Compression,
      rules: Map[com.histmersion.util.basemap.BasemapService.SubsetRulesPart1, Basemap.Read],
      props: Option[Set[com.histmersion.util.basemap.BasemapService.SubsetProps]] = None
  ): Either[com.histmersion.util.basemap.BasemapService.SubsetResultPart1, com.histmersion.util.basemap.BasemapService.SubsetResultPart2] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw
      .flatMap(Mvt.filter(_, rules.map((l, r) => l -> Mvt.Rule(keep = r.kinds, geoms = r.geoms, props = props))))
      .map(t => if t.isEmpty then t else PmTiles.gzip(t))

  /** The drop list as the MVT filter's rules: a layer dropped whole, only some kinds, or all but some. Pure. */
  def mvtRules(rules: List[Basemap.DropRule]): Map[com.histmersion.util.basemap.BasemapService.MvtRulesResultPart1, Mvt.Rule] =
    rules
      .map(r => r.layer -> (if r.whole then Mvt.Rule(none = true) else Mvt.Rule(keep = r.spare, drop = r.drop)))
      .toMap

  /** A vector tile without the dropped layers and kinds (every other layer kept as it is): stored again gzip-compressed
    * (as the source stores it), or dropped when nothing is left. Pure.
    */
  def thin(
      stored: com.histmersion.util.basemap.BasemapService.ThinStored,
      c: PmTiles.Compression,
      rules: Map[com.histmersion.util.basemap.BasemapService.ThinRulesPart1, Mvt.Rule]
  ): Either[com.histmersion.util.basemap.BasemapService.ThinResultPart1, Option[com.histmersion.util.basemap.BasemapService.ThinResultPart2]] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw.flatMap(Mvt.filter(_, rules, others = true)).map { t =>
      Option.when(t.nonEmpty)(if c == PmTiles.Compression.Gzip then PmTiles.gzip(t) else t)
    }

  /** One line for a planned layer: requests and bytes. Pure. */
  def describe(l: Basemap.Layer, p: BasemapFetch.Plan, dryRun: com.histmersion.util.basemap.BasemapService.DescribeDryRun): com.histmersion.util.basemap.BasemapService.DescribeResult =
    val verb = if dryRun then "would download" else "downloaded"
    s"${l.wire} (${p.source.label}, zoom ${p.header.minZoom}–${p.maxZoom}): ${p.tiles.size} tiles, $verb " +
      s"${Backup.humanBytes(p.requestBytes)} in ${p.requests.size} requests (+${p.directoryRequests} for the directories)" +
      // a download ahead says what each zoom costs, so a smaller --max-zoom can be chosen (#3760)
      (if dryRun then s"; by zoom — ${BasemapFetch.perZoomLine(BasemapFetch.perZoom(p))}" else "")

  /** Close a replaced archive a little later, after the requests reading it are done. */
  private def closeLater(f: PmTiles.File): Unit =
    val t = Thread.ofVirtual().unstarted { () => Thread.sleep(30000); f.close() }
    t.start()
