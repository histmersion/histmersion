package com.histmersion.util.basemap

import com.histmersion.api.BBox
import com.histmersion.ByteOffset
import io.github.iltotore.iron.*

import java.nio.{ByteBuffer, ByteOrder}

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

  def rowBytes: com.histmersion.util.basemap.GeoTiffGrid.RowBytesResult = width.value * 2L
  def east: com.histmersion.util.basemap.GeoTiffGrid.EastResult = west.value + width.value * stepX.value
  def south: com.histmersion.util.basemap.GeoTiffGrid.SouthResult = north.value - height.value * stepY.value

  /** The pixels whose area meets `b` (half-open on the east and south edges, so a grid that only touches the box is not
    * read); None when none does.
    */
  def window(b: BBox): Option[GridWindow] =
    val c0 = math.floor((b.minLon - west.value) / stepX.value).toInt.max(0)
    val c1 = (math.ceil((b.maxLon - west.value) / stepX.value).toInt - 1).min(width.value - 1)
    val r0 = math.floor((north.value - b.maxLat) / stepY.value).toInt.max(0)
    val r1 = (math.ceil((north.value - b.minLat) / stepY.value).toInt - 1).min(height.value - 1)
    Option.when(c0 <= c1 && r0 <= r1)(GridWindow(r0, r1, c0, c1))

  /** The window's rows as range requests of whole rows, each at most `maxBytes` (and at least one row): rows that
    * follow each other in the file share a request.
    */
  def runs(w: GridWindow, maxBytes: com.histmersion.util.basemap.GeoTiffGrid.RunsMaxBytes): List[RowRun] =
    val perRun = math.max(1L, maxBytes / rowBytes).toInt
    val out = List.newBuilder[RowRun]
    var start = w.first // the first row of the run being built
    var r = w.first + 1
    while r <= w.last + 1 do
      val contiguous = r <= w.last && rowOffsets(r).value == rowOffsets(r - 1).value + rowBytes
      if !contiguous || r - start >= perRun then
        out += RowRun(start, r - start, rowOffsets(start), (r - start) * rowBytes)
        start = r
      r += 1
    out.result()

  /** The pixel whose area holds (`lon`, `lat`), fractional: its column and row, centres at `.5`. */
  def pixelAt(lon: com.histmersion.util.basemap.GeoTiffGrid.PixelAtLon, lat: com.histmersion.util.basemap.GeoTiffGrid.PixelAtLat): (com.histmersion.util.basemap.GeoTiffGrid.PixelAtResultPart1, com.histmersion.util.basemap.GeoTiffGrid.PixelAtResultPart2) =
    ((lon - west.value) / stepX.value, (north.value - lat) / stepY.value)

