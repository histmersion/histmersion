package com.histmersion.util.basemap

import com.histmersion.api.{BBox, Plural}
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.config.BasemapGebcoConfig
import com.histmersion.util.storage.Store

import java.io.IOException
import java.nio.{ByteBuffer, ByteOrder}
import scala.util.Using

/** The sea floor from the GEBCO grid (15 arc-seconds, ~460 m; public domain), made into Terrarium tiles here: GEBCO
  * publishes the grid as eight 90° GeoTIFFs of uncompressed rows, so the rows a region needs are read by range, kept
  * beside the archive until it is written (an interrupted fetch resumes), and sampled into the tiles of zooms
  * 0…`gebco.max-zoom`. A tile reaching past the region takes its outside from ETOPO1's tile at `etopoUrl` (one GET), as
  * the ETOPO1 extract has it.
  */
final class GebcoSeaFloor(cfg: BasemapGebcoConfig, etopoUrl: GebcoSeaFloor.EtopoUrl, http: RangeHttp, maxRequestBytes: GebcoSeaFloor.MaxRequestBytes):

  private val HeaderBytes = 256 * 1024
  private val TileSize = 256
  private val PngGuess = 80L * 1024 // a tile, about (the ETOPO1 extract's, 2026-10-20)

  /** The GeoTIFFs of the grid's tiling that meet `bbox`, their headers read. */
  def plan(bbox: BBox, maxZoom: com.histmersion.util.basemap.GebcoSeaFloor.PlanMaxZoom): Either[com.histmersion.util.basemap.GebcoSeaFloor.Problem, GebcoPlan] =
    val deg = cfg.tileDegrees
    val cells = for
      west <- (-180 until 180 by deg).toList
      south <- (-90 until 90 by deg).toList
      if west < bbox.maxLon && west + deg > bbox.minLon && south < bbox.maxLat && south + deg > bbox.minLat
    yield (west, south)
    cells
      .foldLeft[Either[String, List[GebcoPart]]](Right(Nil)) { case (acc, (west, south)) =>
        acc.flatMap { done =>
          val url = GebcoSeaFloor.urlOf(cfg.url, west, south, deg)
          val name = s"${west}_$south"
          for
            head <- http.bytes(url, 0, HeaderBytes).map(_._1)
            grid <- GeoTiffGrid.parse(head, (o, l) => http.bytes(url, o, l).map(_._1)).left.map(e => s"$url: $e")
          yield grid.window(bbox).fold(done)(w => done :+ GebcoPart(name, url, grid, w, grid.runs(w, maxRequestBytes)))
        }
      }
      .map { parts =>
        val tiles = GebcoSeaFloor.tiles(bbox, maxZoom)
        GebcoPlan(bbox, maxZoom, parts, tiles, tiles.map(_._2).filterNot(GebcoSeaFloor.inside(bbox, _)).toSet)
      }

  /** What a fetch would do, zoom by zoom — the dry run's answer. */
  def describe(p: GebcoPlan): com.histmersion.util.basemap.GebcoSeaFloor.Description =
    val byZoom = p.tiles.groupBy(_._2._1).toList.sortBy(_._1).map((z, ts) => s"zoom $z: ${ts.size}").mkString(" · ")
    s"bathymetry from GEBCO (zoom 0–${p.maxZoom}): would read ${Backup.humanBytes(p.readBytes)} of " +
      s"${Plural.count(p.parts.size, "GeoTIFF", "GeoTIFFs")} in ${Plural.count(p.parts.map(_.runs.size).sum, "range request", "range requests")}, " +
      s"and write ${Plural.count(p.tiles.size, "tile", "tiles")} (about ${Backup.humanBytes(p.tiles.size * PngGuess)}; $byZoom), " +
      s"${p.edge.size} of them completed from ETOPO1 outside the region"

  /** The bytes a fetch needs on disk at most: the rows read and the tiles written. */
  def needs(p: GebcoPlan): com.histmersion.ByteCount = p.readBytes + p.tiles.size * PngGuess

  /** Read the rows still missing from `work`. */
  def download(p: GebcoPlan, work: Store, gapMs: com.histmersion.util.basemap.BasemapFetch.Pace.GapMs, note: com.histmersion.util.basemap.GebcoSeaFloor.Note => Unit): Either[com.histmersion.util.basemap.GebcoSeaFloor.Problem, Unit] =
    work.makeDirectory().left.foreach(e => throw IOException(e.message))
    val all = p.parts.flatMap(part => part.runs.map(part -> _))
    all.zipWithIndex.foldLeft[Either[String, Unit]](Right(())) { case (acc, ((part, run), i)) =>
      acc.flatMap { _ =>
        val f = part.key(run)
        if work.stat(f).exists(s => s.isFile && s.size.value == run.length) then Right(())
        else
          if Thread.interrupted() then throw InterruptedException()
          if i > 0 then Thread.sleep(gapMs)
          note(s"reading GEBCO ${part.name}: part ${i + 1} of ${all.size} (${Backup.humanBytes(run.length)})")
          Using.resource(work.reserve(f).fold(e => throw IOException(e.message), identity)) { rows =>
            http.download(part.url, run.offset.value, run.length, rows).flatMap { _ =>
              Either
                .cond(
                  rows.size.value == run.length,
                  (),
                  s"${part.url}: ${rows.size.value} bytes for a range of ${run.length}"
                )
                .flatMap(_ => rows.commit().left.map(_.message).map(_ => ()))
            }
          }
      }
    }

  /** Every tile of the plan from the rows in `work`, in tile-id order, handed to `add`. */
  def build(p: GebcoPlan, work: Store, add: (com.histmersion.util.basemap.GebcoSeaFloor.TileKey, com.histmersion.util.basemap.GebcoSeaFloor.Bytes) => Unit, note: com.histmersion.util.basemap.GebcoSeaFloor.Note => Unit): Either[com.histmersion.util.basemap.GebcoSeaFloor.Problem, com.histmersion.ByteCount] =
    val samplers = p.parts.map(part => GebcoSeaFloor.Rows(part, part.runs.map(r => r -> map(work, part.key(r)))))
    var etopoFailures = 0 // ETOPO1 tiles that could not be read: their outside stays at sea level
    p.tiles.zipWithIndex.foreach { case ((id, (z, x, y)), i) =>
      if Thread.interrupted() then throw InterruptedException()
      if i % 200 == 0 then note(s"writing tile ${i + 1} of ${p.tiles.size}")
      lazy val etopo: Option[(Array[Float], com.histmersion.util.basemap.GebcoSeaFloor.GridSize)] =
        http
          .file(etopoUrl.replace("{z}", z.toString).replace("{x}", x.toString).replace("{y}", y.toString))
          .toOption
          .flatten
          .flatMap(b => TerrariumPng.decode(b).toOption)
          .orElse { etopoFailures += 1; None }
      val metres = new Array[Float](TileSize * TileSize)
      for py <- 0 until TileSize; px <- 0 until TileSize do
        val v = GebcoSeaFloor.sample(samplers, z, x, y, px, py, TileSize)
        metres(py * TileSize + px) = v.getOrElse {
          if !p.edge.contains((z, x, y)) then 0f
          else etopo.fold(0f)((m, w) => m((py * w / TileSize) * w + px * w / TileSize))
        }
      add(id, TerrariumPng.encode(metres, TileSize))
    }
    if etopoFailures > 0 then note(s"$etopoFailures edge tile(s) had no ETOPO1 tile: their outside is at sea level")
    Right(p.tiles.size.toLong)

  private def map(work: Store, f: Store.Key): ByteBuffer =
    work.get(f).getOrElse(throw IOException(s"${work.where(f)}: no such file")).mapped().order(ByteOrder.LITTLE_ENDIAN)

