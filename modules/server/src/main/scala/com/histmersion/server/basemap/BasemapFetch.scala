package com.histmersion.server.basemap

import com.histmersion.core.*

import io.circe.Json
import io.circe.syntax.*

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.collection.mutable
import scala.util.Using

/** A regional extract of a remote PMTiles archive (a Protomaps build, Mapterhorn's planet), read with
  * HTTP range requests — the way `pmtiles extract` reads it, and what both providers document for a
  * partial download — and written as a local archive ([[PmTilesWriter]]).
  *
  *  1. [[plan]]: the header, the metadata and only the directories that hold the region's tiles;
  *     the tiles' byte ranges merged into few, large requests ([[coalesce]]).
  *  2. [[download]]: each request into its own file in the work directory, skipped when it is there
  *     already — so an interrupted fetch RESUMES. Every answer must carry the planned `Content-Range`
  *     and the planned file's size and `ETag`: a build replaced mid-download is refused, not mixed.
  *  3. [[assemble]]: the tiles in id order, each passed through `transform` (the vector layer drops
  *     everything but coast, water and land cover, [[Mvt.filter]]), into the local archive.
  *
  * Nothing here knows the configuration or the job; [[BasemapService]] drives it. */
object BasemapFetch:

  /** What a fetch of one layer reads: `url`, the build it names (for the record), and the size the
    * provider publishes for it, when it does. */
  final case class Source(url: String, label: String, expectedSize: Option[Long] = None)

  /** One stored tile of the remote archive that the region needs. */
  final case class Wanted(id: Long, offset: Long, length: Int)

  final case class Plan(
      source: Source,
      header: PmTiles.Header,
      metadata: Json,
      remote: RemoteFile,
      /** Sorted by tile id. */
      tiles: Vector[Wanted],
      /** The merged range requests, as (offset, length) in the remote tile data section, by offset. */
      requests: Vector[(Long, Long)],
      /** The distinct contents' bytes (what the requests carry, without the gaps they read over). */
      contentBytes: Long,
      /** Requests the plan itself made (header, metadata, directories). */
      directoryRequests: Int,
      bbox: BBox,
      maxZoom: Int):
    def requestBytes: Long = requests.map(_._2).sum

  /** The politeness of the requests: the pause between two, the largest, and how much of a gap is
    * read through rather than split. */
  final case class Pace(gapMs: Long, maxRequestBytes: Long, overfetch: Double, sleep: Long => Unit = ms => Thread.sleep(ms))

  // ── pure ───────────────────────────────────────────────────────────────────────────────────────

  /** Every tile id of zooms `minZoom`…`maxZoom` over `b`, sorted. Pure. */
  def wantedIds(b: BBox, minZoom: Int, maxZoom: Int): Array[Long] =
    val out = mutable.ArrayBuilder.make[Long]
    for z <- minZoom to maxZoom do
      val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
      var x = x0 // nested loops over one zoom's rectangle
      while x <= x1 do
        var y = y0
        while y <= y1 do
          out += PmTiles.tileId(z, x, y)
          y += 1
        x += 1
    val a = out.result()
    java.util.Arrays.sort(a)
    a

  /** Distinct `(offset, length)` contents, sorted by offset, merged into requests: the next content
    * joins the current request when the gap before it is at most `overfetch` of the request so far
    * (or 64 KiB) and the request stays within `maxBytes`. Pure. */
  def coalesce(contents: Seq[(Long, Long)], maxBytes: Long, overfetch: Double): Vector[(Long, Long)] =
    val out = Vector.newBuilder[(Long, Long)]
    var start = -1L // the request being built: [start, end)
    var end   = -1L
    contents.foreach { (o, l) =>
      val gap = o - end
      if start >= 0 && gap >= 0 && gap <= math.max(64L * 1024, (overfetch * (end - start)).toLong) && o + l - start <= maxBytes then
        end = math.max(end, o + l)
      else
        if start >= 0 then out += ((start, end - start))
        start = o
        end = o + l
    }
    if start >= 0 then out += ((start, end - start))
    out.result()

  /** The extract's bounds: the region clipped to the source's own. Pure. */
  def clip(b: BBox, h: PmTiles.Header): BBox =
    BBox(b.minLon.max(h.minLon), b.minLat.max(h.minLat), b.maxLon.min(h.maxLon), b.maxLat.min(h.maxLat))

  // ── 1. the plan ────────────────────────────────────────────────────────────────────────────────

  def plan(http: RangeHttp, source: Source, bbox: BBox, maxZoom: Int, pace: Pace, note: String => Unit): Either[String, Plan] =
    var requests = 0 // directory requests made, each after a pause
    def get(offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)] =
      if requests > 0 then pace.sleep(pace.gapMs)
      if Thread.interrupted() then throw InterruptedException()
      requests += 1
      http.bytes(source.url, offset, length)
    for
      first <- get(0, PmTiles.RootBytes)
      (head, remote) = first
      _ <- source.expectedSize.filter(_ != remote.size)
             .map(s => Left(s"${source.url} is ${remote.size} bytes, but the provider lists $s for ${source.label}")).getOrElse(Right(()))
      h <- PmTiles.parseHeader(head)
      _ <- Either.cond(h.rootOffset + h.rootLength <= head.length, (), "root directory outside the first 16 KiB")
      root <- PmTiles.decompress(head.slice(h.rootOffset.toInt, (h.rootOffset + h.rootLength).toInt), h.internalCompression)
                .flatMap(PmTiles.decodeDirectory)
      meta <- if h.metadataLength <= 0 then Right(Json.obj())
              else get(h.metadataOffset, h.metadataLength).flatMap((b, _) => PmTiles.decompress(b, h.internalCompression))
                .flatMap(b => io.circe.parser.parse(String(b, "UTF-8")).left.map(e => s"metadata is not JSON: ${e.getMessage}"))
      region = clip(bbox, h)
      top    = math.min(maxZoom, h.maxZoom)
      ids    = wantedIds(region, h.minZoom, top)
      _      = note(s"reading the directories of ${ids.length} tiles (zoom ${h.minZoom}–$top)")
      tiles <- resolve(root, ids, h, pace, get, 0)
    yield
      val sorted   = tiles.sortBy(_.id)
      val contents = sorted.map(t => (t.offset, t.length.toLong)).distinct.sortBy(_._1)
      Plan(source, h, meta, remote, sorted, coalesce(contents, pace.maxRequestBytes, pace.overfetch),
        contents.map(_._2).sum, requests, region, top)

  /** The stored tiles of `ids` (sorted) under one directory: its runs, and the leaf directories it
    * points to — fetched in merged requests, as the tiles are — searched in turn. */
  private def resolve(dir: Vector[PmTiles.Entry], ids: Array[Long], h: PmTiles.Header, pace: Pace,
      get: (Long, Long) => Either[String, (Array[Byte], RemoteFile)], depth: Int): Either[String, Vector[Wanted]] =
    val found  = Vector.newBuilder[Wanted]
    val leaves = mutable.LinkedHashMap.empty[PmTiles.Entry, mutable.ArrayBuilder[Long]]
    ids.foreach { id =>
      PmTiles.find(dir, id).foreach { e =>
        if e.isLeaf then leaves.getOrElseUpdate(e, mutable.ArrayBuilder.make[Long]) += id
        else found += Wanted(id, e.offset, e.length)
      }
    }
    if leaves.isEmpty then Right(found.result())
    else if depth >= 3 then Left("PMTiles directories nested too deep")
    else
      val byOffset = leaves.keys.toVector.sortBy(_.offset)
      val ranges   = coalesce(byOffset.map(e => (e.offset, e.length.toLong)), 8L * 1024 * 1024, pace.overfetch)
      ranges.foldLeft[Either[String, Vector[Wanted]]](Right(found.result())) { (acc, range) =>
        acc.flatMap { sofar =>
          val (start, len) = range
          get(h.leafOffset + start, len).flatMap { (bytes, _) =>
            byOffset.filter(e => e.offset >= start && e.offset + e.length <= start + len)
              .foldLeft[Either[String, Vector[Wanted]]](Right(sofar)) { (acc2, e) =>
                acc2.flatMap { got =>
                  val slice = bytes.slice((e.offset - start).toInt, (e.offset - start + e.length).toInt)
                  PmTiles.decompress(slice, h.internalCompression).flatMap(PmTiles.decodeDirectory)
                    .flatMap(sub => resolve(sub, leaves(e).result(), h, pace, get, depth + 1)).map(got ++ _)
                }
              }
          }
        }
      }

  // ── 2. the download ────────────────────────────────────────────────────────────────────────────

  /** The file request `r` is kept in. */
  def chunkFile(work: Path, r: (Long, Long)): Path = work.resolve(s"${r._1}-${r._2}.bin")

  /** The requests not downloaded yet. */
  def pending(p: Plan, work: Path): Vector[(Long, Long)] =
    p.requests.filterNot(r => Files.isRegularFile(chunkFile(work, r)) && Files.size(chunkFile(work, r)) == r._2)

  /** Download every pending request into `work`; `room(bytes)` is asked before each one and stops the
    * fetch (with its reason) when the disk would get too full. */
  def download(http: RangeHttp, p: Plan, work: Path, pace: Pace, note: String => Unit,
      room: Long => Either[String, Unit]): Either[String, Int] =
    val todo  = pending(p, work)
    val total = p.requestBytes
    var done  = total - todo.map(_._2).sum // bytes on disk, for the progress line
    todo.zipWithIndex.foldLeft[Either[String, Int]](Right(0)) { case (acc, (r, i)) =>
      acc.flatMap { n =>
        room(r._2).flatMap { _ =>
          if i > 0 || p.directoryRequests > 0 then pace.sleep(pace.gapMs)
          if Thread.interrupted() then throw InterruptedException()
          http.download(p.source.url, p.header.dataOffset + r._1, r._2, chunkFile(work, r)).flatMap { got =>
            if got.size != p.remote.size || got.etag != p.remote.etag then
              val _ = Files.deleteIfExists(chunkFile(work, r))
              Left(s"${p.source.url} changed during the download (size or ETag differ) — run the fetch again for the new build")
            else
              done += r._2
              note(s"downloaded ${Backup.humanBytes(done)} of ${Backup.humanBytes(total)} (${p.requests.size - todo.size + i + 1}/${p.requests.size} requests)")
              Right(n + 1)
          }
        }
      }
    }

  // ── 3. the local archive ───────────────────────────────────────────────────────────────────────

  /** Write the planned tiles from the downloaded requests into `out`: each distinct content passed
    * through `transform` once (`None` drops the tile), in tile-id order. Answers the size and the
    * number of tiles written. */
  def assemble(p: Plan, work: Path, out: Path, transform: Array[Byte] => Either[String, Option[Array[Byte]]],
      header: PmTiles.Header, metadata: Json, note: String => Unit): Either[String, (Long, Long)] =
    val starts   = p.requests.map(_._1).toArray
    val channels = mutable.HashMap.empty[Int, FileChannel]
    def read(offset: Long, length: Int): Array[Byte] =
      val i  = { val k = java.util.Arrays.binarySearch(starts, offset); if k >= 0 then k else -k - 2 }
      val r  = p.requests(i)
      val ch = channels.getOrElseUpdate(i, FileChannel.open(chunkFile(work, r), StandardOpenOption.READ))
      val buf = ByteBuffer.allocate(length)
      var at  = offset - r._1 // positional reads may return short
      while buf.hasRemaining do
        val n = ch.read(buf, at)
        if n < 0 then throw java.io.EOFException(s"${chunkFile(work, r)} is short")
        at += n
      buf.array()
    val writer = PmTilesWriter(out)
    // the transformed bytes of a content seen before (ocean tiles repeat thousands of times); only
    // small ones are kept, a large one is simply read and transformed again
    val small = mutable.HashMap.empty[Long, Option[Array[Byte]]]
    try
      val result = p.tiles.zipWithIndex.foldLeft[Either[String, Long]](Right(0L)) { case (acc, (t, i)) =>
        acc.flatMap { n =>
          if i % 20000 == 0 then
            if Thread.interrupted() then throw InterruptedException()
            note(s"writing ${p.tiles.size} tiles: ${i * 100 / p.tiles.size.max(1)} %")
          val bytes = small.get(t.offset).map(Right(_)).getOrElse {
            val r = transform(read(t.offset, t.length))
            r.foreach(b => if t.length <= 8192 then small(t.offset) = b)
            r
          }
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
          Right((writer.finish(header, metadata), n))
    catch
      case e: Throwable =>
        writer.abort()
        throw e
    finally channels.values.foreach(_.close())

  /** The local archive's header: the source's tile type and compression, the extract's zooms and
    * region. The writer fills in the offsets. */
  def localHeader(p: Plan): PmTiles.Header =
    val b = p.bbox
    p.header.copy(minZoom = p.header.minZoom, maxZoom = p.maxZoom,
      minLon = b.minLon, minLat = b.minLat, maxLon = b.maxLon, maxLat = b.maxLat,
      centerZoom = math.min(p.maxZoom, 5), centerLon = (b.minLon + b.maxLon) / 2, centerLat = (b.minLat + b.maxLat) / 2)

  /** The local archive's metadata: the source's, its `vector_layers` without the `dropped` ones, its
    * attribution replaced by `attribution`, and where and when it came from — and, for the vector
    * layer, what it kept (`filter`: `keepAll` or the `drop` list) — under `histmersion`. */
  def localMetadata(p: Plan, dropped: Set[String], attribution: String, fetchedAt: String, layer: BasemapLayer,
      filter: Option[(Boolean, String)] = None): Json =
    val src = p.metadata.asObject.getOrElse(io.circe.JsonObject.empty)
    val layers = src("vector_layers").flatMap(_.asArray) match
      case Some(ls) if dropped.nonEmpty =>
        src.add("vector_layers", Json.fromValues(ls.filterNot(l => l.hcursor.get[String]("id").toOption.exists(dropped))))
      case _ => src
    val b = p.bbox
    Json.fromJsonObject(layers.add("attribution", attribution.asJson).add("histmersion", Json.obj(
      "layer"     -> layer.wire.asJson,
      "source"    -> p.source.url.asJson,
      "build"     -> p.source.label.asJson,
      "etag"      -> p.remote.etag.asJson,
      "bbox"      -> s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}".asJson,
      "maxZoom"   -> p.maxZoom.asJson,
      "fetchedAt" -> fetchedAt.asJson).deepMerge(filter.fold(Json.obj())((all, drop) =>
        Json.obj("keepAll" -> all.asJson, "drop" -> (if all then "" else drop).asJson)))))

  /** The resume record of a work directory: a fetch continues only the download it describes. */
  def stateOf(p: Plan, extra: String): Json = Json.obj(
    "url" -> p.source.url.asJson, "size" -> p.remote.size.asJson, "etag" -> p.remote.etag.asJson,
    "bbox" -> s"${p.bbox.minLon},${p.bbox.minLat},${p.bbox.maxLon},${p.bbox.maxLat}".asJson,
    "maxZoom" -> p.maxZoom.asJson, "extra" -> extra.asJson)

  /** Make `work` hold only the parts of THIS plan: a work directory left by another download (another
    * build, region or depth) is emptied first. Answers how many parts are already there. */
  def prepare(work: Path, state: Json): Int =
    val file = work.resolve("state.json")
    if Files.isDirectory(work) && !(Files.isRegularFile(file) && io.circe.parser.parse(Files.readString(file)).toOption.contains(state)) then
      Using.resource(Files.list(work))(_.forEach(f => { val _ = Files.deleteIfExists(f) }))
    Files.createDirectories(work)
    Files.writeString(file, state.noSpaces)
    Using.resource(Files.list(work))(_.filter(_.getFileName.toString.endsWith(".bin")).count().toInt)
