package com.histmersion
package util.basemap

import com.histmersion.api.BBox
import com.histmersion.{ByteCount, ByteOffset}
import io.github.iltotore.iron.*

import java.nio.{ByteBuffer, ByteOrder}
import io.github.iltotore.iron.constraint.any.Pure

/** A single-band elevation grid in a GeoTIFF, as far as reading it by range needs: where it lies (its north-west corner
  * and the size of a pixel, in degrees), its size in pixels, where each row's signed 16-bit samples begin in the file,
  * and the value that marks no data.
  */
final case class GeoTiffGrid(
    west: Degrees,
    north: Degrees,
    stepX: Degrees.PerPixel,
    stepY: Degrees.PerPixel,
    width: PixelCount,
    height: PixelCount,
    rowOffsets: Vector[ByteOffset],
    noData: Option[GeoTiffGrid.NoData]
):

  def rowBytes: ByteCount = ByteCount.applyUnsafe(width.value * 2L)
  def east: Degrees = Degrees(west.value + width.value * stepX.value)
  def south: Degrees = Degrees(north.value - height.value * stepY.value)

  /** The pixels whose area meets `b` (half-open on the east and south edges, so a grid that only touches the box is not
    * read); None when none does.
    */
  def window(b: BBox): Option[GridWindow] =
    val c0 = math.floor((b.minLon.value - west.value) / stepX.value).toInt.max(0)
    val c1 = (math.ceil((b.maxLon.value - west.value) / stepX.value).toInt - 1).min(width.value - 1)
    val r0 = math.floor((north.value - b.maxLat.value) / stepY.value).toInt.max(0)
    val r1 = (math.ceil((north.value - b.minLat.value) / stepY.value).toInt - 1).min(height.value - 1)
    Option.when(c0 <= c1 && r0 <= r1)(
      GridWindow(GridWindow.First(r0), GridWindow.Last(r1), GridWindow.FirstCol(c0), GridWindow.LastCol(c1))
    )

  /** The window's rows as range requests of whole rows, each at most `maxBytes` (and at least one row): rows that
    * follow each other in the file share a request.
    */
  def runs(w: GridWindow, maxBytes: ByteCount): List[RowRun] =
    val rowLength = rowBytes.value
    val perRun = math.max(1L, maxBytes.value / rowLength).toInt
    val last = w.last.value
    val out = List.newBuilder[RowRun]
    var start = w.first.value // the first row of the run being built
    var r = w.first.value + 1
    while r <= last + 1 do
      val contiguous = r <= last && rowOffsets(r).value == rowOffsets(r - 1).value + rowLength
      if !contiguous || r - start >= perRun then
        out += RowRun(RowRun.FirstRow(start), RowRun.Rows(r - start), rowOffsets(start), RowRun.Length((r - start) * rowLength))
        start = r
      r += 1
    out.result()

  /** The pixel whose area holds (`lon`, `lat`), fractional: its column and row, centres at `.5`. */
  def pixelAt(lon: Degrees, lat: Degrees): (GeoTiffGrid.PixelX, GeoTiffGrid.PixelY) =
    (GeoTiffGrid.PixelX((lon.value - west.value) / stepX.value), GeoTiffGrid.PixelY((north.value - lat.value) / stepY.value))

