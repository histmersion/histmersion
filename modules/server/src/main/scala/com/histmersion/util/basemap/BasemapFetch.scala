package com.histmersion
package util.basemap
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.JsonObject
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{External, Problem, Raised}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.storage.{RandomReader, Store}
import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.util.storage.ByteSource

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.nowarn
import scala.collection.mutable
import scala.util.Using
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Plural

/** A regional extract of a remote PMTiles archive (a Protomaps build, Mapterhorn's planet), read with HTTP range
  * requests — the way `pmtiles extract` reads it, and what both providers document for a partial download — and written
  * as a local archive ([[PmTilesWriter]]).
  *
  *   1. [[plan]]: the header, the metadata and only the directories that hold the region's tiles; the tiles' byte
  *      ranges merged into few, large requests ([[coalesce]]).
  *   2. [[download]]: each request into its own file in the work store, skipped when it is there already — so an
  *      interrupted fetch RESUMES. Every answer must carry the planned `Content-Range` and the planned file's size and
  *      `ETag`: a build replaced mid-download is refused, not mixed.
  *   3. [[assemble]]: the tiles in id order, each passed through `transform` (the vector layer drops everything but
  *      coast, water and land cover, [[Mvt.filter]]), into the local archive.
  *
  * Nothing here knows the configuration or the job; [[BasemapService]] drives it.
  */
