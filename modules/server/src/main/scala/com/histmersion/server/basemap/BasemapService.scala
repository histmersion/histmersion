package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.server.{BackgroundJob, Deps}

import sttp.model.StatusCode

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.util.Using

/** The terrain base map on this machine (docs/research-terrain-basemap.md): where its two extracts
  * are, the fetch job that downloads them, their removal, and the tiles served to the web page.
  *
  * The extracts are a CACHE — fetched again at will — so they live in the XDG cache directory
  * (`basemap.dir` moves them), never in the data directory with the corpus. `http` and `freeSpace`
  * are the seams a test substitutes for the network and the disk. */
final class BasemapService(deps: Deps, http: () => RangeHttp, freeSpace: Path => Long = BasemapService.usable):

  private def cfg = deps.cfg.basemap
  private val slot = BackgroundJob.Slot()

  /** The open archives, by layer, with the file stamp they were opened at. */
  private val open = ConcurrentHashMap[BasemapLayer, (String, PmTilesFile)]()

  def dir: Path = BasemapService.dir(deps.cfg)

  def path(l: BasemapLayer): Path = dir.resolve(l.file)

  /** The unfinished download of a layer: its range requests, kept until it is assembled. */
  def work(l: BasemapLayer): Path = dir.resolve(s".${l.wire}.part")

  private def attribution(l: BasemapLayer): String = l match
    case BasemapLayer.Vector  => cfg.vector.attribution
    case BasemapLayer.Terrain => cfg.terrain.attribution
    case BasemapLayer.Labels  => cfg.labels.attribution

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  /** A stamp of the file as it is now (size, modification time): it changes with each fetch. */
  private def stamp(p: Path): Option[String] =
    if !Files.isRegularFile(p) then None
    else Some(s"${Files.size(p).toHexString}${Files.getLastModifiedTime(p).toMillis.toHexString}")

  /** The layer's archive, opened once and again only when the file changed. */
  def reader(l: BasemapLayer): Either[String, Option[PmTilesFile]] =
    val p = path(l)
    stamp(p) match
      case None =>
        Option(open.remove(l)).foreach(_._2.close())
        Right(None)
      case Some(s) =>
        Option(open.get(l)) match
          case Some((at, f)) if at == s => Right(Some(f))
          case _ =>
            synchronized {
              Option(open.get(l)) match
                case Some((at, f)) if at == s => Right(Some(f))
                case old =>
                  PmTilesFile.open(p).map { f =>
                    open.put(l, (s, f))
                    // an archive replaced while a request reads it: the old channel stays readable
                    // on macOS and Linux until closed; requests that hold it finish first
                    old.foreach(o => BasemapService.closeLater(o._2))
                    Some(f)
                  }
            }

  def info(l: BasemapLayer): BasemapLayerInfo =
    val p    = path(l)
    val base = BasemapLayerInfo(l.wire, present = false, file = p.toString, attribution = attribution(l))
    reader(l) match
      case Left(e)     => base.copy(present = Files.isRegularFile(p), bytes = scala.util.Try(Files.size(p)).getOrElse(0L), error = Some(e))
      case Right(None) => base
      case Right(Some(f)) =>
        val h    = f.header
        val mine = f.metadata.hcursor.downField("histmersion")
        base.copy(present = true, bytes = f.size, minZoom = Some(h.minZoom), maxZoom = Some(h.maxZoom),
          bbox = Some(s"${h.minLon},${h.minLat},${h.maxLon},${h.maxLat}"), tiles = Some(h.addressedTiles),
          tileType = Some(h.tileType.mime),
          source = mine.get[String]("build").toOption.orElse(mine.get[String]("source").toOption),
          fetchedAt = mine.get[String]("fetchedAt").toOption, version = stamp(p),
          kept = Option.when(l == BasemapLayer.Vector)(Basemap.keptLine(mine.get[Boolean]("keepAll").getOrElse(false),
            mine.get[String]("drop").toOption)))

  def status: BasemapStatus =
    val layers = BasemapLayer.values.toList.map(info)
    def ok(l: BasemapLayer) = layers.exists(i => i.layer == l.wire && i.present && i.error.isEmpty)
    BasemapStatus(
      available = ok(BasemapLayer.Vector),
      dir = dir.toString, bbox = cfg.bbox, layers = layers, fetchCommand = Basemap.FetchCommand,
      freeBytes = freeSpace(BasemapService.existing(dir)), running = slot.current,
      hints = BasemapService.hints(ok(BasemapLayer.Vector), ok(BasemapLayer.Labels),
        layers.exists(i => i.layer == BasemapLayer.Vector.wire && i.present && i.error.isEmpty && i.kept.exists(_.contains("before the drop list")))))

  /** Subset tiles cut lately, by (file stamp, layer, tile, subset): a pan asks for the same few again. */
  private val cut = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[String, Array[Byte]](256, 0.75f, true):
    override def removeEldestEntry(e: java.util.Map.Entry[String, Array[Byte]]): Boolean = size() > 512)

  /** One tile with its content type and encoding: as stored, or — a vector tile with `subset` — only
    * the layers and geometries asked for; 204 where the extract (or the subset) has none. */
  def tile(layer: String, z: Int, x: Long, y: Long, subset: Option[String] = None): Either[ApiError, (Array[Byte], String, Option[String], String, StatusCode)] =
    val cache = s"public, max-age=${cfg.tileCacheSeconds}"
    val sub   = subset.map(_.trim).filter(_.nonEmpty)
    for
      rules <- sub.fold(Right(None))(s => Basemap.parseSubset(s).map(Some(_))).left.map(e => ApiError(s"layers: $e", Some(Write.CodeInvalid)))
      l     <- BasemapLayer.parse(layer).toRight(ApiError(s"no base-map layer '$layer' (vector, terrain or labels)", Some(Write.CodeNotFound)))
      f     <- reader(l).left.map(e => ApiError(e)).flatMap(_.toRight(
                 ApiError(s"the ${l.title} layer is not downloaded — run `${Basemap.FetchCommand}`", Some(Write.CodeNotFound))))
      found <- f.tile(z, x, y).left.map(ApiError(_))
      mime   = f.header.tileType.mime
      out   <- (found, rules.filter(_ => f.header.tileType == PmTiles.TileType.Mvt)) match
                 case (None, _) => Right((Array.emptyByteArray, mime, None, cache, StatusCode.NoContent))
                 case (Some(b), None) =>
                   val enc = Option.when(f.header.tileCompression == PmTiles.Compression.Gzip && PmTiles.isGzip(b))("gzip")
                   Right((b, mime, enc, cache, StatusCode.Ok))
                 case (Some(b), Some(rs)) =>
                   val key = s"$layer|${stamp(path(l)).getOrElse("")}|$z/$x/$y|${sub.getOrElse("")}"
                   Option(cut.get(key)).map(Right(_))
                     .getOrElse(BasemapService.subset(b, f.header.tileCompression, rs).left.map(ApiError(_)).map { t => cut.put(key, t); t })
                     .map(t => if t.isEmpty then (t, mime, None, cache, StatusCode.NoContent) else (t, mime, Some("gzip"), cache, StatusCode.Ok))
    yield out

  // ── removing ───────────────────────────────────────────────────────────────────────────────────

  def remove(layer: String): Either[ApiError, BasemapRemoved] =
    BasemapLayer.parseMany(layer).left.map(e => ApiError(e, Some(Write.CodeInvalid))).flatMap { ls =>
      if slot.isRunning then Left(BackgroundJob.busy("a base-map fetch", slot.current, Some(BasemapApi.Busy)))
      else
        val gone = ls.flatMap { l =>
          Option(open.remove(l)).foreach(_._2.close())
          val f     = path(l)
          val bytes = if Files.isRegularFile(f) then Files.size(f) else 0L
          val file  = Option.when(Files.deleteIfExists(f))((f.toString, bytes))
          val parts = BasemapService.deleteTree(work(l)).map(b => (work(l).toString, b))
          file.toList ++ parts.toList
        }
        Right(BasemapRemoved(gone.map(_._1), gone.map(_._2).sum))
    }

  // ── fetching ───────────────────────────────────────────────────────────────────────────────────

  /** What the next fetch of these options will do, decided before the job starts: refused here when
    * an option is out of range, so the person learns it at once. */
  private final case class Ask(layers: List[BasemapLayer], bbox: BBox, vectorZoom: Int, terrainZoom: Int, dryRun: Boolean,
      keepAll: Boolean)

  private def ask(q: BasemapFetchQuery): Either[String, Ask] =
    def zoom(z: Int, what: String) =
      Either.cond(z >= 0 && z <= Basemap.MaxZoomCap, z, s"$what $z is outside 0–${Basemap.MaxZoomCap}")
    for
      ls <- BasemapLayer.parseMany(q.layer)
      b  <- Basemap.validBBox(q.bbox.map(_.trim).filter(_.nonEmpty).getOrElse(cfg.bbox))
      vz <- zoom(q.maxZoom.getOrElse(cfg.vector.maxZoom), "--max-zoom")
      tz <- zoom(q.terrainMaxZoom.getOrElse(cfg.terrain.maxZoom), "--terrain-max-zoom")
      _  <- Basemap.parseDrop(cfg.vector.drop)
    yield Ask(ls, b, vz, tz, q.dryRun, q.keepAll)

  def fetch(q: BasemapFetchQuery): Either[ApiError, JobInfo] =
    deps.maintenance.guard("basemap/fetch"):
      ask(q).left.map(e => ApiError(e, Some(Write.CodeInvalid))).flatMap { a =>
        val what = s"${if a.dryRun then "dry run: " else ""}terrain base map — ${a.layers.map(_.wire).mkString(" + ")}, " +
          s"bbox ${q.bbox.getOrElse(cfg.bbox)}, zoom ≤ ${a.vectorZoom} (vector) / ${a.terrainZoom} (terrain)"
        BackgroundJob.start(deps.ds, BasemapService.JobKind, Some(JobDetail(about = Some(what))), slot, "a base-map fetch",
          Some(BasemapApi.Busy), deps.pins)((_, note) => BackgroundJob.Outcome.of(run(a, note)))
      }

  /** The job: each layer in turn — planned, checked against the free disk, downloaded, assembled. */
  private def run(a: Ask, note: String => Unit): Either[String, String] =
    a.layers.foldLeft[Either[String, List[String]]](Right(Nil)) { (acc, l) =>
      acc.flatMap(done => one(a, l, s => note(s"${l.wire}: $s")).map(done :+ _))
    }.map(_.mkString("; "))

  private def pace = BasemapFetch.Pace(cfg.requestGapMs.toLong, cfg.maxRequestMb.toLong * 1024 * 1024, cfg.overfetch)

  private def minFree: Long = (cfg.minFreeGb * 1e9).toLong

  /** The source of a layer: the vector build named by the config ("latest": the newest in the
    * provider's list — unless an unfinished download of an earlier one is there to finish), or the
    * terrain URL. */
  private def source(l: BasemapLayer, a: Ask, extra: String): Either[String, BasemapFetch.Source] = l match
    case BasemapLayer.Terrain => Right(BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url))
    case BasemapLayer.Labels  => Left("the physical names are not read with range requests") // `labels` fetches them
    case BasemapLayer.Vector =>
      val pinned = cfg.vector.build.trim
      resumable(l, a, extra) match
        case Some(src) if pinned.equalsIgnoreCase("latest") || src.label == pinned => Right(src)
        case _ =>
          val builds = http().text(cfg.vector.buildsUrl).flatMap(BasemapService.builds)
          val chosen =
            if pinned.equalsIgnoreCase("latest") || pinned.isEmpty then builds.flatMap(_.lastOption.toRight(s"${cfg.vector.buildsUrl} lists no build"))
            else builds.map(_.find(_._1 == pinned).getOrElse((pinned, -1L))).orElse(Right((pinned, -1L)))
          chosen.map((key, size) => BasemapFetch.Source(cfg.vector.url.replace("{build}", key), key, Option.when(size > 0)(size)))

  /** The source an unfinished download of the same region and depth was reading. */
  private def resumable(l: BasemapLayer, a: Ask, extra: String): Option[BasemapFetch.Source] =
    val f = work(l).resolve("state.json")
    Option.when(Files.isRegularFile(f))(Files.readString(f)).flatMap(s => io.circe.parser.parse(s).toOption).flatMap { j =>
      val c = j.hcursor
      val same = c.get[Int]("maxZoom").toOption.contains(zoomOf(l, a)) && c.get[String]("extra").toOption.contains(extra) &&
        c.get[String]("bbox").toOption.flatMap(BBox.parse).exists(b => BasemapService.near(b, a.bbox))
      for
        url   <- c.get[String]("url").toOption if same
        label <- c.get[String]("label").toOption.orElse(Some(url))
        size   = c.get[Long]("size").toOption
      yield BasemapFetch.Source(url, label, size)
    }

  private def zoomOf(l: BasemapLayer, a: Ask): Int = l match
    case BasemapLayer.Vector  => a.vectorZoom
    case BasemapLayer.Terrain => a.terrainZoom
    case BasemapLayer.Labels  => cfg.labels.maxZoom

  private def one(a: Ask, l: BasemapLayer, note: String => Unit): Either[String, String] =
    if l == BasemapLayer.Labels then labels(a, note) else ranged(a, l, note)

  /** A PMTiles layer (vector, terrain): planned, checked against the free disk, downloaded, assembled. */
  private def ranged(a: Ask, l: BasemapLayer, note: String => Unit): Either[String, String] =
    val rules = if a.keepAll then Nil else Basemap.parseDrop(cfg.vector.drop).toOption.getOrElse(Nil)
    val extra = if l != BasemapLayer.Vector then "" else if a.keepAll then "keep-all" else s"drop: ${cfg.vector.drop}"
    val h     = http()
    for
      src  <- source(l, a, extra)
      _     = note(s"reading ${src.label}")
      plan <- BasemapFetch.plan(h, src, a.bbox, zoomOf(l, a), pace, note)
      out  <- if a.dryRun then Right(BasemapService.describe(l, plan, dryRun = true))
              else download(l, plan, rules, extra, h, note, a.keepAll)
    yield out

  private def download(l: BasemapLayer, plan: BasemapFetch.Plan, rules: List[Basemap.DropRule], extra: String,
      h: RangeHttp, note: String => Unit, keepAll: Boolean): Either[String, String] =
    Files.createDirectories(dir)
    val work  = this.work(l)
    val state = BasemapFetch.stateOf(plan, extra).deepMerge(io.circe.Json.obj("label" -> io.circe.Json.fromString(plan.source.label)))
    val had   = BasemapFetch.prepare(work, state)
    val pendingBytes = BasemapFetch.pending(plan, work).map(_._2).sum
    // the local archive is at most the distinct contents the requests carry (the vector one is far
    // smaller once thinned); it is written beside the downloaded parts before they are removed
    val outBytes = plan.contentBytes
    def room(next: Long): Either[String, Unit] =
      val free = freeSpace(dir)
      val left = free - next - outBytes
      Either.cond(left >= minFree, (),
        s"refused: the ${l.title} layer needs ${Backup.humanBytes(pendingBytes + outBytes)} more on ${dir} " +
          s"(${Backup.humanBytes(free)} free), which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb). " +
          "Free some space, fetch a smaller region (--bbox) or fewer zooms (--max-zoom / --terrain-max-zoom), " +
          "or move the files with basemap.dir.")
    for
      _ <- room(pendingBytes)
      _  = if had > 0 then note(s"resuming: ${had} of ${plan.requests.size} parts are here already")
      _ <- BasemapFetch.download(h, plan, work, pace, note, room)
      rulesOf = BasemapService.mvtRules(rules)
      transform = l match
        case BasemapLayer.Vector if !keepAll => (b: Array[Byte]) => BasemapService.thin(b, plan.header.tileCompression, rulesOf)
        case _ => (b: Array[Byte]) => Right(Some(b))
      meta = BasemapFetch.localMetadata(plan, rules.filter(_.whole).map(_.layer).toSet, attribution(l),
        Instant.now().toString, l, Option.when(l == BasemapLayer.Vector)((keepAll, cfg.vector.drop)))
      done <- BasemapFetch.assemble(plan, work, path(l), transform, BasemapFetch.localHeader(plan), meta, note)
    yield
      val _ = BasemapService.deleteTree(work)
      val _ = reader(l)
      s"${BasemapService.describe(l, plan, dryRun = false)}; stored ${done._2} tiles in ${Backup.humanBytes(done._1)} at ${path(l)}"

  /** The physical names: Natural Earth's label files read whole (a few MB each), cut to the region and
    * written as `labels.pmtiles` (about 1 MB) — no range requests, nothing to resume. */
  private def labels(a: Ask, note: String => Unit): Either[String, String] =
    val lc    = cfg.labels
    val files = lc.files.map(f => f -> lc.url.replace("{file}", f))
    if a.dryRun then
      Right(s"labels (Natural Earth, zoom 0–${lc.maxZoom}): would download ${files.size} files — ${lc.files.mkString(", ")} — " +
        "about 20 MB, of which about 1 MB is kept")
    else
      val free = freeSpace(BasemapService.existing(dir))
      val need = 64L * 1024 * 1024
      if free - need < minFree then
        Left(s"refused: the physical names need about ${Backup.humanBytes(need)} on $dir (${Backup.humanBytes(free)} free), " +
          s"which would leave less than ${Backup.humanBytes(minFree)} (basemap.min-free-gb)")
      else
        val h = http()
        val read = files.foldLeft[Either[String, List[BasemapLabels.Label]]](Right(Nil)) { case (acc, (f, url)) =>
          acc.flatMap { got =>
            note(s"reading $f")
            if got.nonEmpty then pace.sleep(pace.gapMs)
            h.text(url).flatMap(t => io.circe.parser.parse(t).left.map(e => s"$url is not JSON: ${e.getMessage}"))
              .flatMap(BasemapLabels.parse(f, _)).map(got ++ _)
          }
        }
        read.map { all =>
          val kept  = all.filter(BasemapLabels.within(_, a.bbox))
          note(s"${kept.size} of ${all.size} names are in the region; writing")
          Files.createDirectories(dir)
          val (tiles, bytes) = BasemapLabels.write(path(BasemapLayer.Labels), kept, a.bbox, lc.maxZoom,
            BasemapService.labelsMetadata(lc, a.bbox, kept.size, Instant.now().toString))
          val _ = reader(BasemapLayer.Labels)
          s"labels (Natural Earth): ${kept.size} names in $tiles tiles, ${Backup.humanBytes(bytes)} at ${path(BasemapLayer.Labels)}"
        }

