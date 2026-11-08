package com.histmersion
package util.basemap

import com.histmersion.{ByteCount, ByteOffset}
import com.histmersion.api.{BBox, Plural}
import com.histmersion.api.backup.Backup
import com.histmersion.api.basemap.Basemap
import com.histmersion.config.{BasemapBathymetryConfig, BasemapGebcoConfig}
import com.histmersion.util.storage.Store

import java.io.IOException
import java.nio.{ByteBuffer, ByteOrder}
import scala.util.Using
import io.github.iltotore.iron.constraint.any.Pure

/** The sea floor from the GEBCO grid (15 arc-seconds, ~460 m; public domain), made into Terrarium tiles here: GEBCO
  * publishes the grid as eight 90° GeoTIFFs of uncompressed rows, so the rows a region needs are read by range, kept
  * beside the archive until it is written (an interrupted fetch resumes), and sampled into the tiles of zooms
  * 0…`gebco.max-zoom`. A tile reaching past the region takes its outside from ETOPO1's tile at `etopoUrl` (one GET), as
  * the ETOPO1 extract has it.
  */
final class GebcoSeaFloor(
    cfg: BasemapGebcoConfig,
    etopoUrl: BasemapBathymetryConfig.Url,
    http: RangeHttp,
    maxRequestBytes: ByteCount
):

  private val HeaderBytes = 256 * 1024
  private val TileSize = 256
  private val PngGuess = 80L * 1024 // a tile, about (the ETOPO1 extract's, 2026-10-20)

  private def human(n: Long): String = Backup.humanBytes(com.histmersion.api.ByteCount(n))

  private def count(n: Long, singular: String, plural: String): Plural.Phrase =
    Plural.count(Plural.CountN(n), Plural.CountSingular(singular), Plural.CountPlural(plural))

  /** The GeoTIFFs of the grid's tiling that meet `bbox`, their headers read. */
  def plan(bbox: BBox, maxZoom: PmTiles.Zoom): Either[GebcoSeaFloor.Problem, GebcoPlan] =
    val deg = cfg.tileDegrees.value
    val cells = for
      west <- (-180 until 180 by deg).toList
      south <- (-90 until 90 by deg).toList
      if west < bbox.maxLon.value && west + deg > bbox.minLon.value && south < bbox.maxLat.value && south + deg > bbox.minLat.value
    yield (west, south)
    cells
      .foldLeft[Either[GebcoSeaFloor.Problem, List[GebcoPart]]](Right(Nil)) { case (acc, (west, south)) =>
        acc.flatMap { done =>
          val url = GebcoSeaFloor.urlOf(
            cfg.url,
            GebcoSeaFloor.WholeDegrees(west),
            GebcoSeaFloor.WholeDegrees(south),
            GebcoSeaFloor.WholeDegrees(deg)
          )
          val name = GebcoPart.Name(s"${west}_$south")
          for
            head <- http.bytes(url, ByteOffset.applyUnsafe(0L), ByteCount.applyUnsafe(HeaderBytes.toLong)).map(_._1)
            grid <- GeoTiffGrid
              .parse(head, (o, l) => http.bytes(url, o, l).map(_._1))
              .left
              .map(e => GebcoSeaFloor.Problem(s"$url: $e"))
          yield grid.window(bbox).fold(done)(w => done :+ GebcoPart(name, url, grid, w, grid.runs(w, maxRequestBytes)))
        }
      }
      .map { parts =>
        val tiles = GebcoSeaFloor.tiles(bbox, maxZoom)
        GebcoPlan(bbox, maxZoom, parts, tiles, tiles.map(_._2).filterNot(GebcoSeaFloor.inside(bbox, _)).toSet)
      }

  /** What a fetch would do, zoom by zoom — the dry run's answer. */
  def describe(p: GebcoPlan): GebcoSeaFloor.Description =
    val byZoom =
      p.tiles
        .groupBy(_._2._1)
        .toList
        .sortBy[Int](_._1.value)
        .map((z, ts) => s"zoom ${z.value}: ${ts.size}")
        .mkString(" · ")
    GebcoSeaFloor.Description(
      s"bathymetry from GEBCO (zoom 0–${p.maxZoom.value}): would read ${human(p.readBytes.value)} of " +
        s"${count(p.parts.size.toLong, "GeoTIFF", "GeoTIFFs")} in ${count(p.parts.map(_.runs.size).sum.toLong, "range request", "range requests")}, " +
        s"and write ${count(p.tiles.size.toLong, "tile", "tiles")} (about ${human(p.tiles.size * PngGuess)}; $byZoom), " +
        s"${p.edge.size} of them completed from ETOPO1 outside the region"
    )

  /** The bytes a fetch needs on disk at most: the rows read and the tiles written. */
  def needs(p: GebcoPlan): ByteCount = ByteCount.applyUnsafe(p.readBytes.value + p.tiles.size * PngGuess)

  /** Read the rows still missing from `work`. */
  def download(
      p: GebcoPlan,
      work: Store,
      gapMs: BasemapFetch.Pace.GapMs,
      note: GebcoSeaFloor.Note => Unit
  ): Either[GebcoSeaFloor.Problem, Unit] =
    work.makeDirectory().left.foreach(e => throw IOException(e.message.value))
    val all = p.parts.flatMap(part => part.runs.map(part -> _))
    all.zipWithIndex.foldLeft[Either[GebcoSeaFloor.Problem, Unit]](Right(())) { case (acc, ((part, run), i)) =>
      acc.flatMap { _ =>
        val f = part.key(run)
        if work.stat(f).exists(s => s.isFile && s.size.value == run.length.value) then Right(())
        else
          if Thread.interrupted() then throw InterruptedException()
          if i > 0 then Thread.sleep(gapMs.value)
          note(
            GebcoSeaFloor.Note(s"reading GEBCO ${part.name}: part ${i + 1} of ${all.size} (${human(run.length.value)})")
          )
          Using.resource(work.reserve(f).fold(e => throw IOException(e.message.value), identity)) { rows =>
            http.download(part.url, run.offset, ByteCount.applyUnsafe(run.length.value), rows).flatMap { _ =>
              Either
                .cond(
                  rows.size.value == run.length.value,
                  (),
                  GebcoSeaFloor.Problem(s"${part.url}: ${rows.size.value} bytes for a range of ${run.length}")
                )
                .flatMap(_ => rows.commit().left.map(e => GebcoSeaFloor.Problem(e.message.value)).map(_ => ()))
            }
          }
      }
    }

  /** Every tile of the plan from the rows in `work`, in tile-id order, handed to `add`. */
  def build(
      p: GebcoPlan,
      work: Store,
      add: (GebcoSeaFloor.TileKey, PmTiles.TileBytes) => Unit,
      note: GebcoSeaFloor.Note => Unit
  ): Either[GebcoSeaFloor.Problem, BasemapFetch.TileCount] =
    val samplers = p.parts.map(part => GebcoSeaFloor.Rows(part, part.runs.map(r => r -> map(work, part.key(r)))))
    var etopoFailures = 0 // ETOPO1 tiles that could not be read: their outside stays at sea level
    p.tiles.zipWithIndex.foreach { case ((id, (z, x, y)), i) =>
      if Thread.interrupted() then throw InterruptedException()
      if i % 200 == 0 then note(GebcoSeaFloor.Note(s"writing tile ${i + 1} of ${p.tiles.size}"))
      lazy val etopo: Option[(Array[Float], Int)] =
        http
          .file(
            RangeHttp.Url(
              etopoUrl.value
                .replace("{z}", z.value.toString)
                .replace("{x}", x.value.toString)
                .replace("{y}", y.value.toString)
            )
          )
          .toOption
          .flatten
          .flatMap(b => TerrariumPng.decode(PmTiles.TileBytes(b.value)).toOption)
          .map((m, w) => (m, w.value))
          .orElse { etopoFailures += 1; None }
      val metres = new Array[Float](TileSize * TileSize)
      for py <- 0 until TileSize; px <- 0 until TileSize do
        val v = GebcoSeaFloor.sample(
          samplers,
          z,
          x,
          y,
          GebcoSeaFloor.PixelX(px),
          GebcoSeaFloor.PixelY(py),
          GebcoSeaFloor.GridSize(TileSize)
        )
        metres(py * TileSize + px) = v.map(_.value).getOrElse {
          if !p.edge.contains((z, x, y)) then 0f
          else etopo.fold(0f)((m, w) => m((py * w / TileSize) * w + px * w / TileSize))
        }
      add(id, TerrariumPng.encode(metres, TerrariumPng.TileSize(TileSize)))
    }
    if etopoFailures > 0 then
      note(GebcoSeaFloor.Note(s"$etopoFailures edge tile(s) had no ETOPO1 tile: their outside is at sea level"))
    Right(BasemapFetch.TileCount(p.tiles.size.toLong))

  private def map(work: Store, f: Store.Key): ByteBuffer =
    work.get(f).getOrElse(throw IOException(s"${work.where(f)}: no such file")).mapped().order(ByteOrder.LITTLE_ENDIAN)

