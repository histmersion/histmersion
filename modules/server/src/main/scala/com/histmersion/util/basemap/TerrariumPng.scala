package com.histmersion.util.basemap

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.{CRC32, Deflater, Inflater}

/** Elevation tiles as Terrarium PNGs (the encoding MapLibre's `raster-dem` reads as `terrarium`): metres + 32768 spread
  * over red (×256), green (×1) and blue (×1/256), one 8-bit RGB pixel a sample.
  */
object TerrariumPng:

  type UnfilterLeftI = UnfilterLeftI.T
  object UnfilterLeftI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterUpI = UnfilterUpI.T
  object UnfilterUpI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterUpLeftI = UnfilterUpLeftI.T
  object UnfilterUpLeftI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type EncodeResult = EncodeResult.T
  object EncodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodePng = DecodePng.T
  object DecodePng extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterResultPart2 = UnfilterResultPart2.T
  object UnfilterResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterRaw = UnfilterRaw.T
  object UnfilterRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkData = ChunkData.T
  object ChunkData extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeflateResult = DeflateResult.T
  object DeflateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeflateRaw = DeflateRaw.T
  object DeflateRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InflateResultPart2 = InflateResultPart2.T
  object InflateResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InflateZ = InflateZ.T
  object InflateZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type EncodeSize = EncodeSize.T
  object EncodeSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResultPart1 = DecodeResultPart1.T
  object DecodeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResultPart2Part2 = DecodeResultPart2Part2.T
  object DecodeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterResultPart1 = UnfilterResultPart1.T
  object UnfilterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterWidth = UnfilterWidth.T
  object UnfilterWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterHeight = UnfilterHeight.T
  object UnfilterHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfilterBpp = UnfilterBpp.T
  object UnfilterBpp extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaethResult = PaethResult.T
  object PaethResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaethA = PaethA.T
  object PaethA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaethB = PaethB.T
  object PaethB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaethC = PaethC.T
  object PaethC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkKind = ChunkKind.T
  object ChunkKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InflateResultPart1 = InflateResultPart1.T
  object InflateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InflateExpected = InflateExpected.T
  object InflateExpected extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Signature = Array[Byte](0x89.toByte, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')
  private val RgbColourType = 2
  private val RgbaColourType = 6
  private val SubFilter = 1

  /** A square tile of `size` × `size` elevations (row by row, metres) as a Terrarium PNG. */
  def encode(metres: Array[Float], size: com.histmersion.util.basemap.TerrariumPng.EncodeSize): com.histmersion.util.basemap.TerrariumPng.EncodeResult =
    val rowLength = 1 + size * 3
    val raw = new Array[Byte](rowLength * size)
    for y <- 0 until size do
      val row = y * rowLength
      raw(row) = SubFilter.toByte
      for x <- 0 until size do
        val v = (metres(y * size + x).toDouble + 32768).max(0).min(65535.996)
        val r = (v / 256).toInt
        val g = v.toInt % 256
        val b = ((v - v.toInt) * 256).toInt
        val at = row + 1 + x * 3
        raw(at) = r.toByte; raw(at + 1) = g.toByte; raw(at + 2) = b.toByte
      // the Sub filter: each byte minus the same channel of the pixel to its left
      var i = row + rowLength - 1 // from the end, so every left neighbour is still unfiltered
      while i > row + 3 do { raw(i) = (raw(i) - raw(i - 3)).toByte; i -= 1 }
    val out = ByteArrayOutputStream()
    out.write(Signature)
    chunk(
      out,
      "IHDR",
      ByteBuffer
        .allocate(13)
        .putInt(size)
        .putInt(size)
        .put(8.toByte)
        .put(RgbColourType.toByte)
        .put(0.toByte)
        .put(0.toByte)
        .put(0.toByte)
        .array()
    )
    chunk(out, "IDAT", deflate(raw))
    chunk(out, "IEND", Array.emptyByteArray)
    out.toByteArray

  /** A Terrarium PNG's elevations (metres, row by row) and its width: 8-bit RGB or RGBA, not interlaced. */
  def decode(png: com.histmersion.util.basemap.TerrariumPng.DecodePng): Either[com.histmersion.util.basemap.TerrariumPng.DecodeResultPart1, (Array[Float], com.histmersion.util.basemap.TerrariumPng.DecodeResultPart2Part2)] =
    if png.length < 33 || !java.util.Arrays.equals(png.take(8), Signature) then Left("not a PNG")
    else
      val b = ByteBuffer.wrap(png)
      val width = b.getInt(16); val height = b.getInt(20)
      val depth = png(24); val colour = png(25); val interlace = png(28)
      val channels = colour match
        case RgbColourType  => 3
        case RgbaColourType => 4
        case _              => 0
      if depth != 8 || channels == 0 || interlace != 0 then
        Left(s"a PNG of colour type $colour, depth $depth, interlace $interlace")
      else
        val idat = ByteArrayOutputStream()
        var at = 8 // the next chunk
        while at + 8 <= png.length do
          val len = b.getInt(at); val kind = new String(png, at + 4, 4, "US-ASCII")
          if kind == "IDAT" then idat.write(png, at + 8, len)
          at += 12 + len
        inflate(idat.toByteArray, (1 + width * channels) * height).flatMap { raw =>
          unfilter(raw, width, height, channels).map { px =>
            val m = new Array[Float](width * height)
            for i <- m.indices do
              val o = i * channels
              m(i) = ((px(o) & 0xff) * 256 + (px(o + 1) & 0xff) + (px(o + 2) & 0xff) / 256.0 - 32768).toFloat
            (m, width)
          }
        }

  private def unfilter(raw: com.histmersion.util.basemap.TerrariumPng.UnfilterRaw, width: com.histmersion.util.basemap.TerrariumPng.UnfilterWidth, height: com.histmersion.util.basemap.TerrariumPng.UnfilterHeight, bpp: com.histmersion.util.basemap.TerrariumPng.UnfilterBpp): Either[com.histmersion.util.basemap.TerrariumPng.UnfilterResultPart1, com.histmersion.util.basemap.TerrariumPng.UnfilterResultPart2] =
    val stride = width * bpp
    val out = new Array[Byte](stride * height)
    (0 until height)
      .foldLeft[Either[String, Unit]](Right(())) { (acc, y) =>
        acc.flatMap { _ =>
          val f = raw(y * (stride + 1))
          val src = y * (stride + 1) + 1
          val dst = y * stride
          def left(i: com.histmersion.util.basemap.TerrariumPng.UnfilterLeftI) = if i >= bpp then out(dst + i - bpp) & 0xff else 0
          def up(i: com.histmersion.util.basemap.TerrariumPng.UnfilterUpI) = if y > 0 then out(dst - stride + i) & 0xff else 0
          def upLeft(i: com.histmersion.util.basemap.TerrariumPng.UnfilterUpLeftI) = if y > 0 && i >= bpp then out(dst - stride + i - bpp) & 0xff else 0
          if f < 0 || f > 4 then Left(s"PNG filter $f")
          else
            for i <- 0 until stride do
              val x = raw(src + i) & 0xff
              val p = f match
                case 0 => 0
                case 1 => left(i)
                case 2 => up(i)
                case 3 => (left(i) + up(i)) / 2
                case _ => paeth(left(i), up(i), upLeft(i))
              out(dst + i) = (x + p).toByte
            Right(())
        }
      }
      .map(_ => out)

  private def paeth(a: com.histmersion.util.basemap.TerrariumPng.PaethA, b: com.histmersion.util.basemap.TerrariumPng.PaethB, c: com.histmersion.util.basemap.TerrariumPng.PaethC): com.histmersion.util.basemap.TerrariumPng.PaethResult =
    val p = a + b - c
    val (pa, pb, pc) = (math.abs(p - a), math.abs(p - b), math.abs(p - c))
    if pa <= pb && pa <= pc then a else if pb <= pc then b else c

  private def chunk(out: ByteArrayOutputStream, kind: com.histmersion.util.basemap.TerrariumPng.ChunkKind, data: com.histmersion.util.basemap.TerrariumPng.ChunkData): Unit =
    val k = kind.getBytes("US-ASCII")
    val crc = CRC32(); crc.update(k); crc.update(data)
    out.write(ByteBuffer.allocate(4).putInt(data.length).array())
    out.write(k); out.write(data)
    out.write(ByteBuffer.allocate(4).putInt(crc.getValue.toInt).array())

  private def deflate(raw: com.histmersion.util.basemap.TerrariumPng.DeflateRaw): com.histmersion.util.basemap.TerrariumPng.DeflateResult =
    val d = Deflater(Deflater.DEFAULT_COMPRESSION)
    try
      d.setInput(raw); d.finish()
      val out = ByteArrayOutputStream(); val buf = new Array[Byte](65536)
      while !d.finished() do out.write(buf, 0, d.deflate(buf))
      out.toByteArray
    finally d.end()

  private def inflate(z: com.histmersion.util.basemap.TerrariumPng.InflateZ, expected: com.histmersion.util.basemap.TerrariumPng.InflateExpected): Either[com.histmersion.util.basemap.TerrariumPng.InflateResultPart1, com.histmersion.util.basemap.TerrariumPng.InflateResultPart2] =
    val inf = Inflater()
    try
      inf.setInput(z)
      val out = new Array[Byte](expected)
      var n = 0 // bytes inflated so far
      while n < expected && !inf.finished() && !inf.needsInput() do n += inf.inflate(out, n, expected - n)
      Either.cond(n == expected, out, s"the PNG's image data holds $n bytes, not $expected")
    catch case e: java.util.zip.DataFormatException => Left(s"the PNG's image data does not inflate: ${e.getMessage}")
    finally inf.end()
