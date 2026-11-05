package com.histmersion.util.basemap

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.storage.{RandomReader, Store}
import com.histmersion.ByteOffset
import io.circe.Json
import io.circe.syntax.*

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable
import scala.util.Using
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.library.Source

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

  /** What a fetch of one layer reads: `url`, the build it names (for the record), and the size the provider publishes
    * for it, when it does.
    */
  type PerZoomResultPart1 = PerZoomResultPart1.T
  object PerZoomResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomResultPart2 = PerZoomResultPart2.T
  object PerZoomResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomResultPart3 = PerZoomResultPart3.T
  object PerZoomResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomLineResult = PerZoomLineResult.T
  object PerZoomLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomLineRowsPart1 = PerZoomLineRowsPart1.T
  object PerZoomLineRowsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomLineRowsPart2 = PerZoomLineRowsPart2.T
  object PerZoomLineRowsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerZoomLineRowsPart3 = PerZoomLineRowsPart3.T
  object PerZoomLineRowsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WantedIdsMinZoom = WantedIdsMinZoom.T
  object WantedIdsMinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WantedIdsMaxZoom = WantedIdsMaxZoom.T
  object WantedIdsMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceResultPart1 = CoalesceResultPart1.T
  object CoalesceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceResultPart2 = CoalesceResultPart2.T
  object CoalesceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceContentsPart1 = CoalesceContentsPart1.T
  object CoalesceContentsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceContentsPart2 = CoalesceContentsPart2.T
  object CoalesceContentsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceMaxBytes = CoalesceMaxBytes.T
  object CoalesceMaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoalesceOverfetch = CoalesceOverfetch.T
  object CoalesceOverfetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanResultPart1 = PlanResultPart1.T
  object PlanResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanMaxZoom = PlanMaxZoom.T
  object PlanMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanNoteArg1 = PlanNoteArg1.T
  object PlanNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanMinZoom = PlanMinZoom.T
  object PlanMinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveGetArg1 = ResolveGetArg1.T
  object ResolveGetArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveGetArg2 = ResolveGetArg2.T
  object ResolveGetArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveGetResultPart1 = ResolveGetResultPart1.T
  object ResolveGetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveDepth = ResolveDepth.T
  object ResolveDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkKeyRPart1 = ChunkKeyRPart1.T
  object ChunkKeyRPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkKeyRPart2 = ChunkKeyRPart2.T
  object ChunkKeyRPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResultPart1 = PendingResultPart1.T
  object PendingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResultPart2 = PendingResultPart2.T
  object PendingResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart2 = DownloadResultPart2.T
  object DownloadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadNoteArg1 = DownloadNoteArg1.T
  object DownloadNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadRoomArg1 = DownloadRoomArg1.T
  object DownloadRoomArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadRoomResultPart1 = DownloadRoomResultPart1.T
  object DownloadRoomResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssembleResultPart1 = AssembleResultPart1.T
  object AssembleResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssembleResultPart2Part1 = AssembleResultPart2Part1.T
  object AssembleResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssembleResultPart2Part2 = AssembleResultPart2Part2.T
  object AssembleResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssembleTransformResultPart1 = AssembleTransformResultPart1.T
  object AssembleTransformResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssembleNoteArg1 = AssembleNoteArg1.T
  object AssembleNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalMetadataDropped = LocalMetadataDropped.T
  object LocalMetadataDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalMetadataAttribution = LocalMetadataAttribution.T
  object LocalMetadataAttribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalMetadataFetchedAt = LocalMetadataFetchedAt.T
  object LocalMetadataFetchedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalMetadataFilterPart1 = LocalMetadataFilterPart1.T
  object LocalMetadataFilterPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalMetadataFilterPart2 = LocalMetadataFilterPart2.T
  object LocalMetadataFilterPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateOfExtra = StateOfExtra.T
  object StateOfExtra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrepareResult = PrepareResult.T
  object PrepareResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Source(url: Source.Url, label: Source.Label, expectedSize: Option[Source.ExpectedSize] = None)

  object Source:
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExpectedSize = ExpectedSize.T
    object ExpectedSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One stored tile of the remote archive that the region needs. */
  final case class Wanted(id: Wanted.Id, offset: Wanted.Offset, length: Wanted.Length)

  object Wanted:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Length = Length.T
    object Length extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Plan(
      source: Source,
      header: PmTiles.Header,
      metadata: Json,
      remote: RemoteFile,
      /** Sorted by tile id. */
      tiles: Vector[Wanted],
      /** The merged range requests, as (offset, length) in the remote tile data section, by offset. */
      requests: Vector[(Plan.RequestsPart1, Plan.RequestsPart2)],
      /** The distinct contents' bytes (what the requests carry, without the gaps they read over). */
      contentBytes: Plan.ContentBytes,
      /** Requests the plan itself made (header, metadata, directories). */
      directoryRequests: Plan.DirectoryRequests,
      bbox: BBox,
      maxZoom: Plan.MaxZoom,
      /** The shallowest zoom planned (a focus region starts below the extract's depth). */
      minZoom: Plan.MinZoom = 0
  ):
    def requestBytes: com.histmersion.util.basemap.BasemapFetch.Plan.RequestBytesResult = requests.map(_._2).sum

  object Plan:
    type RequestBytesResult = RequestBytesResult.T
    object RequestBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type RequestsPart1 = RequestsPart1.T
    object RequestsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RequestsPart2 = RequestsPart2.T
    object RequestsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContentBytes = ContentBytes.T
    object ContentBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DirectoryRequests = DirectoryRequests.T
    object DirectoryRequests extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinZoom = MinZoom.T
    object MinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The politeness of the requests: the pause between two, the largest, and how much of a gap is read through rather
    * than split.
    */
  final case class Pace(
      gapMs: Pace.GapMs,
      maxRequestBytes: Pace.MaxRequestBytes,
      overfetch: Pace.Overfetch,
      sleep: Pace.SleepArg1 => Unit = ms => Thread.sleep(ms)
  )

  object Pace:
    type SleepArg1 = SleepArg1.T
    object SleepArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type GapMs = GapMs.T
    object GapMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxRequestBytes = MaxRequestBytes.T
    object MaxRequestBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Overfetch = Overfetch.T
    object Overfetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── pure ───────────────────────────────────────────────────────────────────────────────────────

  /** What a plan downloads at each zoom: its tiles and their distinct bytes, shallowest first — a download ahead's size
    * estimate per zoom (#3760). Pure.
    */
  def perZoom(p: Plan): List[(com.histmersion.util.basemap.BasemapFetch.PerZoomResultPart1, com.histmersion.util.basemap.BasemapFetch.PerZoomResultPart2, com.histmersion.util.basemap.BasemapFetch.PerZoomResultPart3)] =
    p.tiles.groupBy(t => PmTiles.zxy(t.id)._1).toList.sortBy(_._1).map { (z, ts) =>
      (z, ts.size, ts.map(t => (t.offset, t.length)).distinct.map(_._2.toLong).sum)
    }

  /** [[perZoom]] in a line: `zoom 0: 1 tile, 12 KB · …`, the deepest zooms' running total last. Pure. */
  def perZoomLine(rows: List[(com.histmersion.util.basemap.BasemapFetch.PerZoomLineRowsPart1, com.histmersion.util.basemap.BasemapFetch.PerZoomLineRowsPart2, com.histmersion.util.basemap.BasemapFetch.PerZoomLineRowsPart3)]): com.histmersion.util.basemap.BasemapFetch.PerZoomLineResult =
    rows
      .map((z, n, b) =>
        s"zoom $z: ${com.histmersion.api.Plural.count(n, "tile")}, ${com.histmersion.api.backup.Backup.humanBytes(b)}"
      )
      .mkString(" · ")

  /** Every tile id of zooms `minZoom`…`maxZoom` over `b`, sorted. Pure. */
  def wantedIds(b: BBox, minZoom: com.histmersion.util.basemap.BasemapFetch.WantedIdsMinZoom, maxZoom: com.histmersion.util.basemap.BasemapFetch.WantedIdsMaxZoom): Array[Long] =
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

  /** Distinct `(offset, length)` contents, sorted by offset, merged into requests: the next content joins the current
    * request when the gap before it is at most `overfetch` of the request so far (or 64 KiB) and the request stays
    * within `maxBytes`. Pure.
    */
  def coalesce(contents: Seq[(com.histmersion.util.basemap.BasemapFetch.CoalesceContentsPart1, com.histmersion.util.basemap.BasemapFetch.CoalesceContentsPart2)], maxBytes: com.histmersion.util.basemap.BasemapFetch.CoalesceMaxBytes, overfetch: com.histmersion.util.basemap.BasemapFetch.CoalesceOverfetch): Vector[(com.histmersion.util.basemap.BasemapFetch.CoalesceResultPart1, com.histmersion.util.basemap.BasemapFetch.CoalesceResultPart2)] =
    val out = Vector.newBuilder[(Long, Long)]
    var start = -1L // the request being built: [start, end)
    var end = -1L
    contents.foreach { (o, l) =>
      val gap = o - end
      if start >= 0 && gap >= 0 && gap <= math.max(
          64L * 1024,
          (overfetch * (end - start)).toLong
        ) && o + l - start <= maxBytes
      then end = math.max(end, o + l)
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

  def plan(
      http: RangeHttp,
      source: Source,
      bbox: BBox,
      maxZoom: com.histmersion.util.basemap.BasemapFetch.PlanMaxZoom,
      pace: Pace,
      note: com.histmersion.util.basemap.BasemapFetch.PlanNoteArg1 => Unit,
      minZoom: Option[com.histmersion.util.basemap.BasemapFetch.PlanMinZoom] = None
  ): Either[com.histmersion.util.basemap.BasemapFetch.PlanResultPart1, Plan] =
    var requests = 0 // directory requests made, each after a pause
    def get(offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)] =
      if requests > 0 then pace.sleep(pace.gapMs)
      if Thread.interrupted() then throw InterruptedException()
      requests += 1
      http.bytes(source.url, offset, length)
    for
      first <- get(0, PmTiles.RootBytes)
      (head, remote) = first
      _ <- source.expectedSize
        .filter(_ != remote.size)
        .map(s => Left(s"${source.url} is ${remote.size} bytes, but the provider lists $s for ${source.label}"))
        .getOrElse(Right(()))
      h <- PmTiles.parseHeader(head)
      _ <- Either.cond(h.rootOffset + h.rootLength <= head.length, (), "root directory outside the first 16 KiB")
      root <- PmTiles
        .decompress(head.slice(h.rootOffset.toInt, (h.rootOffset + h.rootLength).toInt), h.internalCompression)
        .flatMap(PmTiles.decodeDirectory)
      meta <-
        if h.metadataLength <= 0 then Right(Json.obj())
        else
          get(h.metadataOffset, h.metadataLength)
            .flatMap((b, _) => PmTiles.decompress(b, h.internalCompression))
            .flatMap(b =>
              io.circe.parser.parse(String(b, "UTF-8")).left.map(e => s"metadata is not JSON: ${e.getMessage}")
            )
      region = clip(bbox, h)
      top = math.min(maxZoom, h.maxZoom)
      low = minZoom.fold(h.minZoom)(_.max(h.minZoom)).min(top)
      ids = wantedIds(region, low, top)
      _ = note(s"reading the directories of ${ids.length} tiles (zoom $low–$top)")
      tiles <- resolve(root, ids, h, pace, get, 0)
    yield
      val sorted = tiles.sortBy(_.id)
      val contents = sorted.map(t => (t.offset, t.length.toLong)).distinct.sortBy(_._1)
      Plan(
        source,
        h,
        meta,
        remote,
        sorted,
        coalesce(contents, pace.maxRequestBytes, pace.overfetch),
        contents.map(_._2).sum,
        requests,
        region,
        top,
        low
      )

  /** The stored tiles of `ids` (sorted) under one directory: its runs, and the leaf directories it points to — fetched
    * in merged requests, as the tiles are — searched in turn.
    */
  private def resolve(
      dir: Vector[PmTiles.Entry],
      ids: Array[Long],
      h: PmTiles.Header,
      pace: Pace,
      get: (com.histmersion.util.basemap.BasemapFetch.ResolveGetArg1, com.histmersion.util.basemap.BasemapFetch.ResolveGetArg2) => Either[com.histmersion.util.basemap.BasemapFetch.ResolveGetResultPart1, (Array[Byte], RemoteFile)],
      depth: com.histmersion.util.basemap.BasemapFetch.ResolveDepth
  ): Either[com.histmersion.util.basemap.BasemapFetch.ResolveResultPart1, Vector[Wanted]] =
    val found = Vector.newBuilder[Wanted]
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
      val ranges = coalesce(byOffset.map(e => (e.offset, e.length.toLong)), 8L * 1024 * 1024, pace.overfetch)
      ranges.foldLeft[Either[String, Vector[Wanted]]](Right(found.result())) { (acc, range) =>
        acc.flatMap { sofar =>
          val (start, len) = range
          get(h.leafOffset + start, len).flatMap { (bytes, _) =>
            byOffset
              .filter(e => e.offset >= start && e.offset + e.length <= start + len)
              .foldLeft[Either[String, Vector[Wanted]]](Right(sofar)) { (acc2, e) =>
                acc2.flatMap { got =>
                  val slice = bytes.slice((e.offset - start).toInt, (e.offset - start + e.length).toInt)
                  PmTiles
                    .decompress(slice, h.internalCompression)
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
  def chunkKey(r: (com.histmersion.util.basemap.BasemapFetch.ChunkKeyRPart1, com.histmersion.util.basemap.BasemapFetch.ChunkKeyRPart2)): Store.Key = Store.Key.applyUnsafe(s"${r._1}-${r._2}.bin")

  /** The requests not downloaded yet. */
  def pending(p: Plan, work: Store): Vector[(com.histmersion.util.basemap.BasemapFetch.PendingResultPart1, com.histmersion.util.basemap.BasemapFetch.PendingResultPart2)] =
    p.requests.filterNot(r => work.stat(chunkKey(r)).exists(s => s.isFile && s.size.value == r._2))

  /** Download every pending request into `work`; `room(bytes)` is asked before each one and stops the fetch (with its
    * reason) when the disk would get too full.
    */
  def download(
      http: RangeHttp,
      p: Plan,
      work: Store,
      pace: Pace,
      note: com.histmersion.util.basemap.BasemapFetch.DownloadNoteArg1 => Unit,
      room: com.histmersion.util.basemap.BasemapFetch.DownloadRoomArg1 => Either[com.histmersion.util.basemap.BasemapFetch.DownloadRoomResultPart1, Unit]
  ): Either[com.histmersion.util.basemap.BasemapFetch.DownloadResultPart1, com.histmersion.util.basemap.BasemapFetch.DownloadResultPart2] =
    val todo = pending(p, work)
    val total = p.requestBytes
    var done = total - todo.map(_._2).sum // bytes on disk, for the progress line
    todo.zipWithIndex.foldLeft[Either[String, Int]](Right(0)) { case (acc, (r, i)) =>
      acc.flatMap { n =>
        room(r._2).flatMap { _ =>
          if i > 0 || p.directoryRequests > 0 then pace.sleep(pace.gapMs)
          if Thread.interrupted() then throw InterruptedException()
          Using.resource(work.reserve(chunkKey(r)).fold(e => throw IOException(e.message), identity)) { part =>
            http.download(p.source.url, p.header.dataOffset + r._1, r._2, part).flatMap { got =>
              if got.size != p.remote.size || got.etag != p.remote.etag then
                part.discard()
                Left(
                  s"${p.source.url} changed during the download (size or ETag differ) — run the fetch again for the new build"
                )
              else
                part.commit().left.map(_.message).map { _ =>
                  done += r._2
                  note(
                    s"downloaded ${Backup.humanBytes(done)} of ${Backup.humanBytes(total)} (${p.requests.size - todo.size + i + 1}/${p.requests.size} requests)"
                  )
                  n + 1
                }
            }
          }
        }
      }
    }

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
      transform: Array[Byte] => Either[com.histmersion.util.basemap.BasemapFetch.AssembleTransformResultPart1, Option[Array[Byte]]],
      header: PmTiles.Header,
      metadata: Json,
      note: com.histmersion.util.basemap.BasemapFetch.AssembleNoteArg1 => Unit
  ): Either[com.histmersion.util.basemap.BasemapFetch.AssembleResultPart1, (com.histmersion.util.basemap.BasemapFetch.AssembleResultPart2Part1, com.histmersion.util.basemap.BasemapFetch.AssembleResultPart2Part2)] =
    val starts = p.requests.map(_._1).toArray
    val readers = mutable.HashMap.empty[Int, RandomReader]
    def read(offset: Long, length: Int): Array[Byte] =
      val i = { val k = java.util.Arrays.binarySearch(starts, offset); if k >= 0 then k else -k - 2 }
      val r = p.requests(i)
      val reader = readers.getOrElseUpdate(
        i,
        work.get(chunkKey(r)).getOrElse(throw IOException(s"${work.where(chunkKey(r))}: no such file")).reader()
      )
      reader.read(ByteOffset.applyUnsafe(offset - r._1), length)
    val writer = PmTilesWriter(out, key)
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
    finally readers.values.foreach(_.close())

  /** The local archive's header: the source's tile type and compression, the extract's zooms and region. The writer
    * fills in the offsets.
    */
  def localHeader(p: Plan): PmTiles.Header =
    val b = p.bbox
    p.header.modify(
      _.minZoom).setTo(p.minZoom).modify(_.maxZoom).setTo(p.maxZoom).modify(_.minLon).setTo(b.minLon).modify(_.minLat).setTo(b.minLat).modify(_.maxLon).setTo(b.maxLon).modify(_.maxLat).setTo(b.maxLat).modify(_.centerZoom).setTo(math.max(p.minZoom, math.min(p.maxZoom, 5))).modify(_.centerLon).setTo((b.minLon + b.maxLon) / 2).modify(_.centerLat).setTo((b.minLat + b.maxLat) / 2
    )

  /** The local archive's metadata: the source's, its `vector_layers` without the `dropped` ones, its attribution
    * replaced by `attribution`, and where and when it came from — and, for the vector layer, what it kept (`filter`:
    * `keepAll` or the `drop` list) — under `histmersion`.
    */
  def localMetadata(
      p: Plan,
      dropped: Set[com.histmersion.util.basemap.BasemapFetch.LocalMetadataDropped],
      attribution: com.histmersion.util.basemap.BasemapFetch.LocalMetadataAttribution,
      fetchedAt: com.histmersion.util.basemap.BasemapFetch.LocalMetadataFetchedAt,
      layer: Basemap.Layer,
      filter: Option[(com.histmersion.util.basemap.BasemapFetch.LocalMetadataFilterPart1, com.histmersion.util.basemap.BasemapFetch.LocalMetadataFilterPart2)] = None
  ): Json =
    val src = p.metadata.asObject.getOrElse(io.circe.JsonObject.empty)
    val layers = src("vector_layers").flatMap(_.asArray) match
      case Some(ls) if dropped.nonEmpty =>
        src.add(
          "vector_layers",
          Json.fromValues(ls.filterNot(l => l.hcursor.get[String]("id").toOption.exists(dropped)))
        )
      case _ => src
    val b = p.bbox
    Json.fromJsonObject(
      layers
        .add("attribution", attribution.asJson)
        .add(
          "histmersion",
          Json
            .obj(
              "layer" -> layer.wire.asJson,
              "source" -> p.source.url.asJson,
              "build" -> p.source.label.asJson,
              "etag" -> p.remote.etag.asJson,
              "bbox" -> s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}".asJson,
              "maxZoom" -> p.maxZoom.asJson,
              "fetchedAt" -> fetchedAt.asJson
            )
            .deepMerge(
              filter.fold(Json.obj())((all, drop) =>
                Json.obj("keepAll" -> all.asJson, "drop" -> (if all then "" else drop).asJson)
              )
            )
        )
    )

  /** The resume record of a work directory: a fetch continues only the download it describes. */
  def stateOf(p: Plan, extra: com.histmersion.util.basemap.BasemapFetch.StateOfExtra): Json = Json.obj(
    "url" -> p.source.url.asJson,
    "size" -> p.remote.size.asJson,
    "etag" -> p.remote.etag.asJson,
    "bbox" -> s"${p.bbox.minLon},${p.bbox.minLat},${p.bbox.maxLon},${p.bbox.maxLat}".asJson,
    "maxZoom" -> p.maxZoom.asJson,
    "extra" -> extra.asJson
  )

  /** Make `work` hold only the parts of THIS plan: a work store left by another download (another build, region or
    * depth) is emptied first. Answers how many parts are already there.
    */
  def prepare(work: Store, state: Json): com.histmersion.util.basemap.BasemapFetch.PrepareResult =
    val file = Store.Key.applyUnsafe("state.json")
    if work.isDirectory(Store.Key.Root) && !work
        .get(file)
        .flatMap(f => io.circe.parser.parse(f.text()).toOption)
        .contains(state)
    then work.list().foreach(f => work.delete(f.key))
    work.makeDirectory().left.foreach(e => throw IOException(e.message))
    work.write(file, state.noSpaces.getBytes(UTF_8)).left.foreach(e => throw IOException(e.message))
    work.list().count(_.key.name.endsWith(".bin"))
