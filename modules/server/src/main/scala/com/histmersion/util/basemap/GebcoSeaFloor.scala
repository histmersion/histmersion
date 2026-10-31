package com.histmersion.util.basemap

import com.histmersion.api.{Backup, Basemap, BBox, Plural}
import com.histmersion.config.BasemapGebcoConfig
import com.histmersion.util.storage.{Store, StoreKey}

import java.io.IOException
import java.nio.{ByteBuffer, ByteOrder}
import scala.util.Using

/** The sea floor from the GEBCO grid (15 arc-seconds, ~460 m; public domain), made into Terrarium tiles here: GEBCO
  * publishes the grid as eight 90° GeoTIFFs of uncompressed rows, so the rows a region needs are read by range, kept
  * beside the archive until it is written (an interrupted fetch resumes), and sampled into the tiles of zooms
  * 0…`gebco.max-zoom`. A tile reaching past the region takes its outside from ETOPO1's tile at `etopoUrl` (one GET), as
  * the ETOPO1 extract has it.
  */
final class GebcoSeaFloor(cfg: BasemapGebcoConfig, etopoUrl: String, http: RangeHttp, maxRequestBytes: Long):

  private val HeaderBytes = 256 * 1024
  private val TileSize = 256
  private val PngGuess = 80L * 1024 // a tile, about (the ETOPO1 extract's, 2026-10-20)

  /** The GeoTIFFs of the grid's tiling that meet `bbox`, their headers read. */
  def plan(bbox: BBox, maxZoom: Int): Either[String, GebcoPlan] =
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
  def describe(p: GebcoPlan): String =
    val byZoom = p.tiles.groupBy(_._2._1).toList.sortBy(_._1).map((z, ts) => s"zoom $z: ${ts.size}").mkString(" · ")
    s"bathymetry from GEBCO (zoom 0–${p.maxZoom}): would read ${Backup.humanBytes(p.readBytes)} of " +
      s"${Plural.count(p.parts.size, "GeoTIFF", "GeoTIFFs")} in ${Plural.count(p.parts.map(_.runs.size).sum, "range request", "range requests")}, " +
      s"and write ${Plural.count(p.tiles.size, "tile", "tiles")} (about ${Backup.humanBytes(p.tiles.size * PngGuess)}; $byZoom), " +
      s"${p.edge.size} of them completed from ETOPO1 outside the region"

  /** The bytes a fetch needs on disk at most: the rows read and the tiles written. */
  def needs(p: GebcoPlan): Long = p.readBytes + p.tiles.size * PngGuess

  /** Read the rows still missing from `work`. */
  def download(p: GebcoPlan, work: Store, gapMs: Long, note: String => Unit): Either[String, Unit] =
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
  def build(p: GebcoPlan, work: Store, add: (Long, Array[Byte]) => Unit, note: String => Unit): Either[String, Long] =
    val samplers = p.parts.map(part => GebcoSeaFloor.Rows(part, part.runs.map(r => r -> map(work, part.key(r)))))
    var etopoFailures = 0 // ETOPO1 tiles that could not be read: their outside stays at sea level
    p.tiles.zipWithIndex.foreach { case ((id, (z, x, y)), i) =>
      if Thread.interrupted() then throw InterruptedException()
      if i % 200 == 0 then note(s"writing tile ${i + 1} of ${p.tiles.size}")
      lazy val etopo: Option[(Array[Float], Int)] =
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

  private def map(work: Store, f: StoreKey): ByteBuffer =
    work.get(f).getOrElse(throw IOException(s"${work.where(f)}: no such file")).mapped().order(ByteOrder.LITTLE_ENDIAN)

object GebcoSeaFloor:

  /** A GeoTIFF's URL in the grid's tiling: `{north}`, `{south}`, `{west}`, `{east}` as GEBCO writes them (`90.0`). */
  def urlOf(template: String, west: Int, south: Int, deg: Int): String =
    def f(v: Int) = String.format(java.util.Locale.ROOT, "%.1f", v.toDouble)
    template
      .replace("{north}", f(south + deg))
      .replace("{south}", f(south))
      .replace("{west}", f(west))
      .replace("{east}", f(west + deg))

  /** The tiles of zooms 0…`maxZoom` over `b`, in tile-id order (the archive's). */
  def tiles(b: BBox, maxZoom: Int): Vector[(Long, (Int, Long, Long))] =
    (0 to maxZoom).toVector
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
        for x <- x0 to x1; y <- y0 to y1 yield (PmTiles.tileId(z, x, y), (z, x, y))
      }
      .sortBy(_._1)

  /** The west, south, east and north of a tile, in degrees (Web Mercator). */
  def bounds(z: Int, x: Long, y: Long): (Double, Double, Double, Double) =
    val n = math.pow(2, z)
    def lat(t: Double) = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * t / n))))
    (x / n * 360 - 180, lat(y + 1.0), (x + 1) / n * 360 - 180, lat(y.toDouble))

  /** A tile wholly inside `b`. */
  def inside(b: BBox, t: (Int, Long, Long)): Boolean =
    val (w, s, e, n) = bounds(t._1, t._2, t._3)
    w >= b.minLon && e <= b.maxLon && s >= b.minLat && n <= b.maxLat

  /** The rows of one part that were read, each run mapped from its file. */
  final case class Rows(part: GebcoPart, runs: List[(RowRun, ByteBuffer)]):
    private val byRow: Array[Int] =
      val a = Array.fill(part.grid.height.value)(-1)
      runs.zipWithIndex.foreach { case ((r, _), i) => for row <- r.firstRow until r.firstRow + r.rows do a(row) = i }
      a

    /** The sample at a column and row, when it was read and is not "no data". */
    def at(col: Int, row: Int): Option[Short] =
      val w = part.window
      if row < w.first || row > w.last || col < w.firstCol || col > w.lastCol then None
      else
        val (run, buf) = runs(byRow(row))
        val v = buf.getShort(((row - run.firstRow) * part.grid.rowBytes + col * 2L).toInt)
        Option.when(!part.grid.noData.contains(v))(v)

  /** The elevation under pixel (`px`, `py`) of tile (`z`, `x`, `y`): the mean of the grid's samples under four points
    * spread over the pixel (a pixel of a low zoom covers many samples). None when the rows read hold none of them.
    */
  def sample(rows: List[Rows], z: Int, x: Long, y: Long, px: Int, py: Int, size: Int): Option[Float] =
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