object BasemapService:

  /** What a fetch would add: the physical names to an extract that has the map but not them. Pure. */
  def hints(vector: Boolean, labels: Boolean, narrow: Boolean = false): List[String] =
    Option.when(narrow)(s"The vector layer was fetched before the base map kept land use and natural points: a style " +
      s"can draw only coast, water and land cover from it. One `${Basemap.FetchCommand} --layer vector` stores the broader " +
      "extract (or `" + Basemap.FetchCommand + "` for everything); restyling never needs a download after that.").toList ++
    Option.when(vector && !labels)(s"The physical names (mountain ranges, deserts, peninsulas, lakes, major rivers, peaks) " +
      s"are not downloaded: `${Basemap.FetchCommand} --layer labels` adds them (about 20 MB read, about 1 MB kept).").toList

  /** The names archive's metadata: its layers, credit, and where and when it came from. Pure. */
  def labelsMetadata(lc: BasemapLabelsConfig, b: BBox, names: Int, fetchedAt: String): io.circe.Json =
    import io.circe.Json
    val build = "/(v\\d+\\.\\d+(\\.\\d+)?)/".r.findFirstMatchIn(lc.url).map(m => s"Natural Earth ${m.group(1)}").getOrElse("Natural Earth")
    Json.obj(
      "name" -> Json.fromString("histmersion physical names"),
      "vector_layers" -> Json.arr(Json.obj("id" -> Json.fromString("physical")), Json.obj("id" -> Json.fromString("rivers"))),
      "attribution" -> Json.fromString(lc.attribution),
      "histmersion" -> Json.obj(
        "layer" -> Json.fromString(BasemapLayer.Labels.wire),
        "source" -> Json.fromString(lc.url),
        "build" -> Json.fromString(build),
        "bbox" -> Json.fromString(s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"),
        "maxZoom" -> Json.fromInt(lc.maxZoom),
        "names" -> Json.fromInt(names),
        "fetchedAt" -> Json.fromString(fetchedAt)))

  val JobKind = "basemap-fetch"

  /** Where the extracts live: `basemap.dir`, else `$XDG_CACHE_HOME/histmersion/basemap` (default
    * `~/.cache/histmersion/basemap`), else under the data directory. */
  def dir(cfg: AppConfig, env: String => Option[String] = sys.env.get): Path =
    Option(cfg.basemap.dir).map(_.trim).filter(_.nonEmpty)
      .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home"))))
      .orElse(UserPaths.cacheDir(env).map(_.resolve("basemap")))
      .getOrElse(Path.of(cfg.init.dataDir).resolve("basemap"))
      .toAbsolutePath.normalize

  /** Free space for this process on the volume of `p`. */
  def usable(p: Path): Long = scala.util.Try(Files.getFileStore(existing(p)).getUsableSpace).getOrElse(0L)

  /** `p`, or the nearest of its parents that exists (the directory may not be made yet). */
  def existing(p: Path): Path =
    Iterator.iterate(p.toAbsolutePath)(_.getParent).takeWhile(_ != null).find(Files.exists(_)).getOrElse(p)

  /** Two regions the same to within rounding. Pure. */
  def near(a: BBox, b: BBox): Boolean =
    List(a.minLon - b.minLon, a.minLat - b.minLat, a.maxLon - b.maxLon, a.maxLat - b.maxLat).forall(d => math.abs(d) < 1e-6)

  /** The provider's builds, oldest first: `[{key, size}, …]` → `(key, size)`. Pure. */
  def builds(json: String): Either[String, List[(String, Long)]] =
    io.circe.parser.parse(json).left.map(e => s"the list of builds is not JSON: ${e.getMessage}").flatMap { j =>
      j.asArray.toRight("the list of builds is not a JSON array").map(_.toList.flatMap { b =>
        b.hcursor.get[String]("key").toOption.filter(_.endsWith(".pmtiles")).map(k => (k, b.hcursor.get[Long]("size").getOrElse(-1L)))
      }.sortBy(_._1))
    }

  /** A stored vector tile cut to `rules` (layer → geometries; none = all), gzip-compressed; empty when
    * nothing is left. Pure. */
  def subset(stored: Array[Byte], c: PmTiles.Compression, rules: Map[String, Set[Int]]): Either[String, Array[Byte]] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw.flatMap(Mvt.filter(_, rules.map((l, g) => l -> Mvt.Rule(geoms = g)))).map(t => if t.isEmpty then t else PmTiles.gzip(t))

  /** The drop list as the MVT filter's rules: a layer dropped whole, only some kinds, or all but some. Pure. */
  def mvtRules(rules: List[Basemap.DropRule]): Map[String, Mvt.Rule] =
    rules.map(r => r.layer -> (if r.whole then Mvt.Rule(none = true) else Mvt.Rule(keep = r.spare, drop = r.drop))).toMap

  /** A vector tile without the dropped layers and kinds (every other layer kept as it is): stored
    * again gzip-compressed (as the source stores it), or dropped when nothing is left. Pure. */
  def thin(stored: Array[Byte], c: PmTiles.Compression, rules: Map[String, Mvt.Rule]): Either[String, Option[Array[Byte]]] =
    val raw = if c == PmTiles.Compression.Gzip || PmTiles.isGzip(stored) then PmTiles.gunzip(stored) else Right(stored)
    raw.flatMap(Mvt.filter(_, rules, others = true)).map { t =>
      Option.when(t.nonEmpty)(if c == PmTiles.Compression.Gzip then PmTiles.gzip(t) else t)
    }

  /** One line for a planned layer: requests and bytes. Pure. */
  def describe(l: BasemapLayer, p: BasemapFetch.Plan, dryRun: Boolean): String =
    val verb = if dryRun then "would download" else "downloaded"
    s"${l.wire} (${p.source.label}, zoom ${p.header.minZoom}–${p.maxZoom}): ${p.tiles.size} tiles, $verb " +
      s"${Backup.humanBytes(p.requestBytes)} in ${p.requests.size} requests (+${p.directoryRequests} for the directories)"

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
    val t = Thread.ofVirtual().unstarted(() => { Thread.sleep(30000); f.close() })
    t.start()