object GebcoSeaFloor:
  type Column = Column.T
  object Column extends Newtype[Int, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Elevation = Elevation.T
  object Elevation extends Newtype[Short, Pure]
  type GridSize = GridSize.T
  object GridSize extends Newtype[Int, Pure]
  type Note = BasemapFetch.Note
  val Note: BasemapFetch.Note.type = BasemapFetch.Note
  type PixelX = PixelX.T
  object PixelX extends Newtype[Int, Pure]
  type PixelY = PixelY.T
  object PixelY extends Newtype[Int, Pure]
  type Problem = RangeHttp.Problem
  val Problem: RangeHttp.Problem.type = RangeHttp.Problem
  type Row = Row.T
  object Row extends Newtype[Int, Pure]
  type SampledElevation = SampledElevation.T
  object SampledElevation extends Newtype[Float, Pure]
  type TileKey = GebcoPlan.TileKey
  val TileKey: GebcoPlan.TileKey.type = GebcoPlan.TileKey
  type TileX = GebcoPlan.TileX
  val TileX: GebcoPlan.TileX.type = GebcoPlan.TileX
  type TileY = GebcoPlan.TileY
  val TileY: GebcoPlan.TileY.type = GebcoPlan.TileY
  type WholeDegrees = WholeDegrees.T
  object WholeDegrees extends Newtype[Int, Pure]
  type Zoom = GebcoPlan.Zoom
  val Zoom: GebcoPlan.Zoom.type = GebcoPlan.Zoom

  /** A GeoTIFF's URL in the grid's tiling: `{north}`, `{south}`, `{west}`, `{east}` as GEBCO writes them (`90.0`). */
  def urlOf(
      template: BasemapGebcoConfig.Url,
      west: WholeDegrees,
      south: WholeDegrees,
      deg: WholeDegrees
  ): GebcoPart.Url =
    def f(v: Int) = String.format(java.util.Locale.ROOT, "%.1f", v.toDouble)
    GebcoPart.Url(
      template.value
        .replace("{north}", f(south.value + deg.value))
        .replace("{south}", f(south.value))
        .replace("{west}", f(west.value))
        .replace("{east}", f(west.value + deg.value))
    )

  /** The tiles of zooms 0…`maxZoom` over `b`, in tile-id order (the archive's). */
  def tiles(b: BBox, maxZoom: PmTiles.Zoom): Vector[(TileKey, (Zoom, TileX, TileY))] =
    (0 to maxZoom.value).toVector
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, Basemap.Zoom(z))
        for x <- x0.value to x1.value; y <- y0.value to y1.value
        yield
          val tile = (Zoom(z), TileX(x), TileY(y))
          (PmTiles.tileId(tile._1, tile._2, tile._3), tile)
      }
      .sortBy[Long](_._1.value)

  /** The west, south, east and north of a tile, in degrees (Web Mercator). */
  def bounds(z: Zoom, x: TileX, y: TileY): (Degrees, Degrees, Degrees, Degrees) =
    val n = math.pow(2, z.value)
    def lat(t: Double) = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * t / n))))
    (
      Degrees(x.value / n * 360 - 180),
      Degrees(lat(y.value + 1.0)),
      Degrees((x.value + 1) / n * 360 - 180),
      Degrees(lat(y.value.toDouble))
    )

  /** A tile wholly inside `b`. */
  def inside(b: BBox, t: (Zoom, TileX, TileY)): Boolean =
    val (w, s, e, n) = bounds(t._1, t._2, t._3)
    w.value >= b.minLon.value && e.value <= b.maxLon.value && s.value >= b.minLat.value && n.value <= b.maxLat.value

  /** The rows of one part that were read, each run mapped from its file. */
  final case class Rows(part: GebcoPart, runs: List[(RowRun, ByteBuffer)]):
    private val byRow: Array[Int] =
      val a = Array.fill(part.grid.height.value)(-1)
      runs.zipWithIndex.foreach { case ((r, _), i) =>
        for row <- r.firstRow.value until r.firstRow.value + r.rows.value do a(row) = i
      }
      a

    /** The sample at a column and row, when it was read and is not "no data". */
    def at(col: Column, row: Row): Option[Elevation] =
      val w = part.window
      if row.value < w.first.value || row.value > w.last.value || col.value < w.firstCol.value || col.value > w.lastCol.value
      then None
      else
        val (run, buf) = runs(byRow(row.value))
        val v = buf.getShort(((row.value - run.firstRow.value) * part.grid.rowBytes.value + col.value * 2L).toInt)
        Option.when(!part.grid.noData.exists(_.value == v))(Elevation(v))

  /** The elevation under pixel (`px`, `py`) of tile (`z`, `x`, `y`): the mean of the grid's samples under four points
    * spread over the pixel (a pixel of a low zoom covers many samples). None when the rows read hold none of them.
    */
  def sample(
      rows: List[Rows],
      z: Zoom,
      x: TileX,
      y: TileY,
      px: PixelX,
      py: PixelY,
      size: GridSize
  ): Option[SampledElevation] =
    val n = math.pow(2, z.value) * size.value
    var sum = 0.0 // the samples found under the four points, and how many
    var found = 0
    for (dx, dy) <- List((0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)) do
      val gx = x.value * size.value + px.value + dx
      val gy = y.value * size.value + py.value + dy
      val lon = gx / n * 360 - 180
      val lat = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * gy / n))))
      rows.iterator
        .flatMap { r =>
          val (c, rr) = r.part.grid.pixelAt(Degrees(lon), Degrees(lat))
          r.at(Column(math.floor(c.value).toInt), Row(math.floor(rr.value).toInt))
        }
        .nextOption()
        .foreach { v => sum += v.value; found += 1 }
    Option.when(found > 0)(SampledElevation((sum / found).toFloat))