object GebcoSeaFloor:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Column = Column.T
  object Column extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Elevation = Elevation.T
  object Elevation extends _root_.io.github.iltotore.iron.RefinedType[Short, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GridSize = GridSize.T
  object GridSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelX = PixelX.T
  object PixelX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelY = PixelY.T
  object PixelY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Row = Row.T
  object Row extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowIndex = RowIndex.T
  object RowIndex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SampledElevation = SampledElevation.T
  object SampledElevation extends _root_.io.github.iltotore.iron.RefinedType[Float, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileKey = TileKey.T
  object TileKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlTemplate = UrlTemplate.T
  object UrlTemplate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WholeDegrees = WholeDegrees.T
  object WholeDegrees extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A GeoTIFF's URL in the grid's tiling: `{north}`, `{south}`, `{west}`, `{east}` as GEBCO writes them (`90.0`). */
  type BoundsLatT = BoundsLatT.T
  object BoundsLatT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type EtopoUrl = EtopoUrl.T
  object EtopoUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRequestBytes = com.histmersion.util.basemap.BasemapFetch.Pace.MaxRequestBytes
  val MaxRequestBytes: _root_.com.histmersion.util.basemap.BasemapFetch.Pace.MaxRequestBytes.type = _root_.com.histmersion.util.basemap.BasemapFetch.Pace.MaxRequestBytes

  type PlanMaxZoom = _root_.com.histmersion.util.basemap.GebcoPlan.MaxZoom
  val PlanMaxZoom: _root_.com.histmersion.util.basemap.GebcoPlan.MaxZoom.type = _root_.com.histmersion.util.basemap.GebcoPlan.MaxZoom
  type BoundsResultPart1 = BoundsResultPart1.T
  object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart2 = BoundsResultPart2.T
  object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart3 = BoundsResultPart3.T
  object BoundsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart4 = BoundsResultPart4.T
  object BoundsResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def urlOf(template: com.histmersion.util.basemap.GebcoSeaFloor.UrlTemplate, west: com.histmersion.util.basemap.GebcoSeaFloor.WholeDegrees, south: com.histmersion.util.basemap.GebcoSeaFloor.WholeDegrees, deg: com.histmersion.util.basemap.GebcoSeaFloor.WholeDegrees): com.histmersion.util.basemap.GebcoPart.Url =
    def f(v: com.histmersion.util.basemap.GebcoSeaFloor.WholeDegrees) = String.format(java.util.Locale.ROOT, "%.1f", v.toDouble)
    template
      .replace("{north}", f(south + deg))
      .replace("{south}", f(south))
      .replace("{west}", f(west))
      .replace("{east}", f(west + deg))

  /** The tiles of zooms 0…`maxZoom` over `b`, in tile-id order (the archive's). */
  def tiles(b: BBox, maxZoom: com.histmersion.util.basemap.GebcoPlan.MaxZoom): Vector[(com.histmersion.util.basemap.GebcoSeaFloor.TileKey, (com.histmersion.util.basemap.GebcoSeaFloor.Zoom, com.histmersion.util.basemap.GebcoSeaFloor.TileX, com.histmersion.util.basemap.GebcoSeaFloor.TileY))] =
    (0 to maxZoom).toVector
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
        for x <- x0 to x1; y <- y0 to y1 yield (PmTiles.tileId(z, x, y), (z, x, y))
      }
      .sortBy(_._1)

  /** The west, south, east and north of a tile, in degrees (Web Mercator). */
  def bounds(z: com.histmersion.util.basemap.GebcoSeaFloor.Zoom, x: com.histmersion.util.basemap.GebcoSeaFloor.TileX, y: com.histmersion.util.basemap.GebcoSeaFloor.TileY): (com.histmersion.util.basemap.GebcoSeaFloor.BoundsResultPart1, com.histmersion.util.basemap.GebcoSeaFloor.BoundsResultPart2, com.histmersion.util.basemap.GebcoSeaFloor.BoundsResultPart3, com.histmersion.util.basemap.GebcoSeaFloor.BoundsResultPart4) =
    val n = math.pow(2, z)
    def lat(t: com.histmersion.util.basemap.GebcoSeaFloor.BoundsLatT) = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * t / n))))
    (x / n * 360 - 180, lat(y + 1.0), (x + 1) / n * 360 - 180, lat(y.toDouble))

  /** A tile wholly inside `b`. */
  def inside(b: BBox, t: (com.histmersion.util.basemap.GebcoSeaFloor.Zoom, com.histmersion.util.basemap.GebcoSeaFloor.TileX, com.histmersion.util.basemap.GebcoSeaFloor.TileY)): Boolean =
    val (w, s, e, n) = bounds(t._1, t._2, t._3)
    w >= b.minLon && e <= b.maxLon && s >= b.minLat && n <= b.maxLat

  /** The rows of one part that were read, each run mapped from its file. */
  final case class Rows(part: GebcoPart, runs: List[(RowRun, ByteBuffer)]):
    private val byRow: com.histmersion.util.basemap.GebcoSeaFloor.RowIndex =
      val a = Array.fill(part.grid.height.value)(-1)
      runs.zipWithIndex.foreach { case ((r, _), i) => for row <- r.firstRow until r.firstRow + r.rows do a(row) = i }
      a

    /** The sample at a column and row, when it was read and is not "no data". */
    def at(col: com.histmersion.util.basemap.GebcoSeaFloor.Column, row: com.histmersion.util.basemap.GebcoSeaFloor.Row): Option[com.histmersion.util.basemap.GebcoSeaFloor.Elevation] =
      val w = part.window
      if row < w.first || row > w.last || col < w.firstCol || col > w.lastCol then None
      else
        val (run, buf) = runs(byRow(row))
        val v = buf.getShort(((row - run.firstRow) * part.grid.rowBytes + col * 2L).toInt)
        Option.when(!part.grid.noData.contains(v))(v)

  object Rows:


  /** The elevation under pixel (`px`, `py`) of tile (`z`, `x`, `y`): the mean of the grid's samples under four points
    * spread over the pixel (a pixel of a low zoom covers many samples). None when the rows read hold none of them.
    */
  def sample(rows: List[Rows], z: com.histmersion.util.basemap.GebcoSeaFloor.Zoom, x: com.histmersion.util.basemap.GebcoSeaFloor.TileX, y: com.histmersion.util.basemap.GebcoSeaFloor.TileY, px: com.histmersion.util.basemap.GebcoSeaFloor.PixelX, py: com.histmersion.util.basemap.GebcoSeaFloor.PixelY, size: com.histmersion.util.basemap.GebcoSeaFloor.GridSize): Option[com.histmersion.util.basemap.GebcoSeaFloor.SampledElevation] =
    val n = math.pow(2, z) * size
    var sum = 0.0 // the samples found under the four points, and how many
    var found = 0
    for (dx, dy) <- List((0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)) do
      val gx = x * size + px + dx
      val gy = y * size + py + dy
      val lon = gx / n * 360 - 180
      val lat = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * gy / n))))
      rows.iterator
        .flatMap { r =>
          val (c, rr) = r.part.grid.pixelAt(lon, lat)
          r.at(math.floor(c).toInt, math.floor(rr).toInt)
        }
        .nextOption()
        .foreach { v => sum += v; found += 1 }
    Option.when(found > 0)((sum / found).toFloat)
