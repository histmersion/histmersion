package com.histmersion
package util.basemap
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{External, Problem, Problems, Raised}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.{BBox, Write}
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.storage.{ByteSource, Location, Storage, Store}

import sttp.model.StatusCode

import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.{Basemap, BasemapApi, BaseStyles}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.jobs.Job
import com.histmersion.api.basemap.BasemapJobKinds
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The terrain base map on this machine (docs/research-terrain-basemap.md): where its two extracts are, the fetch job
  * that downloads them, their removal, and the tiles served to the web page.
  *
  * The extracts are a CACHE — fetched again at will — so their store ([[Location.Basemap]]) is in the XDG cache
  * directory (`basemap.dir` moves it), never in the data directory with the corpus. `config` and the store are read at
  * each use, so a reload is followed; `seaFloor` is the grid the settings choose for the sea floor. `http` and
  * `freeSpace` are the seams a test substitutes for the network and the disk.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class BasemapService(
    config: () => BasemapConfig,
    storage: Storage,
    seaFloor: () => SeaFloorGrid,
    jobs: BasemapJobs,
    http: () => RangeHttp,
    freeSpace: Store => ByteCount = _.usableSpace
)(using clock: java.time.Clock):

  private def cfg = config()

  private def human(n: Long): String = BasemapService.human(n)

  /** The open archives, by where their file is, with the file stamp they were opened at. */
  private val open = ConcurrentHashMap[String, (String, PmTiles.File)]()

  /** The base map's files, where the configuration puts them now. */
  def files: Store = storage.store(Location.Basemap)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key(l: Basemap.Layer): Store.Key = Store.Key.applyUnsafe(l.file.value)

  /** The unfinished download of a layer: its range requests, kept until it is assembled. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def work(l: Basemap.Layer): Store.Key = Store.Key.applyUnsafe(s".${l.wire}.part")

  private def done(written: Either[Store.Error, ?]): Unit =
    written.left.foreach(e => throw Raised(e.message))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def attribution(l: Basemap.Layer): BasemapFetch.Attribution = BasemapFetch.Attribution(l match
    case Basemap.Layer.Vector     => cfg.vector.attribution.value
    case Basemap.Layer.Terrain    => cfg.terrain.attribution.value
    case Basemap.Layer.Labels     => cfg.labels.attribution.value
    case Basemap.Layer.Bathymetry => cfg.bathymetry.attribution.value)

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  /** A stamp of the file as it is now (size, modification time): it changes with each fetch. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def stamp(k: Store.Key, store: Store = files): Option[String] =
    store.stat(k).filter(_.isFile).map(f => s"${f.size.value.toHexString}${f.modified.toEpochMilli.toHexString}")

  /** The layer's archive, opened once and again only when the file changed. */
  def reader(l: Basemap.Layer): Either[Problem, Option[PmTiles.File]] = readerAt(key(l))

  /** The archive `k` (a layer's, a focus region's), opened once and again only when the file changed. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readerAt(k: Store.Key): Either[Problem, Option[PmTiles.File]] =
    val store = files
    val p = store.where(k).value
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
                    .fold[Either[Problem, Option[PmTiles.File]]](Right(None))(
                      PmTiles.File.open(_).map(Some(_))
                    )
                    .map { opened =>
                      opened.foreach(f => open.put(p, (s, f)))
                      // an archive replaced while a request reads it: the old channel stays readable
                      // on macOS and Linux until closed; requests that hold it finish first
                      old.foreach(o => BasemapService.closeLater(o._2))
                      opened
                    }
            }

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def info(l: Basemap.Layer): Basemap.LayerInfo =
    val p = key(l)
    val base = Basemap.LayerInfo(
      l.wire,
      present = Basemap.LayerInfo.Present.Absent,
      file = Basemap.LayerInfo.File(files.where(p).value),
      attribution = Basemap.LayerInfo.Attribution(attribution(l).value)
    )
    reader(l) match
      case Left(e) =>
        val now = files.stat(p)
        base
          .modify(_.present)
          .setTo((if now.exists(_.isFile) then Basemap.LayerInfo.Present.Present else Basemap.LayerInfo.Present.Absent))
          .modify(_.bytes)
          .setTo(com.histmersion.api.ByteCount(now.fold(0L)(_.size.value)))
          .modify(_.error)
          .setTo(Some(e))
      case Right(None)    => base
      case Right(Some(f)) =>
        val h = f.header
        val mine = f.metadata.cursor.downField("histmersion")
        val keepAll = (if mine.get[Boolean]("keepAll").getOrElse(false) then Basemap.FetchQuery.KeepAll.KeepAll
                       else Basemap.FetchQuery.KeepAll.Trim)
        val drop = mine.get[String]("drop").toOption.map(Basemap.DropRule.Spec(_))
        val credit = if l != Basemap.Layer.Bathymetry then attribution(l)
        else
          BasemapService.seaCredit(
            f.metadata.cursor.get[String]("attribution").toOption.map(BasemapFetch.Attribution(_)),
            BasemapFetch.Attribution(cfg.bathymetry.attribution.value),
            cfg.bathymetry.onDemand.enabled.value
          )
        base.copy(
          present = Basemap.LayerInfo.Present.Present,
          attribution = Basemap.LayerInfo.Attribution(credit.value),
          bytes = com.histmersion.api.ByteCount(f.size.value),
          minZoom = Some(Basemap.LayerInfo.MinZoom(h.minZoom.value)),
          maxZoom = Some(Basemap.LayerInfo.MaxZoom(h.maxZoom.value)),
          bbox = Some(Basemap.LayerInfo.Bbox(BasemapService.bboxText(h))),
          tiles = Some(Basemap.LayerInfo.Tiles(h.addressedTiles.value)),
          tileType = Some(Basemap.LayerInfo.TileType(h.tileType.mime.value)),
          source = mine
            .get[String]("build")
            .toOption
            .orElse(mine.get[String]("source").toOption)
            .map(Basemap.LayerInfo.Source(_)),
          fetchedAt = mine.get[String]("fetchedAt").toOption.map(Basemap.LayerInfo.FetchedAt(_)),
          // the relief's tiles also come from the focus regions (and, deeper, from both): their URLs change
          // when a region is added or replaced, so a browser never keeps a tile resampled before
          version = (if l == Basemap.Layer.Terrain then stamp(p).map(_ + focusStamp) else stamp(p))
            .map(Basemap.LayerInfo.Version(_)),
          kept = Option.when(l == Basemap.Layer.Vector)(Basemap.keptLine(keepAll, drop)),
          onDemand = onDemandOf(l).map(_.status),
          focus = if l == Basemap.Layer.Terrain then focusInfo else Nil,
          dropped = if l != Basemap.Layer.Vector then Nil
          else Basemap.droppedLayers(keepAll, drop)
        )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def status: Basemap.Status =
    val layers = Basemap.Layer.values.toList.map(info)
    def ok(l: Basemap.Layer) = layers.exists(i => i.layer == l.wire && i.present.present && i.error.isEmpty)
    val vector = layers.find(i => i.layer == Basemap.Layer.Vector.wire && i.present.present && i.error.isEmpty)
    val states = BaseStyles.states(vector.map(_.dropped))
    Basemap.Status(
      available =
        (if ok(Basemap.Layer.Vector) then Basemap.Status.Available.Available else Basemap.Status.Available.Unavailable),
      dir = Basemap.Status.Dir(files.where().value),
      bbox = Basemap.Status.Bbox(cfg.bbox.value),
      layers = layers,
      fetchCommand = Basemap.Status.FetchCommand(Basemap.FetchCommand),
      freeBytes = Basemap.Status.FreeBytes(freeSpace(files).value),
      running = jobs.running.map(r => Basemap.Status.Running(r.value)),
      mapMaxZoom = Basemap.Status.MapMaxZoom(cfg.mapMaxZoom.value),
      hints = BasemapService.hints(
        ok(Basemap.Layer.Vector),
        ok(Basemap.Layer.Labels),
        vector.exists(_.kept.exists(_.value.contains("before the drop list")))
      ) ++ BasemapService.lackingHint(states),
      styles = states,
      regions = cfg.terrain.focusRegions
        .flatMap(r => Basemap.focusRegion(Basemap.Focus.Spec(r.value)).toOption)
        .map((name, b) => Basemap.Region(name, Basemap.Region.Bbox(BasemapService.bboxText(b))))
    )

  // ── the relief deeper than the extract: focus regions and on demand ────────────────────────────

  /** Where the focus regions' relief archives are. */
  def focusDir: Store.Key = BasemapService.FocusDir

  /** The focus region `name`'s archive. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusKey(name: Basemap.Focus.Name): Store.Key = focusDir.child(Store.EntryName(s"${name.value}.pmtiles"))

  /** The focus regions' archives, by name. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusFiles: List[(Basemap.Focus.Name, Store.Key)] =
    files
      .list(focusDir)
      .filter(f => f.isFile && f.key.name.value.endsWith(".pmtiles"))
      .map(f => (Basemap.Focus.Name(f.key.name.value.stripSuffix(".pmtiles")), f.key))
      .sortBy(_._1.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def focusInfo: List[Basemap.Focus] = focusFiles.flatMap { (name, p) =>
    readerAt(p).toOption.flatten.map { f =>
      val h = f.header
      Basemap.Focus(
        name,
        Basemap.Focus.File(files.where(p).value),
        com.histmersion.api.ByteCount(f.size.value),
        Basemap.Focus.Bbox(BasemapService.bboxText(h)),
        Basemap.Focus.MinZoom(h.minZoom.value),
        Basemap.Focus.MaxZoom(h.maxZoom.value),
        Basemap.Focus.Tiles(h.addressedTiles.value),
        f.metadata.cursor.downField("histmersion").get[String]("fetchedAt").toOption.map(Basemap.Focus.FetchedAt(_))
      )
    }
  }

  /** The focus regions' files as they are now, in a few characters ("" with none). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def focusStamp: String =
    val all = focusFiles.flatMap((n, p) => stamp(p).map(n.value + _))
    if all.isEmpty then "" else "-" + Integer.toHexString(all.mkString("|").hashCode)

  // the on-demand layers of the directory configured now (`basemap.dir` may change with a reload)
  @volatile private var demands = Map.empty[Basemap.Layer, OnDemandTiles]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def demandAt(l: Basemap.Layer, name: String)(make: Store => OnDemandTiles): OnDemandTiles =
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def onDemand: OnDemandTiles =
    demandAt(Basemap.Layer.Terrain, "terrain-cache")(at =>
      OnDemandTiles(
        at,
        () => cfg.terrain.onDemand,
        OnDemandTiles.Source.Planet(() => RangeHttp.Url(cfg.terrain.url.value)),
        http
      )(using clock)
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
        ext = DiskLru.Ext("mvt"),
        transform = b =>
          BasemapService.thin(
            b,
            if PmTiles.isGzip(b) then PmTiles.Compression.Gzip else PmTiles.Compression.None,
            vectorRules()
          )
      )(using clock)
    )

  /** The sea floor outside the extract (#3760): one GET a tile, cached in `sea-cache/`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seaOnDemand: OnDemandTiles =
    demandAt(Basemap.Layer.Bathymetry, "sea-cache")(at =>
      OnDemandTiles(
        at,
        () => cfg.bathymetry.onDemand,
        OnDemandTiles.Source.Template(() => OnDemandTiles.UrlTemplate(cfg.bathymetry.url.value)),
        http,
        ext = DiskLru.Ext("png")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def vectorRules(): Map[Mvt.LayerName, Mvt.Rule] =
    val meta = reader(Basemap.Layer.Vector).toOption.flatten.map(_.metadata.cursor.downField("histmersion"))
    val keepAll = meta.flatMap(_.get[Boolean]("keepAll").toOption).getOrElse(false)
    val drop = meta.flatMap(_.get[String]("drop").toOption).getOrElse(cfg.vector.drop.value)
    if keepAll then Map.empty
    else BasemapService.mvtRules(Basemap.parseDrop(Basemap.DropRule.Spec(drop)).toOption.getOrElse(Nil))

  // `var`: the newest build's key, asked of the provider once (when neither the extract nor the config names one)
  @volatile private var latestBuild: Option[String] = None

  /** The planet the vector tiles are read from: the build the extract came from, else the configured one, else the
    * provider's newest. "" when none can be named (the read then fails, as offline).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def vectorPlanet(): RangeHttp.Url =
    val fromExtract = reader(Basemap.Layer.Vector).toOption.flatten
      .flatMap(_.metadata.cursor.downField("histmersion").get[String]("build").toOption)
      .filter(_.endsWith(".pmtiles"))
    val pinned = Option(cfg.vector.build.value.trim).filter(b => b.nonEmpty && !b.equalsIgnoreCase("latest"))
    RangeHttp.Url(
      fromExtract
        .orElse(pinned)
        .orElse(latestBuild)
        .orElse {
          val got =
            http()
              .text(RangeHttp.Url(cfg.vector.buildsUrl.value))
              .flatMap(t => BasemapService.builds(BasemapService.JsonText(t.value)))
              .toOption
              .flatMap(_.lastOption.map(_._1.value))
          latestBuild = got
          got
        }
        .fold("")(b => cfg.vector.url.value.replace("{build}", b))
    )

  /** A relief tile deeper than the extract (or outside it): a focus region's, else the on-demand one. */
  private def deeperRelief(
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow,
      extractMax: BasemapFetch.Plan.MaxZoom
  ): Either[Problem, Option[PmTiles.TileBytes]] =
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def fromAncestor(
      z: PmTiles.Zoom,
      x: PmTiles.TileColumn,
      y: PmTiles.TileRow,
      extract: PmTiles.File
  ): Option[PmTiles.TileBytes] =
    val key = s"${stamp(this.key(Basemap.Layer.Terrain)).getOrElse("")}$focusStamp|${z.value}/${x.value}/${y.value}"
    Option(resampled.get(key)).orElse {
      val focus = focusFiles.flatMap(f => readerAt(f._2).toOption.flatten.map(f._2 -> _))
      def at(az: Int, ax: Long, ay: Long): Option[(String, PmTiles.TileBytes)] =
        focus.iterator
          .filter((_, f) => az >= f.header.minZoom.value && az <= f.header.maxZoom.value)
          .flatMap((p, f) =>
            f.tile(PmTiles.Zoom(az), PmTiles.TileColumn(ax), PmTiles.TileRow(ay))
              .toOption
              .flatten
              .map(b => (s"${f.where}|${stamp(p).getOrElse("")}|$az/$ax/$ay", b))
          )
          .nextOption()
          .orElse(
            Option
              .when(az <= extract.header.maxZoom.value)(())
              .flatMap(_ =>
                extract
                  .tile(PmTiles.Zoom(az), PmTiles.TileColumn(ax), PmTiles.TileRow(ay))
                  .toOption
                  .flatten
                  .map(b => (s"extract|$az/$ax/$ay", b))
              )
          )
      // at most AncestorDepth zooms up: a tile far from every region is not drawn from the world's few
      val found = (z.value - 1 to math.max(0, z.value - BasemapService.AncestorDepth) by -1).iterator
        .flatMap { az =>
          val d = z.value - az
          at(az, x.value >> d, y.value >> d).map(t => (az, t))
        }
        .nextOption()
      found.flatMap { case (az, (k, bytes)) =>
        val img = Option(decoded.get(k))
          .map(Right(_))
          .getOrElse(TerrainResample.decode(bytes).map { i => decoded.put(k, i); i })
        img match
          case Left(e) =>
            scribe.warn(s"relief tile $az (for ${z.value}/${x.value}/${y.value}) cannot be read: ${e.sentence}")
            None
          case Right(i) if !i.isSquare =>
            None
          case Right(i) =>
            val png = Png.encodeRgb(
              i.width,
              i.height,
              TerrainResample.resample(i.pixels, i.width, TerrainResample.ZoomDelta(z.value - az), x, y)
            )
            resampled.put(key, png)
            Some(png)
      }
    }

  /** The deepest relief zoom anything here holds — the extract, a focus region, the on-demand relief: what the page's
    * elevation source asks for at most, and where the cutting from ancestors stops.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def deepestRelief(extractMax: BasemapFetch.Plan.MaxZoom): Int =
    val od = cfg.terrain.onDemand
    (extractMax.value :: (if od.enabled.value then List(od.maxZoom.value) else Nil) ++
      focusFiles.flatMap(f => readerAt(f._2).toOption.flatten.map(_.header.maxZoom.value))).max

  /** Relief tiles resampled lately, and the ancestors they were cut from (a view asks for neighbours). */
  private val resampled =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, PmTiles.TileBytes](64, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, PmTiles.TileBytes]): Boolean = size() > 96)
  private val decoded =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, Vp8l.Image](16, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, Vp8l.Image]): Boolean = size() > 8)

  /** Subset tiles cut lately, by (file stamp, layer, tile, subset): a pan asks for the same few again. */
  private val cut =
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, PmTiles.TileBytes](256, 0.75f, true):
      override def removeEldestEntry(e: java.util.Map.Entry[String, PmTiles.TileBytes]): Boolean = size() > 512)

  /** One tile with its content type and encoding: as stored, or — a vector tile with `subset` — only the layers and
    * geometries asked for (and with `keys` only those properties); 204 where the extract (or the subset) has none.
    * Answers the bytes, their content type, their content encoding, the cache control and the status.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tile(
      layer: Basemap.Layer.Wire,
      z: Basemap.Zoom,
      x: Basemap.Tile.Column,
      y: Basemap.Tile.Row,
      subset: Option[Basemap.Subset.Spec] = None,
      keys: Option[BasemapService.TileKeys] = None
  ): Either[
    Api.Error,
    (
        BasemapApi.Tile.Bytes,
        BasemapApi.Tile.ContentType,
        Option[BasemapApi.Tile.Etag],
        BasemapApi.Tile.Encoding,
        StatusCode
    )
  ] =
    val cache = s"public, max-age=${cfg.tileCacheSeconds.value}"
    val pz = PmTiles.Zoom(z.value)
    val px = PmTiles.TileColumn(x.value)
    val py = PmTiles.TileRow(y.value)
    def reply(
        bytes: PmTiles.TileBytes,
        contentType: String,
        encoding: Option[String],
        cacheControl: String,
        status: StatusCode
    ) =
      (
        BasemapApi.Tile.Bytes(bytes.value),
        BasemapApi.Tile.ContentType(contentType),
        encoding.map(BasemapApi.Tile.Etag(_)),
        BasemapApi.Tile.Encoding(cacheControl),
        status
      )
    // the subset at this zoom: a layer asked for only from a deeper zoom is left out here
    val sub = subset.map(s => Basemap.Subset.Spec(s.value.trim)).filter(_.value.nonEmpty).map(Basemap.subsetAt(_, z))
    val props: Option[Set[Mvt.Rule.Props]] = keys
      .map(_.value.split(",").map(_.trim).filter(_.nonEmpty).toSet)
      .filter(_.nonEmpty)
      .map(_.map(Mvt.Rule.Props(_)))
    for
      rules <- sub
        .fold[Either[Api.Error, Option[Map[Basemap.Layer.Wire, Basemap.Read]]]](Right(None))(s =>
          Basemap.parseSubset(s).map(Some(_)).left.map(e => BasemapService.invalid(e.within(Problem.Field("layers"))))
        )
      l <- Basemap.Layer
        .parse(layer)
        .toRight(
          BasemapService.notFound(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.named[Basemap.Layer],
              Problem.Raw.of(layer),
              Basemap.Layer.values.toVector.map(l => Problem.Raw.of(l.wire))
            )
          )
        )
      f <- reader(l).left
        .map(e => Api.Error(e))
        .flatMap(
          _.toRight(
            BasemapService.notFound(
              Problem.NotFound.Undownloaded(Problem.Raw.of(l.title), Problem.Raw(Basemap.FetchCommand))
            )
          )
        )
      stored <- f.tile(pz, px, py).left.map(e => Api.Error(e))
      // the relief deeper than the extract: a focus region's, or read now from the provider (no tile, and
      // not cached by the browser, when it cannot be read now — offline)
      deeper = Option
        .when(stored.isEmpty && l == Basemap.Layer.Terrain && pz > f.header.maxZoom)(
          deeperRelief(pz, px, py, BasemapFetch.Plan.MaxZoom(f.header.maxZoom.value))
        )
        // the map and the sea floor outside the extract, or deeper: read now from the provider (#3760)
        .orElse(
          Option
            .when(
              stored.isEmpty && (l == Basemap.Layer.Vector || l == Basemap.Layer.Bathymetry) &&
                (pz > f.header.maxZoom || !BasemapService.inside(f.header, pz, px, py))
            )(onDemandOf(l))
            .flatten
            .filter(_.servesAt(pz))
            .map(_.tile(pz, px, py))
        )
      found = stored.orElse(deeper.flatMap(_.toOption.flatten))
      // none of them has it: cut from the nearest ancestor (offline, the on-demand relief failing: not kept
      // by the browser, so the provider's own tile replaces it once it can be read)
      fallback = Option
        .when(
          found.isEmpty && deeper.isDefined && pz.value <= deepestRelief(
            BasemapFetch.Plan.MaxZoom(f.header.maxZoom.value)
          )
        )(
          fromAncestor(pz, px, py, f)
        )
        .flatten
      mime = f.header.tileType.mime.value
      out <- (found, rules.filter(_ => f.header.tileType == PmTiles.TileType.Mvt)) match
        case (None, _) if fallback.isDefined =>
          Right(
            reply(
              fallback.get,
              "image/png",
              None,
              if deeper.exists(_.isLeft) then "no-store" else cache,
              StatusCode.Ok
            )
          )
        case (None, _) if deeper.exists(_.isLeft) =>
          Right(reply(PmTiles.TileBytes(Array.emptyByteArray), mime, None, "no-store", StatusCode.NoContent))
        case (None, _) =>
          Right(reply(PmTiles.TileBytes(Array.emptyByteArray), mime, None, cache, StatusCode.NoContent))
        case (Some(b), None) =>
          // a tile read on demand is stored as its provider sends it (a vector one gzipped)
          val enc = Option.when(
            (f.header.tileCompression == PmTiles.Compression.Gzip || stored.isEmpty) && PmTiles.isGzip(b)
          )("gzip")
          Right(reply(b, mime, enc, cache, StatusCode.Ok))
        case (Some(b), Some(rs)) =>
          val key =
            s"$layer|${stamp(this.key(l)).getOrElse("")}|${pz.value}/${px.value}/${py.value}|${sub.fold("")(_.value)}|${props.fold("")(_.toList.map(_.value).sorted.mkString(","))}"
          Option(cut.get(key))
            .map(Right(_))
            .getOrElse(
              BasemapService
                .subset(b, if PmTiles.isGzip(b) then PmTiles.Compression.Gzip else f.header.tileCompression, rs, props)
                .left
                .map(e => Api.Error(e))
                .map { t => cut.put(key, t); t }
            )
            .map(t =>
              if t.value.isEmpty then reply(t, mime, None, cache, StatusCode.NoContent)
              else reply(t, mime, Some("gzip"), cache, StatusCode.Ok)
            )
    yield out

  // ── removing ───────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def remove(q: Basemap.RemoveQuery): Either[Api.Error, Basemap.Removed] =
    val which = q.layer.value.trim
    val layers: Either[Problem, List[Basemap.Layer]] =
      if which.equalsIgnoreCase("none") then Right(Nil)
      else Basemap.Layer.parseMany(Basemap.Layer.Wires(q.layer.value))
    val all = which.equalsIgnoreCase("all") || which.isEmpty
    val focus: Either[Problem, List[Basemap.Focus.Name]] = q.focus.map(_.value.trim).filter(_.nonEmpty) match
      case Some("all") => Right(focusFiles.map(_._1))
      case Some(n)     =>
        Basemap
          .validFocusName(Basemap.Focus.Name(n))
          .flatMap(n =>
            Either.cond(
              focusFiles.exists(_._1 == n) || files.isDirectory(focusWork(n)),
              List(n),
              Problem.Invalid.Unknown(
                Problem.TypeName.of(Basemap.Focus.Name),
                Problem.Raw.of(n),
                focusFiles.map(f => Problem.Raw.of(f._1)).toVector,
                Vector(Problem.Field("focus"))
              )
            )
          )
      case None if all => Right(focusFiles.map(_._1))
      case None        => Right(Nil)
    layers.and(focus).left.map(e => BasemapService.invalid(e)).flatMap { (ls, fs) =>
      if jobs.running.isDefined then Left(jobs.busy(BasemapJobs.Target("a base-map fetch"), Some(BasemapApi.BusyCode)))
      else
        val store = files
        def gone(f: Store.Key, w: Store.Key): List[(String, Long)] =
          Option(open.remove(store.where(f).value)).foreach(_._2.close())
          val bytes = store.stat(f).filter(_.isFile).fold(0L)(_.size.value)
          val file = Option.when(store.delete(f))((store.where(f).value, bytes))
          val parts = store.deleteTree(w).map(b => (store.where(w).value, b.value))
          file.toList ++ parts.toList
        val removed = ls.flatMap(l => gone(key(l), work(l))) ++ fs.flatMap(n => gone(focusKey(n), focusWork(n)))
        val cached = Option
          .when(q.cache.cache || all) {
            Basemap.Layer.values.toList.flatMap(onDemandOf).flatMap { od =>
              val c = od.store
              Option.when(c.exists(Store.Key.Root))(
                (c.where().value, od.cache.clear().value + c.deleteTree(Store.Key.Root).fold(0L)(_.value))
              )
            }
          }
          .toList
          .flatten
        val out = removed ++ cached
        Right(
          Basemap.Removed(
            out.map(r => Basemap.Removed.Value(r._1)),
            com.histmersion.api.ByteCount(out.map(_._2).sum)
          )
        )
    }

  /** The unfinished download of a focus region. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusWork(name: Basemap.Focus.Name): Store.Key = Store.Key.applyUnsafe(s".terrain-focus-${name.value}.part")

  // ── fetching ───────────────────────────────────────────────────────────────────────────────────

  /** What the next fetch of these options will do, decided before the job starts: refused here when an option is out of
    * range, so the person learns it at once.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final private case class Ask(
      layers: List[Basemap.Layer],
      bbox: BBox,
      vectorZoom: BasemapFetch.Plan.MaxZoom,
      terrainZoom: BasemapFetch.Plan.MaxZoom,
      dryRun: Boolean,
      keepAll: Boolean,
      focus: Option[(Basemap.Focus.Name, BBox)] = None
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def ask(q: Basemap.FetchQuery): Either[Problems, Ask] =
    def zoom(z: Int, what: Problem.Field): Either[Problem, BasemapFetch.Plan.MaxZoom] =
      Either.cond(
        z >= 0 && z <= Basemap.MaxZoomCap,
        BasemapFetch.Plan.MaxZoom(z),
        Problem.Invalid.OutOfRange(
          Problem.TypeName.of(BasemapFetch.Plan.MaxZoom),
          Problem.Raw.of(z.toLong),
          Problem.Invalid.Bounds(Some(Problem.Raw.of(0L)), Some(Problem.Raw.of(Basemap.MaxZoomCap.toLong))),
          Vector(what)
        )
      )
    val asked = q.bbox.map(_.value.trim).filter(_.nonEmpty)
    for
      ls <- Basemap.Layer.parseMany(Basemap.Layer.Wires(q.layer.value))
      b <- Basemap
        .validBBox(Basemap.Focus.Bbox(asked.getOrElse(cfg.bbox.value)))
      onlyTerrain = ls == List(Basemap.Layer.Terrain)
      // the relief alone: --max-zoom is its depth too
      tzAsked = q.terrainMaxZoom.map(_.value).orElse(q.maxZoom.map(_.value).filter(_ => onlyTerrain))
      focus <- BasemapService.focusOf(
        q.focus,
        asked.map(_ => b),
        onlyTerrain,
        tzAsked.map(BasemapFetch.Plan.MaxZoom(_)),
        cfg.terrain
      )
      vz <- zoom(q.maxZoom.map(_.value).getOrElse(cfg.vector.maxZoom.value), Problem.Field("--max-zoom"))
      tz <- zoom(
        tzAsked.getOrElse(if focus.isDefined then cfg.terrain.onDemand.maxZoom.value else cfg.terrain.maxZoom.value),
        Problem.Field("--terrain-max-zoom")
      )
      _ <- Either.cond(
        focus.isEmpty || tz.value > cfg.terrain.maxZoom.value,
        (),
        Problem.Invalid.Broken.here(Problem.Invalid.Rule.FocusDeeper(Problem.Count.of(cfg.terrain.maxZoom)))
      )
      _ <- Basemap.parseDrop(Basemap.DropRule.Spec(cfg.vector.drop.value))
    yield Ask(
      if focus.isDefined then List(Basemap.Layer.Terrain) else ls,
      focus.fold(b)(_._2),
      vz,
      tz,
      q.dryRun.dryRun,
      q.keepAll.keepAll,
      focus
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fetch(q: Basemap.FetchQuery)(using Creator.New): Either[Api.Error, JobInfo] =
    jobs.guarded(BasemapJobs.Operation("basemap/fetch")):
      ask(q).left.map(e => BasemapService.invalid(e)).flatMap { a =>
        val dry = if a.dryRun then "dry run: " else ""
        val what = a.focus match
          case Some((name, b)) =>
            s"${dry}terrain base map — the relief of focus region '${name.value}' " +
              s"(${BasemapService.bboxText(b)}), zoom ${cfg.terrain.maxZoom.value + 1}–${a.terrainZoom.value}"
          case None =>
            s"${dry}terrain base map — ${a.layers.map(_.wire.value).mkString(" + ")}, " +
              s"bbox ${q.bbox.fold(cfg.bbox.value)(_.value)}, zoom ≤ " + a.layers
                .collect {
                  case Basemap.Layer.Vector     => s"${a.vectorZoom.value} (vector)"
                  case Basemap.Layer.Terrain    => s"${a.terrainZoom.value} (terrain)"
                  case Basemap.Layer.Bathymetry =>
                    s"${cfg.bathymetry.extractMaxZoom.value} (sea floor, ${seaFloor().wire})"
                }
                .mkString(" / ")
        jobs.start(
          BasemapService.JobKind,
          BasemapJobs.Description(what),
          BasemapJobs.Target("a base-map fetch"),
          Some(BasemapApi.BusyCode)
        )(note =>
          run(a, n => note(BasemapJobs.Target(n.value)))
            .map(BasemapJobs.Description(_))
        )
      }

  /** The job: each layer in turn — planned, checked against the free disk, downloaded, assembled. */
  private def run(a: Ask, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    a.focus match
      case Some((name, _)) => focusFetch(a, name, note)
      case None            => layers(a, note)

  /** A focus region's relief: zooms deeper than the extract, into `terrain-focus/<name>.pmtiles`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def focusFetch(
      a: Ask,
      name: Basemap.Focus.Name,
      note: BasemapFetch.Note => Unit
  ): Either[Problem, String] =
    val h = http()
    val src =
      BasemapFetch.Source(RangeHttp.Url(cfg.terrain.url.value), BasemapFetch.Source.Label(cfg.terrain.url.value))
    val extra = BasemapFetch.Note(s"focus ${name.value} from ${cfg.terrain.maxZoom.value + 1}")
    for
      plan <- BasemapFetch.plan(
        h,
        src,
        a.bbox,
        a.terrainZoom,
        pace,
        note,
        minZoom = Some(BasemapFetch.Plan.MinZoom(cfg.terrain.maxZoom.value + 1))
      )
      out <-
        if a.dryRun then
          Right(s"focus '${name.value}': " + BasemapService.describe(Basemap.Layer.Terrain, plan, dryRun = true))
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
          ).map(s => s"focus '${name.value}': $s")
    yield out

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def layers(a: Ask, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    a.layers
      .foldLeft[Either[Problem, List[String]]](Right(Nil)) { (acc, l) =>
        acc.flatMap(done => one(a, l, s => note(BasemapFetch.Note(s"${l.wire}: ${s.value}"))).map(done :+ _))
      }
      .map(_.mkString("; "))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pace = BasemapFetch.Pace(
    BasemapFetch.Pace.GapMs(cfg.requestGapMs.value.toLong),
    ByteCount.applyUnsafe(cfg.maxRequestMb.value.toLong * 1024 * 1024),
    BasemapFetch.Pace.Overfetch(cfg.overfetch.value)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def minFree: ByteCount = ByteCount.applyUnsafe((cfg.minFreeGb.value * 1e9).toLong)

  /** The source of a layer: the vector build named by the config ("latest": the newest in the provider's list — unless
    * an unfinished download of an earlier one is there to finish), or the terrain URL.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def source(
      l: Basemap.Layer,
      a: Ask,
      extra: BasemapFetch.Note
  ): Either[Problem, BasemapFetch.Source] = l match
    case Basemap.Layer.Terrain =>
      Right(BasemapFetch.Source(RangeHttp.Url(cfg.terrain.url.value), BasemapFetch.Source.Label(cfg.terrain.url.value)))
    case Basemap.Layer.Labels =>
      Left(Problem.Invalid.Broken.here(Problem.Invalid.Rule.LabelsUnranged)) // `labels` fetches them
    case Basemap.Layer.Bathymetry =>
      Left(Problem.Invalid.Broken.here(Problem.Invalid.Rule.SeaFloorUnranged)) // `bathymetry` fetches it
    case Basemap.Layer.Vector =>
      val pinned = cfg.vector.build.value.trim
      resumable(l, a, extra) match
        case Some(src) if pinned.equalsIgnoreCase("latest") || src.label.value == pinned => Right(src)
        case _                                                                           =>
          val builds = http()
            .text(RangeHttp.Url(cfg.vector.buildsUrl.value))
            .flatMap(t => BasemapService.builds(BasemapService.JsonText(t.value)))
          val chosen: Either[Problem, (BasemapService.BuildName, Long)] =
            if pinned.equalsIgnoreCase("latest") || pinned.isEmpty then
              builds.flatMap(
                _.lastOption.toRight(
                  Problem.Io.Corrupt(
                    Problem.Io.Format.Json,
                    Problem.Where.of(cfg.vector.buildsUrl),
                    Problem.Io.Damage.Structure(Problem.Io.Flaw.NoBuild)
                  )
                )
              )
            else
              builds
                .map(_.find(_._1.value == pinned).getOrElse((BasemapService.BuildName(pinned), -1L)))
                .orElse(Right((BasemapService.BuildName(pinned), -1L)))
          chosen.map((key, size) =>
            BasemapFetch.Source(
              RangeHttp.Url(cfg.vector.url.value.replace("{build}", key.value)),
              BasemapFetch.Source.Label(key.value),
              Option.when(size > 0)(BasemapFetch.Source.ExpectedSize(size))
            )
          )

  /** The source an unfinished download of the same region and depth was reading. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resumable(l: Basemap.Layer, a: Ask, extra: BasemapFetch.Note): Option[BasemapFetch.Source] =
    files
      .get(work(l).child(Store.EntryName("state.json")))
      .map(_.text().value)
      .flatMap(s => RawJson.parse(s).toOption)
      .flatMap { j =>
        val c = j.cursor
        val same =
          c.get[Int]("maxZoom").toOption.contains(zoomOf(l, a).value) &&
            c.get[String]("extra").toOption.contains(extra.value) &&
            c.get[String]("bbox")
              .toOption
              .flatMap(s => BBox.parse(BBox.Text(s)))
              .exists(b => BasemapService.near(b, a.bbox))
        for
          url <- c.get[String]("url").toOption if same
          label <- c.get[String]("label").toOption.orElse(Some(url))
          size = c.get[Long]("size").toOption
        yield BasemapFetch.Source(
          RangeHttp.Url(url),
          BasemapFetch.Source.Label(label),
          size.map(BasemapFetch.Source.ExpectedSize(_))
        )
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def zoomOf(l: Basemap.Layer, a: Ask): BasemapFetch.Plan.MaxZoom = l match
    case Basemap.Layer.Vector     => a.vectorZoom
    case Basemap.Layer.Terrain    => a.terrainZoom
    case Basemap.Layer.Labels     => BasemapFetch.Plan.MaxZoom(cfg.labels.maxZoom.value)
    case Basemap.Layer.Bathymetry => BasemapFetch.Plan.MaxZoom(cfg.bathymetry.extractMaxZoom.value)

  private def one(a: Ask, l: Basemap.Layer, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    l match
      case Basemap.Layer.Labels     => labels(a, note)
      case Basemap.Layer.Bathymetry =>
        seaFloor() match
          case SeaFloorGrid.Etopo1 => bathymetry(a, note)
          case SeaFloorGrid.Gebco  => gebco(a, note)
      case _ => ranged(a, l, note)

  /** A PMTiles layer (vector, terrain): planned, checked against the free disk, downloaded, assembled. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def ranged(
      a: Ask,
      l: Basemap.Layer,
      note: BasemapFetch.Note => Unit
  ): Either[Problem, String] =
    val rules =
      if a.keepAll then Nil
      else Basemap.parseDrop(Basemap.DropRule.Spec(cfg.vector.drop.value)).toOption.getOrElse(Nil)
    val extra = BasemapFetch.Note(
      if l != Basemap.Layer.Vector then "" else if a.keepAll then "keep-all" else s"drop: ${cfg.vector.drop.value}"
    )
    val h = http()
    for
      src <- source(l, a, extra)
      _ = note(BasemapFetch.Note(s"reading ${src.label.value}"))
      plan <- BasemapFetch.plan(h, src, a.bbox, zoomOf(l, a), pace, note)
      out <-
        if a.dryRun then Right(BasemapService.describe(l, plan, dryRun = true))
        else download(l, plan, rules, extra, h, note, a.keepAll)
    yield out

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def download(
      l: Basemap.Layer,
      plan: BasemapFetch.Plan,
      rules: List[Basemap.DropRule],
      extra: BasemapFetch.Note,
      h: RangeHttp,
      note: BasemapFetch.Note => Unit,
      keepAll: Boolean,
      out: Option[Store.Key] = None,
      workDir: Option[Store.Key] = None
  ): Either[Problem, String] =
    val store = files
    done(store.makeDirectory())
    val workKey = workDir.getOrElse(this.work(l))
    val work = store.sub(workKey)
    val target = out.getOrElse(key(l))
    val state = BasemapFetch
      .stateOf(plan, extra)
      .deepMerge(Json.obj("label" -> Json.fromString(plan.source.label.value)))
    val had = BasemapFetch.prepare(work, state)
    val pendingBytes = BasemapFetch.pending(plan, work).map(_._2.value).sum
    // the local archive is at most the distinct contents the requests carry (the vector one is far
    // smaller once thinned); it is written beside the downloaded parts before they are removed
    val outBytes = plan.contentBytes.value
    def room(next: ByteCount): Either[Problem, Unit] =
      val free = freeSpace(store).value
      val left = free - next.value - outBytes
      Either.cond(
        left >= minFree.value,
        (),
        Problem.Refused.Space(
          Problem.Refused.Work.Layer(Problem.Raw.of(l.title)),
          Problem.Count(pendingBytes + outBytes),
          Problem.Io.Path.of(store.where()),
          Problem.Count(free),
          Problem.Count.of(minFree)
        )
      )
    val rulesOf = BasemapService.mvtRules(rules)
    def transformOf(): PmTiles.TileBytes => Either[Problem, Option[PmTiles.TileBytes]] = l match
      case Basemap.Layer.Vector if !keepAll =>
        (b: PmTiles.TileBytes) => BasemapService.thin(b, plan.header.tileCompression, rulesOf)
      case _ => (b: PmTiles.TileBytes) => Right(Some(b))
    for
      _ <- room(ByteCount.applyUnsafe(pendingBytes))
      _ = if had.value > 0 then
        note(BasemapFetch.Note(s"resuming: ${had.value} of ${plan.requests.size} parts are here already"))
      _ <- BasemapFetch.download(h, plan, work, pace, note, room)
      transform = transformOf()
      meta = BasemapFetch.localMetadata(
        plan,
        rules.filter(_.whole.whole).map(_.layer).toSet,
        attribution(l),
        BasemapFetch.FetchedAt(Instant.now(clock).toString),
        l,
        Option.when(l == Basemap.Layer.Vector)(
          (
            (if keepAll then BasemapFetch.Filtered.Filtered else BasemapFetch.Filtered.Whole),
            BasemapFetch.FilterSpec(cfg.vector.drop.value)
          )
        )
      )
      written <- BasemapFetch.assemble(plan, work, store, target, transform, BasemapFetch.localHeader(plan), meta, note)
    yield
      val _ = store.deleteTree(workKey)
      val _ = readerAt(target)
      s"${BasemapService.describe(l, plan, dryRun = false)}; stored ${written._2.value} tiles in ${human(written._1.value)} at ${store.where(target).value}"

  /** The sea floor: every tile of zooms 0…`bathymetry.max-zoom` over the region, one GET each (a public bucket of map
    * tiles: no range requests), kept in a work directory as they come — so an interrupted fetch resumes — then written
    * as `bathymetry.pmtiles`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bathymetry(a: Ask, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    val bc = cfg.bathymetry
    // every tile of the region, in tile-id order (the archive's)
    val tiles = (0 to bc.maxZoom.value)
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(a.bbox, Basemap.Zoom(z))
        for x <- x0.value to x1.value; y <- y0.value to y1.value
        yield (PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y)), (z, x, y))
      }
      .sortBy[Long](_._1.value)
    val ids = tiles.map(_._1)
    val each = 80L * 1024 // a tile, about (measured: 2026-10-20)
    if a.dryRun then
      Right(
        s"bathymetry (zoom 0–${bc.maxZoom.value}): would download ${ids.length} tiles, about ${human(ids.length * each)}"
      )
    else
      val need = ids.length * each * 2
      val store = files
      val free = freeSpace(store).value
      if free - need < minFree.value then
        Left(
          Problem.Refused.Space(
            Problem.Refused.Work.SeaFloor,
            Problem.Count(need),
            Problem.Io.Path.of(store.where()),
            Problem.Count(free),
            Problem.Count.of(minFree)
          )
        )
      else
        val work = store.sub(this.work(Basemap.Layer.Bathymetry))
        done(work.makeDirectory())
        val h = http()
        val coords = tiles.map(_._2)
        val got = coords.zipWithIndex.foldLeft[Either[Problem, Int]](Right(0)) { case (acc, ((z, x, y), i)) =>
          acc.flatMap { n =>
            val f = Store.Key.applyUnsafe(s"$z-$x-$y.png")
            val none = Store.Key.applyUnsafe(s"$z-$x-$y.none")
            if work.isFile(f) || work.isFile(none) then Right(n)
            else
              if n > 0 then pace.sleep(BasemapFetch.Pace.GapMs(bc.requestGapMs.value.toLong))
              if Thread.interrupted() then throw InterruptedException()
              if i % 50 == 0 then note(BasemapFetch.Note(s"downloading ${i + 1} of ${ids.length} tiles"))
              h.file(
                RangeHttp.Url(
                  bc.url.value.replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString)
                )
              ).map {
                case Some(b) => done(work.write(f, ByteSource.Content(b.value))); n + 1
                case None    => done(work.write(none, ByteSource.Content(Array.emptyByteArray))); n + 1
              }
          }
        }
        got.map { _ =>
          note(BasemapFetch.Note("writing bathymetry.pmtiles"))
          val out = key(Basemap.Layer.Bathymetry)
          val w = PmTilesWriter(store, out)
          var stored = 0L // tiles written
          ids.zip(coords).foreach { case (id, (z, x, y)) =>
            work.get(Store.Key.applyUnsafe(s"$z-$x-$y.png")).foreach { f =>
              w.add(id, PmTiles.TileBytes(f.bytes().value)); stored += 1
            }
          }
          val header = BasemapService.pngHeader(bc.maxZoom.value, a.bbox)
          val meta = Json.obj(
            "name" -> Json.fromString("histmersion sea floor"),
            "attribution" -> Json.fromString(bc.attribution.value),
            "histmersion" -> Json.obj(
              "layer" -> Json.fromString(Basemap.Layer.Bathymetry.wire.value),
              "source" -> Json.fromString(bc.url.value),
              "build" -> Json.fromString("ETOPO1 (Mapzen terrain tiles)"),
              "bbox" -> Json.fromString(BasemapService.bboxText(a.bbox)),
              "maxZoom" -> Json.fromInt(bc.maxZoom.value),
              "fetchedAt" -> Json.fromString(Instant.now(clock).toString)
            )
          )
          val bytes = w.finish(header, meta)
          val _ = store.deleteTree(this.work(Basemap.Layer.Bathymetry))
          val _ = reader(Basemap.Layer.Bathymetry)
          s"bathymetry (zoom 0–${bc.maxZoom.value}): $stored tiles, ${human(bytes.value)} at ${store.where(out).value}"
        }

  /** The sea floor from the GEBCO grid ([[GebcoSeaFloor]]): the GeoTIFF rows the region needs read by range into the
    * work directory (resumed when interrupted), then made into the tiles of zooms 0…`bathymetry.gebco.max-zoom`,
    * written as `bathymetry.pmtiles`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def gebco(a: Ask, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    val bc = cfg.bathymetry
    val sea =
      GebcoSeaFloor(bc.gebco, bc.url, http(), ByteCount.applyUnsafe(cfg.maxRequestMb.value.toLong * 1024 * 1024))
    note(BasemapFetch.Note("reading the GEBCO GeoTIFFs' headers"))
    sea.plan(a.bbox, PmTiles.Zoom(bc.gebco.maxZoom.value)).flatMap { plan =>
      if a.dryRun then Right(sea.describe(plan).value)
      else
        val store = files
        val free = freeSpace(store).value
        if free - sea.needs(plan).value < minFree.value then
          Left(
            Problem.Refused.Space(
              Problem.Refused.Work.Gebco,
              Problem.Count.of(sea.needs(plan)),
              Problem.Io.Path.of(store.where()),
              Problem.Count(free),
              Problem.Count.of(minFree)
            )
          )
        else
          val work = store.sub(this.work(Basemap.Layer.Bathymetry))
          for
            _ <- sea.download(plan, work, BasemapFetch.Pace.GapMs(cfg.requestGapMs.value.toLong), note)
            out = key(Basemap.Layer.Bathymetry)
            w = PmTilesWriter(store, out)
            n <- sea.build(plan, work, w.add, note)
          yield
            val header = BasemapService.pngHeader(bc.gebco.maxZoom.value, a.bbox)
            val meta = Json.obj(
              "name" -> Json.fromString("histmersion sea floor"),
              "attribution" -> Json.fromString(bc.gebco.attribution.value),
              "histmersion" -> Json.obj(
                "layer" -> Json.fromString(Basemap.Layer.Bathymetry.wire.value),
                "source" -> Json.fromString(bc.gebco.url.value),
                "build" -> Json.fromString("GEBCO grid"),
                "bbox" -> Json.fromString(BasemapService.bboxText(a.bbox)),
                "maxZoom" -> Json.fromInt(bc.gebco.maxZoom.value),
                "fetchedAt" -> Json.fromString(Instant.now(clock).toString)
              )
            )
            val bytes = w.finish(header, meta)
            val _ = store.deleteTree(this.work(Basemap.Layer.Bathymetry))
            val _ = reader(Basemap.Layer.Bathymetry)
            s"bathymetry from GEBCO (zoom 0–${bc.gebco.maxZoom.value}): ${n.value} tiles from ${human(plan.readBytes.value)} of the grid, " +
              s"${human(bytes.value)} at ${store.where(out).value}"
    }

  /** The physical names: Natural Earth's label files read whole (a few MB each), cut to the region and written as
    * `labels.pmtiles` (about 1.4 MB for the default region) — no range requests, nothing to resume.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def labels(a: Ask, note: BasemapFetch.Note => Unit): Either[Problem, String] =
    val lc = cfg.labels
    val files = lc.files.map(f => f -> lc.url.value.replace("{file}", f.value))
    if a.dryRun then
      Right(
        s"labels (Natural Earth, zoom 0–${lc.maxZoom.value}): would download ${files.size} files — ${lc.files.map(_.value).mkString(", ")} — " +
          "about 20 MB, of which about 1.4 MB is kept"
      )
    else
      val store = this.files
      val free = freeSpace(store).value
      val need = 64L * 1024 * 1024
      if free - need < minFree.value then
        Left(
          Problem.Refused.Space(
            Problem.Refused.Work.Labels,
            Problem.Count(need),
            Problem.Io.Path.of(store.where()),
            Problem.Count(free),
            Problem.Count.of(minFree)
          )
        )
      else
        val h = http()
        val read = files.foldLeft[Either[Problem, List[BasemapLabels.Label]]](Right(Nil)) { case (acc, (f, url)) =>
          acc.flatMap { got =>
            note(BasemapFetch.Note(s"reading ${f.value}"))
            if got.nonEmpty then pace.sleep(pace.gapMs)
            h.text(RangeHttp.Url(url))
              .flatMap(t =>
                RawJson
                  .parse(t.value)
                  .left
                  .map(e =>
                    Problem.Io.Corrupt(
                      Problem.Io.Format.Json,
                      Problem.Where(url),
                      Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
                    )
                  )
              )
              .flatMap(BasemapLabels.parse(BasemapLabels.FileName(f.value), _))
              .map(got ++ _)
          }
        }
        read.map { all =>
          val kept = all.filter(BasemapLabels.within(_, a.bbox))
          note(BasemapFetch.Note(s"${kept.size} of ${all.size} names are in the region; writing"))
          done(store.makeDirectory())
          val (tiles, bytes) = BasemapLabels.write(
            store,
            key(Basemap.Layer.Labels),
            kept,
            a.bbox,
            PmTiles.Zoom(lc.maxZoom.value),
            BasemapService.labelsMetadata(
              lc,
              a.bbox,
              BasemapService.NameCount(kept.size),
              BasemapFetch.FetchedAt(Instant.now(clock).toString)
            )
          )
          val _ = reader(Basemap.Layer.Labels)
          s"labels (Natural Earth): ${kept.size} names in ${tiles.value} tiles, ${human(bytes.value)} at ${store.where(key(Basemap.Layer.Labels)).value}"
        }

object BasemapService:
  type BuildName = BuildName.T
  object BuildName extends Newtype[String, Pure]
  type JsonText = JsonText.T
  object JsonText extends Newtype[String, Pure]
  type NameCount = NameCount.T
  object NameCount extends Newtype[Int, Pure]
  type TileKeys = TileKeys.T
  object TileKeys extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def invalid(problems: Problems): Api.Error = Api.Error(problems, Some(Api.Error.Code(Write.CodeInvalid)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def notFound(problem: Problem): Api.Error = Api.Error(problem, Some(Api.Error.Code(Write.CodeNotFound)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def human(n: Long): String = Backup.humanBytes(com.histmersion.api.ByteCount(n))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bboxText(b: BBox): String =
    s"${b.minLon.value},${b.minLat.value},${b.maxLon.value},${b.maxLat.value}"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bboxText(h: PmTiles.Header): String =
    s"${h.minLon.value},${h.minLat.value},${h.maxLon.value},${h.maxLat.value}"

  /** The header of a PNG archive of zooms 0…`maxZoom` over `b`: the writer fills in the offsets. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pngHeader(maxZoom: Int, b: BBox): PmTiles.Header =
    PmTiles.Header(
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      ByteOffset.applyUnsafe(0L),
      ByteCount.zero,
      PmTiles.Header.AddressedTiles(0L),
      PmTiles.Header.TileEntries(0L),
      PmTiles.Header.TileContents(0L),
      PmTiles.Header.Clustered.Clustered,
      PmTiles.Compression.Gzip,
      PmTiles.Compression.None,
      PmTiles.TileType.Png,
      PmTiles.Zoom(0),
      PmTiles.Zoom(maxZoom),
      PmTiles.Header.MinLon(b.minLon.value),
      PmTiles.Header.MinLat(b.minLat.value),
      PmTiles.Header.MaxLon(b.maxLon.value),
      PmTiles.Header.MaxLat(b.maxLat.value),
      PmTiles.Zoom(math.min(maxZoom, 5)),
      PmTiles.Header.CenterLon((b.minLon.value + b.maxLon.value) / 2),
      PmTiles.Header.CenterLat((b.minLat.value + b.maxLat.value) / 2)
    )

  /** The sea floor's credit line: the extract's own (GEBCO or ETOPO1), and ETOPO1's as well when the sea outside the
    * extract is read from it on demand and the extract is another's.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seaCredit(
      extract: Option[BasemapFetch.Attribution],
      etopo1: BasemapFetch.Attribution,
      onDemand: Boolean
  ): BasemapFetch.Attribution =
    extract.filter(_.value.trim.nonEmpty) match
      case Some(e) if e != etopo1 && onDemand => BasemapFetch.Attribution(s"${e.value}; outside it, ${etopo1.value}")
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
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusOf(
      focus: Option[Basemap.Focus.Name],
      bbox: Option[BBox],
      onlyTerrain: Boolean,
      terrainZoom: Option[BasemapFetch.Plan.MaxZoom],
      t: BasemapTerrainConfig
  ): Either[Problem, Option[(Basemap.Focus.Name, BBox)]] =
    val named = t.focusRegions.flatMap(r => Basemap.focusRegion(Basemap.Focus.Spec(r.value)).toOption)
    focus.map(f => Basemap.Focus.Name(f.value.trim)).filter(_.value.nonEmpty) match
      case Some(f) =>
        Basemap.validFocusName(f).flatMap { n =>
          bbox
            .orElse(named.find(_._1 == n).map(_._2))
            .map(b => Some((n, b)))
            .toRight(
              Problem.Invalid.Broken.here(
                Problem.Invalid.Rule.FocusRegion(Problem.Raw.of(n), named.map(r => Problem.Raw.of(r._1)).toVector)
              )
            )
        }
      case None =>
        Right(
          bbox
            .filter(_ => onlyTerrain && terrainZoom.exists(_.value > t.maxZoom.value))
            .map(b => (Basemap.focusNameOf(b), b))
        )

  /** What a fetch would add: the physical names to an extract that has the map but not them. Pure. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hints(vector: Boolean, labels: Boolean, narrow: Boolean = false): List[Basemap.Status.Hints] =
    (Option
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
        .toList).map(Basemap.Status.Hints(_))

  /** What to run when some styles lack layers the download left out. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def lackingHint(states: List[Basemap.StyleState]): List[Basemap.Status.Hints] =
    val lacking = states.filter(_.missing.nonEmpty)
    Option
      .when(lacking.nonEmpty) {
        val layers = lacking.flatMap(_.missing).distinct.sorted
        s"${names(lacking.map(_.name.value))} ${if lacking.size == 1 then "needs" else "need"} ${names(layers.map(_.value))}, which this download " +
          s"left out (basemap.vector.drop): `${Basemap.KeepAllCommand}` stores the vector layer whole (about 1.5 GB at zoom 10; " +
          "`--dry-run` first says how much), or take those layers out of basemap.vector.drop and run `" +
          Basemap.FetchCommand + " --layer vector`."
      }
      .toList
      .map(Basemap.Status.Hints(_))

  /** "a", "a and b", "a, b and c". Pure. */
  private def names(xs: List[String]): String = xs match
    case Nil        => ""
    case one :: Nil => one
    case _          => xs.init.mkString(", ") + " and " + xs.last

  /** The names archive's metadata: its layers, credit, and where and when it came from. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def labelsMetadata(
      lc: BasemapLabelsConfig,
      b: BBox,
      names: NameCount,
      fetchedAt: BasemapFetch.FetchedAt
  ): Json =
    val build = "/(v\\d+\\.\\d+(\\.\\d+)?)/".r
      .findFirstMatchIn(lc.url.value)
      .map(m => s"Natural Earth ${m.group(1)}")
      .getOrElse("Natural Earth")
    Json.obj(
      "name" -> Json.fromString("histmersion physical names"),
      "vector_layers" -> Json
        .arr(Json.obj("id" -> Json.fromString("physical")), Json.obj("id" -> Json.fromString("rivers"))),
      "attribution" -> Json.fromString(lc.attribution.value),
      "histmersion" -> Json.obj(
        "layer" -> Json.fromString(Basemap.Layer.Labels.wire.value),
        "source" -> Json.fromString(lc.url.value),
        "build" -> Json.fromString(build),
        "bbox" -> Json.fromString(bboxText(b)),
        "maxZoom" -> Json.fromInt(lc.maxZoom.value),
        "names" -> Json.fromInt(names.value),
        "fetchedAt" -> Json.fromString(fetchedAt.value)
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val JobKind: Job.Kind =
    Job.Kind.applyUnsafe(BasemapJobKinds.BasemapFetch.kind.value)

  /** Where the focus regions' relief archives are, in the base map's store. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val FocusDir: Store.Key = Store.Key.applyUnsafe("terrain-focus")

  /** Whether tile `z/x/y` lies in an archive's bounds (any part of it). Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def inside(h: PmTiles.Header, z: PmTiles.Zoom, x: PmTiles.TileColumn, y: PmTiles.TileRow): Boolean =
    val b = BBox(
      h.minLon,
      h.minLat,
      h.maxLon,
      h.maxLat
    )
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, Basemap.Zoom(z.value))
    x.value >= x0.value && x.value <= x1.value && y.value >= y0.value && y.value <= y1.value

  /** Two regions the same to within rounding. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def near(a: BBox, b: BBox): Boolean =
    List(
      a.minLon.value - b.minLon.value,
      a.minLat.value - b.minLat.value,
      a.maxLon.value - b.maxLon.value,
      a.maxLat.value - b.maxLat.value
    ).forall(d => math.abs(d) < 1e-6)

  /** The provider's builds, oldest first: `[{key, size}, …]` → `(key, size)`. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def builds(json: JsonText): Either[Problem, List[(BuildName, Long)]] =
    RawJson
      .parse(json.value)
      .left
      .map(e =>
        Problem.Io.Corrupt(
          Problem.Io.Format.Json,
          Problem.Where("the list of builds"),
          Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
        )
      )
      .flatMap { j =>
        j.asArray
          .toRight(
            Problem.Io.Corrupt(
              Problem.Io.Format.Json,
              Problem.Where("the list of builds"),
              Problem.Io.Damage.Structure(Problem.Io.Flaw.NotArray)
            )
          )
          .map(
            _.toList
              .flatMap { b =>
                b.cursor
                  .get[String]("key")
                  .toOption
                  .filter(_.endsWith(".pmtiles"))
                  .map(k => (BuildName(k), b.cursor.get[Long]("size").getOrElse(-1L)))
              }
              .sortBy(_._1.value)
          )
      }

  /** A stored vector tile cut to `rules` (layer → geometries and kinds; none = all) and, given `props`, to those
    * properties; gzip-compressed; empty when nothing is left. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def subset(
      stored: PmTiles.TileBytes,
      c: PmTiles.Compression,
      rules: Map[Basemap.Layer.Wire, Basemap.Read],
      props: Option[Set[Mvt.Rule.Props]] = None
  ): Either[Problem, PmTiles.TileBytes] =
    val raw =
      if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then
        PmTiles.gunzip(PmTiles.CompressedBytes(stored.value))
      else Right(PmTiles.PlainBytes(stored.value))
    raw
      .flatMap(
        Mvt.filter(
          _,
          rules.map((l, r) =>
            Mvt.LayerName(l.value) -> Mvt.Rule(
              keep = r.kinds.map(k => Mvt.Rule.Keep(k.value)),
              geoms = r.geoms.map(g => Mvt.Rule.Geoms(g.value)),
              props = props
            )
          )
        )
      )
      .map(t => PmTiles.TileBytes(if t.value.isEmpty then t.value else PmTiles.gzip(t).value))

  /** The drop list as the MVT filter's rules: a layer dropped whole, only some kinds, or all but some. Pure. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mvtRules(rules: List[Basemap.DropRule]): Map[Mvt.LayerName, Mvt.Rule] =
    rules
      .map(r =>
        Mvt.LayerName(r.layer.value) -> (if r.whole.whole then Mvt.Rule(none = Mvt.Rule.Empty.Empty)
                                         else
                                           Mvt.Rule(
                                             keep = r.spare.map(k => Mvt.Rule.Keep(k.value)),
                                             drop = r.drop.map(d => Mvt.Rule.Drop(d.value))
                                           ))
      )
      .toMap

  /** A vector tile without the dropped layers and kinds (every other layer kept as it is): stored again gzip-compressed
    * (as the source stores it), or dropped when nothing is left. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def thin(
      stored: PmTiles.TileBytes,
      c: PmTiles.Compression,
      rules: Map[Mvt.LayerName, Mvt.Rule]
  ): Either[Problem, Option[PmTiles.TileBytes]] =
    val raw =
      if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then
        PmTiles.gunzip(PmTiles.CompressedBytes(stored.value))
      else Right(PmTiles.PlainBytes(stored.value))
    raw.flatMap(Mvt.filter(_, rules, others = Mvt.Others.Others)).map { t =>
      Option.when(t.value.nonEmpty)(PmTiles.TileBytes(if c == PmTiles.Compression.Gzip then PmTiles.gzip(t).value
      else t.value))
    }

  /** One line for a planned layer: requests and bytes. Pure. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(l: Basemap.Layer, p: BasemapFetch.Plan, dryRun: Boolean): String =
    val verb = if dryRun then "would download" else "downloaded"
    s"${l.wire} (${p.source.label.value}, zoom ${p.header.minZoom.value}–${p.maxZoom.value}): ${p.tiles.size} tiles, $verb " +
      s"${human(p.requestBytes.value)} in ${p.requests.size} requests (+${p.directoryRequests.value} for the directories)" +
      // a download ahead says what each zoom costs, so a smaller --max-zoom can be chosen (#3760)
      (if dryRun then s"; by zoom — ${BasemapFetch.perZoomLine(BasemapFetch.perZoom(p)).value}" else "")

  /** Close a replaced archive a little later, after the requests reading it are done. */
  private def closeLater(f: PmTiles.File): Unit =
    val t = Thread.ofVirtual().unstarted { () => Thread.sleep(30000); f.close() }
    t.start()