object GeoTiffGrid:
  type PixelX = PixelX.T
  object PixelX extends RefinedType[Double, Pure]
  type PixelY = PixelY.T
  object PixelY extends RefinedType[Double, Pure]
  type Problem = RangeHttp.Problem
  val Problem: RangeHttp.Problem.type = RangeHttp.Problem
  type NoData = NoData.T
  object NoData extends RefinedType[Short, Pure]

  // TIFF and GeoTIFF tag numbers (TIFF 6.0; OGC GeoTIFF 1.1; GDAL's no-data tag)
  private val ImageWidth = 256; private val ImageLength = 257; private val BitsPerSample = 258
  private val Compression = 259; private val StripOffsets = 273; private val SamplesPerPixel = 277
  private val RowsPerStrip = 278; private val PlanarConfiguration = 284; private val TileWidth = 322
  private val SampleFormat = 339; private val ModelPixelScale = 33550; private val ModelTiepoint = 33922
  private val GeoKeyDirectory = 34735; private val GdalNoData = 42113
  private val RasterTypeGeoKey = 1025
  private val PixelIsPoint = 2
  private val SignedInteger = 2

  private val TypeSize =
    Map(1 -> 1, 2 -> 1, 3 -> 2, 4 -> 4, 5 -> 8, 6 -> 1, 7 -> 1, 8 -> 2, 9 -> 4, 10 -> 8, 11 -> 4, 12 -> 8)

  final private case class Entry(tag: Int, kind: Int, count: Long, at: Long, inline: Array[Byte])

  private def le(b: Array[Byte]) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

  /** One image file directory: its entries, and their values read from the file as asked. */
  final private class Directory(entries: Map[Int, Entry], bytesAt: (Long, Int) => Either[String, Array[Byte]]):
    def has(tag: Int): Boolean = entries.contains(tag)
    private def raw(tag: Int): Either[String, Option[(Entry, Array[Byte])]] = entries.get(tag) match
      case None    => Right(None)
      case Some(e) =>
        val size = TypeSize.getOrElse(e.kind, 1) * e.count
        (if size <= 4 then Right(e.inline) else bytesAt(e.at, size.toInt)).map(b => Some(e -> b))
    def ints(tag: Int): Either[String, Option[Vector[Long]]] = raw(tag).map(_.map { (e, b) =>
      val buf = le(b)
      (0 until e.count.toInt).toVector.map(i =>
        if e.kind == 3 then (buf.getShort(i * 2) & 0xffff).toLong else buf.getInt(i * 4).toLong & 0xffffffffL
      )
    })
    def doubles(tag: Int): Either[String, Option[Vector[Double]]] =
      raw(tag).map(_.map((e, b) => (0 until e.count.toInt).toVector.map(i => le(b).getDouble(i * 8))))
    def text(tag: Int): Either[String, Option[String]] =
      raw(tag).map(_.map((_, b) => new String(b, "US-ASCII").takeWhile(_ != '\u0000').trim))
    def one(tag: Int, default: Option[Long] = None): Either[String, Long] =
      ints(tag).flatMap(_.flatMap(_.headOption).orElse(default).toRight(s"no tag $tag"))

  /** Read the grid from the start of its file (`head`), asking `read(offset, length)` for whatever lies beyond it (the
    * strip offsets of a large grid). A little-endian classic TIFF holding one band of uncompressed signed 16-bit
    * samples in strips — GEBCO's GeoTIFFs — and nothing else: anything else is refused with what it is.
    */
  def parse(
      head: RangeHttp.ResponseBytes,
      read: (ByteOffset, ByteCount) => Either[RangeHttp.Problem, RangeHttp.ResponseBytes]
  ): Either[Problem, GeoTiffGrid] =
    parseBytes(
      head.value,
      (offset, length) =>
        read(ByteOffset.applyUnsafe(offset), ByteCount.applyUnsafe(length.toLong)).map(_.value).left.map(_.value)
    ).left.map(Problem(_))

  private def parseBytes(head: Array[Byte], read: (Long, Int) => Either[String, Array[Byte]]): Either[String, GeoTiffGrid] =
    def bytesAt(offset: Long, length: Int): Either[String, Array[Byte]] =
      if offset + length <= head.length then
        Right(java.util.Arrays.copyOfRange(head, offset.toInt, offset.toInt + length))
      else read(offset, length)
    for
      _ <- Either.cond(head.length >= 8 && head(0) == 'I' && head(1) == 'I', (), "not a little-endian TIFF")
      _ <- Either.cond(le(head).getShort(2) == 42, (), "not a classic TIFF (BigTIFF is not read)")
      ifdAt = le(head).getInt(4).toLong & 0xffffffffL
      countB <- bytesAt(ifdAt, 2)
      n = le(countB).getShort(0) & 0xffff
      dir <- bytesAt(ifdAt + 2, n * 12)
      entries = (0 until n)
        .map { i =>
          val e = le(dir); val o = i * 12
          Entry(
            e.getShort(o) & 0xffff,
            e.getShort(o + 2) & 0xffff,
            e.getInt(o + 4).toLong & 0xffffffffL,
            e.getInt(o + 8).toLong & 0xffffffffL,
            java.util.Arrays.copyOfRange(dir, o + 8, o + 12)
          )
        }
        .map(e => e.tag -> e)
        .toMap
      grid <- gridOf(Directory(entries, bytesAt))
    yield grid

  private def gridOf(d: Directory): Either[String, GeoTiffGrid] =
    for
      width <- d.one(ImageWidth)
      height <- d.one(ImageLength)
      _ <- d.one(BitsPerSample).flatMap(b => Either.cond(b == 16, (), s"$b-bit samples (only 16-bit are read)"))
      _ <- d
        .one(Compression, Some(1))
        .flatMap(c => Either.cond(c == 1, (), s"compression $c (only uncompressed grids are read)"))
      _ <- d.one(SamplesPerPixel, Some(1)).flatMap(s => Either.cond(s == 1, (), s"$s bands (only one is read)"))
      _ <- d.one(PlanarConfiguration, Some(1)).flatMap(p => Either.cond(p == 1, (), s"planar configuration $p"))
      _ <- d
        .one(SampleFormat, Some(1))
        .flatMap(f => Either.cond(f == SignedInteger, (), s"sample format $f (only signed integers are read)"))
      _ <- Either.cond(!d.has(TileWidth), (), "a tiled TIFF (only strips are read)")
      perStrip <- d.one(RowsPerStrip, Some(height))
      strips <- d.ints(StripOffsets).flatMap(_.toRight("no strip offsets"))
      scale <- d.doubles(ModelPixelScale).flatMap(_.filter(_.size >= 2).toRight("no pixel scale (not a GeoTIFF)"))
      tie <- d.doubles(ModelTiepoint).flatMap(_.filter(_.size >= 6).toRight("no tie point (not a GeoTIFF)"))
      keys <- d.ints(GeoKeyDirectory).map(_.getOrElse(Vector.empty))
      noData <- d.text(GdalNoData).map(_.flatMap(_.toDoubleOption).map(v => NoData(v.toShort)))
      stepX <- Degrees.PerPixel.either(scale(0))
      stepY <- Degrees.PerPixel.either(scale(1))
      w <- PixelCount.either(width.toInt)
      h <- PixelCount.either(height.toInt)
      offsets <- rowOffsets(strips, perStrip, height, width * 2)
    yield
      val point = rasterType(keys).contains(PixelIsPoint)
      val west = tie(3) - tie(0) * scale(0) - (if point then scale(0) / 2 else 0)
      val north = tie(4) + tie(1) * scale(1) + (if point then scale(1) / 2 else 0)
      GeoTiffGrid(Degrees(west), Degrees(north), stepX, stepY, w, h, offsets, noData)

  /** Where each row begins: its strip's offset, plus the rows before it in that strip. */
  private def rowOffsets(
      strips: Vector[Long],
      perStrip: Long,
      height: Long,
      rowBytes: Long
  ): Either[String, Vector[ByteOffset]] =
    val b = Vector.newBuilder[ByteOffset]
    (0L until height)
      .foldLeft[Either[String, Unit]](Right(())) { (acc, r) =>
        acc.flatMap { _ =>
          strips
            .lift((r / perStrip).toInt)
            .toRight(s"no strip for row $r")
            .flatMap(o => ByteOffset.either(o + (r % perStrip) * rowBytes))
            .map { o => b += o; () }
        }
      }
      .map(_ => b.result())

  /** The GeoKey directory's `GTRasterTypeGeoKey`: 1 = a pixel is an area, 2 = a point. */
  private def rasterType(keys: Vector[Long]): Option[Long] =
    keys.drop(4).grouped(4).collectFirst { case Vector(id, 0L, 1L, v) if id == RasterTypeGeoKey => v }