object GeoTiffGrid:

  // TIFF and GeoTIFF tag numbers (TIFF 6.0; OGC GeoTIFF 1.1; GDAL's no-data tag)
  type RowBytesResult = RowBytesResult.T
  object RowBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EastResult = EastResult.T
  object EastResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SouthResult = SouthResult.T
  object SouthResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsMaxBytes = RunsMaxBytes.T
  object RunsMaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelAtResultPart1 = PixelAtResultPart1.T
  object PixelAtResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelAtResultPart2 = PixelAtResultPart2.T
  object PixelAtResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelAtLon = PixelAtLon.T
  object PixelAtLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PixelAtLat = PixelAtLat.T
  object PixelAtLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReadArg1 = ParseReadArg1.T
  object ParseReadArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReadArg2 = ParseReadArg2.T
  object ParseReadArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseReadResultPart1 = ParseReadResultPart1.T
  object ParseReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GridOfResultPart1 = GridOfResultPart1.T
  object GridOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowOffsetsResultPart1 = RowOffsetsResultPart1.T
  object RowOffsetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowOffsetsStrips = RowOffsetsStrips.T
  object RowOffsetsStrips extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowOffsetsPerStrip = RowOffsetsPerStrip.T
  object RowOffsetsPerStrip extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowOffsetsHeight = RowOffsetsHeight.T
  object RowOffsetsHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowOffsetsRowBytes = RowOffsetsRowBytes.T
  object RowOffsetsRowBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RasterTypeResult = RasterTypeResult.T
  object RasterTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RasterTypeKeys = RasterTypeKeys.T
  object RasterTypeKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NoData = NoData.T
  object NoData extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Short, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  final private case class Entry(tag: Entry.Tag, kind: Entry.Kind, count: Entry.Count, at: Entry.At, inline: Entry.Inline)

  object Entry:
    type Inline = Inline.T
    object Inline extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Tag = Tag.T
    object Tag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def le(b: Array[Byte]) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

  /** One image file directory: its entries, and their values read from the file as asked. */
  final private class Directory(entries: Map[Int, Entry], bytesAt: (Long, Int) => Either[String, Array[Byte]]):
    def has(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.HasTag): com.histmersion.util.basemap.GeoTiffGrid.Directory.HasResult = entries.contains(tag)
    private def raw(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.RawTag): Either[com.histmersion.util.basemap.GeoTiffGrid.Directory.RawResultPart1, Option[(Entry, Array[Byte])]] = entries.get(tag) match
      case None    => Right(None)
      case Some(e) =>
        val size = TypeSize.getOrElse(e.kind, 1) * e.count
        (if size <= 4 then Right(e.inline) else bytesAt(e.at, size.toInt)).map(b => Some(e -> b))
    def ints(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.IntsTag): Either[com.histmersion.util.basemap.GeoTiffGrid.Directory.IntsResultPart1, Option[Vector[com.histmersion.util.basemap.GeoTiffGrid.Directory.IntsResultPart2]]] = raw(tag).map(_.map { (e, b) =>
      val buf = le(b)
      (0 until e.count.toInt).toVector.map(i =>
        if e.kind == 3 then (buf.getShort(i * 2) & 0xffff).toLong else buf.getInt(i * 4).toLong & 0xffffffffL
      )
    })
    def doubles(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.DoublesTag): Either[com.histmersion.util.basemap.GeoTiffGrid.Directory.DoublesResultPart1, Option[Vector[com.histmersion.util.basemap.GeoTiffGrid.Directory.DoublesResultPart2]]] =
      raw(tag).map(_.map((e, b) => (0 until e.count.toInt).toVector.map(i => le(b).getDouble(i * 8))))
    def text(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.TextTag): Either[com.histmersion.util.basemap.GeoTiffGrid.Directory.TextResultPart1, Option[com.histmersion.util.basemap.GeoTiffGrid.Directory.TextResultPart2]] =
      raw(tag).map(_.map((_, b) => new String(b, "US-ASCII").takeWhile(_ != '\u0000').trim))
    def one(tag: com.histmersion.util.basemap.GeoTiffGrid.Directory.OneTag, default: Option[com.histmersion.util.basemap.GeoTiffGrid.Directory.OneDefault] = None): Either[com.histmersion.util.basemap.GeoTiffGrid.Directory.OneResultPart1, com.histmersion.util.basemap.GeoTiffGrid.Directory.OneResultPart2] =
      ints(tag).flatMap(_.flatMap(_.headOption).orElse(default).toRight(s"no tag $tag"))

  object Directory:
    type HasResult = HasResult.T
    object HasResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasTag = HasTag.T
    object HasTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RawResultPart1 = RawResultPart1.T
    object RawResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RawTag = RawTag.T
    object RawTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntsResultPart1 = IntsResultPart1.T
    object IntsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntsResultPart2 = IntsResultPart2.T
    object IntsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntsTag = IntsTag.T
    object IntsTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoublesResultPart1 = DoublesResultPart1.T
    object DoublesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoublesResultPart2 = DoublesResultPart2.T
    object DoublesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoublesTag = DoublesTag.T
    object DoublesTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextResultPart1 = TextResultPart1.T
    object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextResultPart2 = TextResultPart2.T
    object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextTag = TextTag.T
    object TextTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OneResultPart1 = OneResultPart1.T
    object OneResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OneResultPart2 = OneResultPart2.T
    object OneResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OneTag = OneTag.T
    object OneTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OneDefault = OneDefault.T
    object OneDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Read the grid from the start of its file (`head`), asking `read(offset, length)` for whatever lies beyond it (the
    * strip offsets of a large grid). A little-endian classic TIFF holding one band of uncompressed signed 16-bit
    * samples in strips — GEBCO's GeoTIFFs — and nothing else: anything else is refused with what it is.
    */
  def parse(head: Array[Byte], read: (com.histmersion.util.basemap.GeoTiffGrid.ParseReadArg1, com.histmersion.util.basemap.GeoTiffGrid.ParseReadArg2) => Either[com.histmersion.util.basemap.GeoTiffGrid.ParseReadResultPart1, Array[Byte]]): Either[com.histmersion.util.basemap.GeoTiffGrid.ParseResultPart1, GeoTiffGrid] =
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

  private def gridOf(d: Directory): Either[com.histmersion.util.basemap.GeoTiffGrid.GridOfResultPart1, GeoTiffGrid] =
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
      noData <- d.text(GdalNoData).map(_.flatMap(_.toDoubleOption).map(_.toShort))
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
      strips: Vector[com.histmersion.util.basemap.GeoTiffGrid.RowOffsetsStrips],
      perStrip: com.histmersion.util.basemap.GeoTiffGrid.RowOffsetsPerStrip,
      height: com.histmersion.util.basemap.GeoTiffGrid.RowOffsetsHeight,
      rowBytes: com.histmersion.util.basemap.GeoTiffGrid.RowOffsetsRowBytes
  ): Either[com.histmersion.util.basemap.GeoTiffGrid.RowOffsetsResultPart1, Vector[ByteOffset]] =
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
  private def rasterType(keys: Vector[com.histmersion.util.basemap.GeoTiffGrid.RasterTypeKeys]): Option[com.histmersion.util.basemap.GeoTiffGrid.RasterTypeResult] =
    keys.drop(4).grouped(4).collectFirst { case Vector(id, 0L, 1L, v) if id == RasterTypeGeoKey => v }