object BasemapFetch:
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
  type ChunkCount = ChunkCount.T
  object ChunkCount extends Newtype[Int, Pure]
  export Basemap.LayerInfo.Dropped
  type FetchedAt = FetchedAt.T
  object FetchedAt extends Newtype[String, Pure]
  type FilterSpec = FilterSpec.T
  object FilterSpec extends Newtype[String, Pure]
  type Filtered = Filtered.T
  object Filtered extends Newtype[Boolean, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type TileCount = TileCount.T
  object TileCount extends Newtype[Long, Pure]
  type ZoomSummary = ZoomSummary.T
  object ZoomSummary extends Newtype[String, Pure]

  /** What a fetch of one layer reads: `url`, the build it names (for the record), and the size the provider publishes
    * for it, when it does.
    */
  final case class Source(url: Source.Url, label: Source.Label, expectedSize: Option[Source.ExpectedSize] = None)

  object Source:
    export RangeHttp.Url
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type ExpectedSize = ExpectedSize.T
    object ExpectedSize extends Newtype[Long, Pure]

  /** One stored tile of the remote archive that the region needs. */
  final case class Wanted(id: Wanted.Id, offset: Wanted.Offset, length: Wanted.Length)

  object Wanted:
    export PmTiles.Entry.TileId as Id
    type Offset = ByteOffset
    val Offset: ByteOffset.type = ByteOffset
    export PmTiles.Entry.Length

  final case class Plan(
      source: Source,
      header: PmTiles.Header,
      metadata: Json,
      remote: RemoteFile,
      /** Sorted by tile id. */
      tiles: Vector[Wanted],
      /** The merged range requests, as (offset, length) in the remote tile data section, by offset. */
      requests: Vector[(ByteOffset, ByteCount)],
      /** The distinct contents' bytes (what the requests carry, without the gaps they read over). */
      contentBytes: ByteCount,
      /** Requests the plan itself made (header, metadata, directories). */
      directoryRequests: Plan.DirectoryRequests,
      bbox: BBox,
      maxZoom: Plan.MaxZoom,
      /** The shallowest zoom planned (a focus region starts below the extract's depth). */
      minZoom: Plan.MinZoom = Plan.MinZoom(0)
  ):
    def requestBytes: ByteCount = ByteCount.applyUnsafe(requests.map(_._2.value).sum)

  object Plan:
    type DirectoryRequests = DirectoryRequests.T
    object DirectoryRequests extends Newtype[Int, Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends Newtype[Int, Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends Newtype[Int, Pure]

  /** The politeness of the requests: the pause between two, the largest, and how much of a gap is read through rather
    * than split.
    */
  final case class Pace(
      gapMs: Pace.GapMs,
      maxRequestBytes: ByteCount,
      overfetch: Pace.Overfetch,
      sleep: Pace.GapMs => Unit = ms => Thread.sleep(ms.value)
  )

  object Pace:
    type GapMs = GapMs.T
    object GapMs extends Newtype[Long, Pure]
    type Overfetch = Overfetch.T
    object Overfetch extends Newtype[Double, Pure]

  // ── pure ───────────────────────────────────────────────────────────────────────────────────────

  /** What a plan downloads at each zoom: its tiles and their distinct bytes, shallowest first — a download ahead's size
    * estimate per zoom (#3760). Pure.
    */
  def perZoom(p: Plan): List[(PmTiles.Zoom, TileCount, ByteCount)] =
    p.tiles.groupBy(t => PmTiles.zxy(t.id)._1).toList.sortBy[Int](_._1.value).map { (z, ts) =>
      (
        z,
        TileCount(ts.size.toLong),
        ByteCount.applyUnsafe(ts.map(t => (t.offset, t.length)).distinct.map(_._2.value.toLong).sum)
      )
    }

  /** [[perZoom]] in a line: `zoom 0: 1 tile, 12 KB · …`, the deepest zooms' running total last. Pure. */
  def perZoomLine(rows: List[(PmTiles.Zoom, TileCount, ByteCount)]): ZoomSummary =
    ZoomSummary(
      rows
        .map((z, n, b) =>
          s"zoom ${z.value}: ${Plural.count(Plural.CountN(n.value), Plural.CountSingular("tile"))}, ${Backup.humanBytes(com.histmersion.api.ByteCount(b.value))}"
        )
        .mkString(" · ")
    )

  /** Every tile id of zooms `minZoom`…`maxZoom` over `b`, sorted. Pure. */
  def wantedIds(b: BBox, minZoom: Plan.MinZoom, maxZoom: Plan.MaxZoom): Array[Long] =
    val out = mutable.ArrayBuilder.make[Long]
    for z <- minZoom.value to maxZoom.value do
      val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, Basemap.Zoom(z))
      var x: Long = x0.value // nested loops over one zoom's rectangle
      while x <= x1.value do
        var y: Long = y0.value
        while y <= y1.value do
          out += PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y)).value
          y += 1
        x += 1
    val a = out.result()
    java.util.Arrays.sort(a)
    a

  /** Distinct `(offset, length)` contents, sorted by offset, merged into requests: the next content joins the current
    * request when the gap before it is at most `overfetch` of the request so far (or 64 KiB) and the request stays
    * within `maxBytes`. Pure.
    */
  def coalesce(
      contents: Seq[(ByteOffset, ByteCount)],
      maxBytes: ByteCount,
      overfetch: Pace.Overfetch
  ): Vector[(ByteOffset, ByteCount)] =
    val out = Vector.newBuilder[(Long, Long)]
    var start = -1L // the request being built: [start, end)
    var end = -1L
    contents.foreach { (offset, length) =>
      val o = offset.value
      val l = length.value
      val gap = o - end
      if start >= 0 && gap >= 0 && gap <= math.max(
          64L * 1024,
          (overfetch.value * (end - start)).toLong
        ) && o + l - start <= maxBytes.value
      then end = math.max(end, o + l)
      else
        if start >= 0 then out += ((start, end - start))
        start = o
        end = o + l
    }
    if start >= 0 then out += ((start, end - start))
    out.result().map((o, l) => (ByteOffset.applyUnsafe(o), ByteCount.applyUnsafe(l)))

  /** The extract's bounds: the region clipped to the source's own. Pure. */
  def clip(b: BBox, h: PmTiles.Header): BBox =
    BBox(
      Longitude(b.minLon.value.max(h.minLon.value)),
      Latitude(b.minLat.value.max(h.minLat.value)),
      Longitude(b.maxLon.value.min(h.maxLon.value)),
      Latitude(b.maxLat.value.min(h.maxLat.value))
    )

  // ── 1. the plan ────────────────────────────────────────────────────────────────────────────────

  def plan(
      http: RangeHttp,
      source: Source,
      bbox: BBox,
      maxZoom: Plan.MaxZoom,
      pace: Pace,
      note: Note => Unit,
      minZoom: Option[Plan.MinZoom] = None
  ): Either[Problem, Plan] =
    var requests = 0 // directory requests made, each after a pause
    def get(offset: ByteOffset, length: ByteCount): Either[Problem, (RangeHttp.ResponseBytes, RemoteFile)] =
      if requests > 0 then pace.sleep(pace.gapMs)
      if Thread.interrupted() then throw InterruptedException()
      requests += 1
      http.bytes(source.url, offset, length)
    for
      first <- get(ByteOffset.applyUnsafe(0L), ByteCount.applyUnsafe(PmTiles.RootBytes.toLong))
      (head, remote) = first
      _ <- source.expectedSize
        .filter(s => s.value != remote.size.value)
        .map(s =>
          Left(
            Problem.Upstream.Mislisted(
              Problem.Upstream.Url.of(source.url),
              Problem.Count.of(remote.size),
              Problem.Count.of(s),
              Problem.Raw.of(source.label)
            )
          )
        )
        .getOrElse(Right(()))
      h <- PmTiles.parseHeader(PmTiles.HeaderBytes(head.value))
      _ <- Either.cond(
        h.rootOffset.value + h.rootLength.value <= head.value.length,
        (),
        Problem.Io.Corrupt(
          Problem.Io.Format.PmTiles,
          Problem.Where("the root directory"),
          Problem.Io.Damage.Structure(Problem.Io.Flaw.RootOutside)
        )
      )
      root <- PmTiles
        .decompress(
          PmTiles.CompressedBytes(
            head.value.slice(h.rootOffset.value.toInt, (h.rootOffset.value + h.rootLength.value).toInt)
          ),
          h.internalCompression
        )
        .flatMap(PmTiles.decodeDirectory)
      meta <-
        if h.metadataLength.value <= 0 then Right(Json.obj())
        else
          get(h.metadataOffset, h.metadataLength)
            .flatMap((b, _) => PmTiles.decompress(PmTiles.CompressedBytes(b.value), h.internalCompression))
            .flatMap(b =>
              RawJson
                .parse(String(b.value, "UTF-8"))
                .left
                .map(e =>
                  Problem.Io.Corrupt(
                    Problem.Io.Format.Json,
                    Problem.Where("the PMTiles metadata"),
                    Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
                  )
                )
            )
      region = clip(bbox, h)
      top = math.min(maxZoom.value, h.maxZoom.value)
      low = minZoom.fold(h.minZoom.value)(_.value.max(h.minZoom.value)).min(top)
      ids = wantedIds(region, Plan.MinZoom(low), Plan.MaxZoom(top))
      _ = note(Note(s"reading the directories of ${ids.length} tiles (zoom $low–$top)"))
      tiles <- resolve(root, ids, h, pace, get, 0)
    yield
      val sorted = tiles.sortBy[Long](_.id.value)
      val contents = sorted
        .map(t => (t.offset, ByteCount.applyUnsafe(t.length.value.toLong)))
        .distinct
        .sortBy[Long](_._1.value)
      Plan(
        source,
        h,
        meta,
        remote,
        sorted,
        coalesce(contents, pace.maxRequestBytes, pace.overfetch),
        ByteCount.applyUnsafe(contents.map(_._2.value).sum),
        Plan.DirectoryRequests(requests),
        region,
        Plan.MaxZoom(top),
        Plan.MinZoom(low)
      )

  /** The stored tiles of `ids` (sorted) under one directory: its runs, and the leaf directories it points to — fetched
    * in merged requests, as the tiles are — searched in turn.
    */
  private def resolve(
      dir: Vector[PmTiles.Entry],
      ids: Array[Long],
      h: PmTiles.Header,
      pace: Pace,
      get: (ByteOffset, ByteCount) => Either[Problem, (RangeHttp.ResponseBytes, RemoteFile)],
      depth: Int
  ): Either[Problem, Vector[Wanted]] =
    val found = Vector.newBuilder[Wanted]
    val leaves = mutable.LinkedHashMap.empty[PmTiles.Entry, mutable.ArrayBuilder[Long]]
    ids.foreach { id =>
      PmTiles.find(dir, PmTiles.Entry.TileId(id)).foreach { e =>
        if e.isLeaf then leaves.getOrElseUpdate(e, mutable.ArrayBuilder.make[Long]) += id
        else found += Wanted(PmTiles.Entry.TileId(id), e.offset, e.length)
      }
    }
    if leaves.isEmpty then Right(found.result())
    else if depth >= 3 then
      Left(
        Problem.Io.Corrupt(
          Problem.Io.Format.PmTiles,
          Problem.Where("the leaf directories"),
          Problem.Io.Damage.Structure(Problem.Io.Flaw.TooDeep)
        )
      )
    else
      val byOffset = leaves.keys.toVector.sortBy[Long](_.offset.value)
      val ranges = coalesce(
        byOffset.map(e => (e.offset, ByteCount.applyUnsafe(e.length.value.toLong))),
        ByteCount.applyUnsafe(8L * 1024 * 1024),
        pace.overfetch
      )
      ranges.foldLeft[Either[Problem, Vector[Wanted]]](Right(found.result())) { (acc, range) =>
        acc.flatMap { sofar =>
          val (start, len) = range
          get(ByteOffset.applyUnsafe(h.leafOffset.value + start.value), len).flatMap { (bytes, _) =>
            byOffset
              .filter(e => e.offset >= start && e.offset.value + e.length.value <= start.value + len.value)
              .foldLeft[Either[Problem, Vector[Wanted]]](Right(sofar)) { (acc2, e) =>
                acc2.flatMap { got =>
                  val from = (e.offset.value - start.value).toInt
                  val slice = bytes.value.slice(from, from + e.length.value)
                  PmTiles
                    .decompress(PmTiles.CompressedBytes(slice), h.internalCompression)
                    .flatMap(PmTiles.decodeDirectory)
                    .flatMap(sub => resolve(sub, leaves(e).result(), h, pace, get, depth + 1))
                    .map(got ++ _)
                }
              }
          }
        }
      }

  // ── 2. the download ────────────────────────────────────────────────────────────────────────────

  /** The file request `r` is kept in, in the work store. */
  def chunkKey(r: (ByteOffset, ByteCount)): Store.Key = Store.Key.applyUnsafe(s"${r._1}-${r._2}.bin")

  /** The requests not downloaded yet. */
  def pending(p: Plan, work: Store): Vector[(ByteOffset, ByteCount)] =
    p.requests.filterNot(r => work.stat(chunkKey(r)).exists(s => s.isFile && s.size == r._2))

  /** Download every pending request into `work`; `room(bytes)` is asked before each one and stops the fetch (with its
    * reason) when the disk would get too full.
    */
  def download(
      http: RangeHttp,
      p: Plan,
      work: Store,
      pace: Pace,
      note: Note => Unit,
      room: ByteCount => Either[Problem, Unit]
  ): Either[Problem, ChunkCount] =
    val todo = pending(p, work)
    val total = p.requestBytes.value
    var done = total - todo.map(_._2.value).sum // bytes on disk, for the progress line
    todo.zipWithIndex
      .foldLeft[Either[Problem, Int]](Right(0)) { case (acc, (r, i)) =>
        acc.flatMap { n =>
          room(r._2).flatMap { _ =>
            if i > 0 || p.directoryRequests.value > 0 then pace.sleep(pace.gapMs)
            if Thread.interrupted() then throw InterruptedException()
            work
              .reserve(chunkKey(r))
              .left
              .map(_.message)
              .flatMap(reserved =>
                Using.resource(reserved) { part =>
                  http
                    .download(p.source.url, ByteOffset.applyUnsafe(p.header.dataOffset.value + r._1.value), r._2, part)
                    .flatMap { got =>
                      if got.size != p.remote.size || got.etag != p.remote.etag then
                        part.discard()
                        Left(Problem.Upstream.Rebuilt(Problem.Upstream.Url.of(p.source.url)))
                      else
                        part.commit().left.map(e => e.message).map { _ =>
                          done += r._2.value
                          note(
                            Note(
                              s"downloaded ${Backup.humanBytes(com.histmersion.api.ByteCount(done))} of ${Backup.humanBytes(com.histmersion.api.ByteCount(total))} (${p.requests.size - todo.size + i + 1}/${p.requests.size} requests)"
                            )
                          )
                          n + 1
                        }
                    }
                }
              )
          }
        }
      }
      .map(ChunkCount(_))

  // ── 3. the local archive ───────────────────────────────────────────────────────────────────────

  /** Write the planned tiles from the downloaded requests into the archive `key` of `out`: each distinct content passed
    * through `transform` once (`None` drops the tile), in tile-id order. Answers the size and the number of tiles
    * written.
    */
  def assemble(
      p: Plan,
      work: Store,
      out: Store,
      key: Store.Key,
      transform: PmTiles.TileBytes => Either[Problem, Option[PmTiles.TileBytes]],
      header: PmTiles.Header,
      metadata: Json,
      note: Note => Unit
  ): Either[Problem, (ByteCount, TileCount)] =
    val starts = p.requests.map[Long](_._1.value).toArray
    val readers = mutable.HashMap.empty[Int, RandomReader]
    def read(offset: ByteOffset, length: Wanted.Length): PmTiles.TileBytes =
      val i = { val k = java.util.Arrays.binarySearch(starts, offset.value); if k >= 0 then k else -k - 2 }
      val r = p.requests(i)
      val reader = readers.getOrElseUpdate(
        i,
        work
          .get(chunkKey(r))
          .getOrElse(
            throw Raised(Problem.Io.Wrong(Problem.Io.Path.of(work.where(chunkKey(r))), Problem.Io.Kind.Present))
          )
          .reader()
      )
      PmTiles.TileBytes(
        reader.read(ByteOffset.applyUnsafe(offset.value - r._1.value), PmTiles.ByteLength(length.value)).value
      )
    val writer = PmTilesWriter(out, key)
    // the transformed bytes of a content seen before (ocean tiles repeat thousands of times); only
    // small ones are kept, a large one is simply read and transformed again
    val small = mutable.HashMap.empty[Long, Option[PmTiles.TileBytes]]
    try
      val result = p.tiles.zipWithIndex.foldLeft[Either[Problem, Long]](Right(0L)) { case (acc, (t, i)) =>
        acc.flatMap { n =>
          if i % 20000 == 0 then
            if Thread.interrupted() then throw InterruptedException()
            note(Note(s"writing ${p.tiles.size} tiles: ${i * 100 / p.tiles.size.max(1)} %"))
          val bytes: Either[Problem, Option[PmTiles.TileBytes]] = small.get(t.offset.value) match
            case Some(kept) => Right(kept)
            case None       =>
              val r = transform(read(t.offset, t.length))
              r.foreach(b => if t.length.value <= 8192 then small(t.offset.value) = b)
              r
          bytes.map {
            case Some(b) => writer.add(t.id, b); n + 1
            case None    => n
          }
        }
      }
      result match
        case Left(e) =>
          writer.abort()
          Left(e)
        case Right(n) =>
          Right((writer.finish(header, metadata), TileCount(n)))
    catch
      // aborts the partial archive and rethrows: every Throwable, so no failure leaves a half-written file
      case e: (Throwable @nowarn("id=E235")) =>
        writer.abort()
        throw e
    finally readers.values.foreach(_.close())

  /** The local archive's header: the source's tile type and compression, the extract's zooms and region. The writer
    * fills in the offsets.
    */
  def localHeader(p: Plan): PmTiles.Header =
    val b = p.bbox
    p.header
      .modify(_.minZoom)
      .setTo(PmTiles.Zoom(p.minZoom.value))
      .modify(_.maxZoom)
      .setTo(PmTiles.Zoom(p.maxZoom.value))
      .modify(_.minLon)
      .setTo(PmTiles.Header.MinLon(b.minLon.value))
      .modify(_.minLat)
      .setTo(PmTiles.Header.MinLat(b.minLat.value))
      .modify(_.maxLon)
      .setTo(PmTiles.Header.MaxLon(b.maxLon.value))
      .modify(_.maxLat)
      .setTo(PmTiles.Header.MaxLat(b.maxLat.value))
      .modify(_.centerZoom)
      .setTo(PmTiles.Zoom(math.max(p.minZoom.value, math.min(p.maxZoom.value, 5))))
      .modify(_.centerLon)
      .setTo(PmTiles.Header.CenterLon((b.minLon.value + b.maxLon.value) / 2))
      .modify(_.centerLat)
      .setTo(PmTiles.Header.CenterLat((b.minLat.value + b.maxLat.value) / 2))

  /** The local archive's metadata: the source's, its `vector_layers` without the `dropped` ones, its attribution
    * replaced by `attribution`, and where and when it came from — and, for the vector layer, what it kept (`filter`:
    * `keepAll` or the `drop` list) — under `histmersion`.
    */
  def localMetadata(
      p: Plan,
      dropped: Set[Dropped],
      attribution: Attribution,
      fetchedAt: FetchedAt,
      layer: Basemap.Layer,
      filter: Option[(Filtered, FilterSpec)] = None
  ): Json =
    val src = p.metadata.asObject.getOrElse(JsonObject.empty)
    val layers = src("vector_layers").flatMap(_.asArray) match
      case Some(ls) if dropped.nonEmpty =>
        src.updated(
          "vector_layers",
          Json.fromValues(
            ls.filterNot(l => l.cursor.get[String]("id").toOption.exists(id => dropped.exists(_.value == id)))
          )
        )
      case _ => src
    val b = p.bbox
    Json.fromJsonObject(
      layers
        .updated("attribution", Json.fromString(attribution.value))
        .updated(
          "histmersion",
          Json
            .obj(
              "layer" -> Json.fromString(layer.wire.value),
              "source" -> Json.fromString(p.source.url.value),
              "build" -> Json.fromString(p.source.label.value),
              "etag" -> p.remote.etag.fold(Json.Null)(e => Json.fromString(e.value)),
              "bbox" -> Json.fromString(
                s"${b.minLon.value},${b.minLat.value},${b.maxLon.value},${b.maxLat.value}"
              ),
              "maxZoom" -> Json.fromInt(p.maxZoom.value),
              "fetchedAt" -> Json.fromString(fetchedAt.value)
            )
            .deepMerge(
              filter.fold(Json.obj())((all, drop) =>
                Json.obj(
                  "keepAll" -> Json.fromBoolean(all.value),
                  "drop" -> Json.fromString(if all.value then "" else drop.value)
                )
              )
            )
        )
    )

  /** The resume record of a work directory: a fetch continues only the download it describes. */
  def stateOf(p: Plan, extra: Note): Json = Json.obj(
    "url" -> Json.fromString(p.source.url.value),
    "size" -> Json.fromLong(p.remote.size.value),
    "etag" -> p.remote.etag.fold(Json.Null)(e => Json.fromString(e.value)),
    "bbox" -> Json.fromString(
      s"${p.bbox.minLon.value},${p.bbox.minLat.value},${p.bbox.maxLon.value},${p.bbox.maxLat.value}"
    ),
    "maxZoom" -> Json.fromInt(p.maxZoom.value),
    "extra" -> Json.fromString(extra.value)
  )

  /** Make `work` hold only the parts of THIS plan: a work store left by another download (another build, region or
    * depth) is emptied first. Answers how many parts are already there.
    */
  def prepare(work: Store, state: Json): ChunkCount =
    val file = Store.Key.applyUnsafe("state.json")
    if work.isDirectory(Store.Key.Root) && !work
        .get(file)
        .flatMap(f => RawJson.parse(f.text().value).toOption)
        .contains(state)
    then work.list().foreach(f => work.delete(f.key))
    work.makeDirectory().left.foreach(e => throw Raised(e.message))
    work
      .write(file, ByteSource.Content(state.noSpaces.getBytes(UTF_8)))
      .left
      .foreach(e => throw Raised(e.message))
    ChunkCount(work.list().count(_.key.name.value.endsWith(".bin")))
