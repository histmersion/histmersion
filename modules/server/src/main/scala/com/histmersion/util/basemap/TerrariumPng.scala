package com.histmersion
package util.basemap
import com.histmersion.util.problem.{External, Problem}

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.{CRC32, Deflater, Inflater}
import io.github.iltotore.iron.constraint.any.Pure

/** Elevation tiles as Terrarium PNGs (the encoding MapLibre's `raster-dem` reads as `terrarium`): metres + 32768 spread
  * over red (×256), green (×1) and blue (×1/256), one 8-bit RGB pixel a sample.
  */
object TerrariumPng:
  type TileSize = TileSize.T
  object TileSize extends Newtype[Int, Pure]

  private val Signature = Array[Byte](0x89.toByte, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')
  private val RgbColourType = 2
  private val RgbaColourType = 6
  private val SubFilter = 1

  /** A square tile of `size` × `size` elevations (row by row, metres) as a Terrarium PNG. */
  def encode(metres: Array[Float], size: TileSize): Png.PngBytes =
    val side: Int = size.value
    val rowLength = 1 + side * 3
    val raw = new Array[Byte](rowLength * side)
    for y <- 0 until side do
      val row = y * rowLength
      raw(row) = SubFilter.toByte
      for x <- 0 until side do
        val v = (metres(y * side + x).toDouble + 32768).max(0).min(65535.996)
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
        .putInt(side)
        .putInt(side)
        .put(8.toByte)
        .put(RgbColourType.toByte)
        .put(0.toByte)
        .put(0.toByte)
        .put(0.toByte)
        .array()
    )
    chunk(out, "IDAT", deflate(raw))
    chunk(out, "IEND", Array.emptyByteArray)
    Png.PngBytes(out.toByteArray)

  /** A Terrarium PNG's elevations (metres, row by row) and its width: 8-bit RGB or RGBA, not interlaced. */
  def decode(file: Png.PngBytes): Either[Problem, (Array[Float], TileSize)] =
    val png: Array[Byte] = file.value
    if png.length < 33 || !java.util.Arrays.equals(png.take(8), Signature) then
      Left(Problem.Io.Corrupt(Problem.Io.Format.Png, Problem.Where("the signature"), Problem.Io.Damage.Foreign))
    else
      val b = ByteBuffer.wrap(png)
      val width = b.getInt(16); val height = b.getInt(20)
      val depth = png(24); val colour = png(25); val interlace = png(28)
      val channels = colour match
        case RgbColourType  => 3
        case RgbaColourType => 4
        case _              => 0
      if depth != 8 || channels == 0 || interlace != 0 then
        Left(
          Problem.Io.Corrupt(
            Problem.Io.Format.Png,
            Problem.Where("the image header"),
            Problem.Io.Damage.Unsupported(
              Problem.Io.Variant
                .PngLayout(Problem.Count(colour.toLong), Problem.Count(depth.toLong), Problem.Count(interlace.toLong))
            )
          )
        )
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
            (m, TileSize(width))
          }
        }

  private def unfilter(raw: Array[Byte], width: Int, height: Int, bpp: Int): Either[Problem, Array[Byte]] =
    val stride = width * bpp
    val out = new Array[Byte](stride * height)
    (0 until height)
      .foldLeft[Either[Problem, Unit]](Right(())) { (acc, y) =>
        acc.flatMap { _ =>
          val f = raw(y * (stride + 1))
          val src = y * (stride + 1) + 1
          val dst = y * stride
          def left(i: Int) = if i >= bpp then out(dst + i - bpp) & 0xff else 0
          def up(i: Int) = if y > 0 then out(dst - stride + i) & 0xff else 0
          def upLeft(i: Int) = if y > 0 && i >= bpp then out(dst - stride + i - bpp) & 0xff else 0
          if f < 0 || f > 4 then
            Left(
              Problem.Io.Corrupt(
                Problem.Io.Format.Png,
                Problem.Where("the image data"),
                Problem.Io.Damage.Unsupported(Problem.Io.Variant.PngFilter(Problem.Count(f.toLong)))
              )
            )
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

  private def paeth(a: Int, b: Int, c: Int): Int =
    val p = a + b - c
    val (pa, pb, pc) = (math.abs(p - a), math.abs(p - b), math.abs(p - c))
    if pa <= pb && pa <= pc then a else if pb <= pc then b else c

  private def chunk(out: ByteArrayOutputStream, kind: String, data: Array[Byte]): Unit =
    val k = kind.getBytes("US-ASCII")
    val crc = CRC32(); crc.update(k); crc.update(data)
    out.write(ByteBuffer.allocate(4).putInt(data.length).array())
    out.write(k); out.write(data)
    out.write(ByteBuffer.allocate(4).putInt(crc.getValue.toInt).array())

  private def deflate(raw: Array[Byte]): Array[Byte] =
    val d = Deflater(Deflater.DEFAULT_COMPRESSION)
    try
      d.setInput(raw); d.finish()
      val out = ByteArrayOutputStream(); val buf = new Array[Byte](65536)
      while !d.finished() do out.write(buf, 0, d.deflate(buf))
      out.toByteArray
    finally d.end()

  private def inflate(z: Array[Byte], expected: Int): Either[Problem, Array[Byte]] =
    val inf = Inflater()
    try
      inf.setInput(z)
      val out = new Array[Byte](expected)
      var n = 0 // bytes inflated so far
      while n < expected && !inf.finished() && !inf.needsInput() do n += inf.inflate(out, n, expected - n)
      Either.cond(
        n == expected,
        out,
        Problem.Io.Corrupt(
          Problem.Io.Format.Png,
          Problem.Where("the image data"),
          Problem.Io.Damage.Bytes(Problem.Count(expected.toLong), Problem.Count(n.toLong))
        )
      )
    catch
      case e: java.util.zip.DataFormatException =>
        Left(
          Problem.Io.Corrupt(
            Problem.Io.Format.Png,
            Problem.Where("the image data"),
            Problem.Io.Damage.Inflate(External.Reported.of(e))
          )
        )
    finally inf.end()
