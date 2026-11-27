package com.histmersion
package util.basemap
import com.histmersion.util.problem.Problem

import java.io.ByteArrayOutputStream
import java.util.zip.{CRC32, Deflater, Inflater}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** PNG, as much as the relief needs: encoding RGB tiles, decoding 8-bit RGB(A) non-interlaced ones. Pure. */
object Png:

  /** The place in the data that a problem names: where in a PNG its layout is read from. */
  private val AtImageHeader: Problem.Where = Problem.Where("the image header")
  type CompressionLevel = CompressionLevel.T
  object CompressionLevel extends Newtype[Int, Pure]
  export Vp8l.Image.{Height, Pixels}
  export PmTiles.TileBytes as PngBytes
  export Vp8l.Image.Width

  private val Signature = Array[Byte](0x89.toByte, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isPng(b: PngBytes): Boolean =
    val a: Array[Byte] = b.value
    a.length >= 8 && a.take(8).sameElements(Signature)

  /** An RGB PNG of `px` (`0x??RRGGBB`, row-major, `w` × `h`), each row filtered by its left neighbour. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def encodeRgb(w: Width, h: Height, px: Pixels, level: CompressionLevel = CompressionLevel(4)): PngBytes =
    val width: Int = w.value
    val height: Int = h.value
    val pixels: Array[Int] = px.value
    val raw = new Array[Byte](height * (1 + 3 * width))
    var y = 0
    while y < height do
      val row = y * (1 + 3 * width)
      raw(row) = 1 // filter: Sub
      var x = 0
      var pr, pg, pb = 0
      while x < width do
        val p = pixels(y * width + x)
        val r = (p >> 16) & 0xff
        val g = (p >> 8) & 0xff
        val b = p & 0xff
        val o = row + 1 + 3 * x
        raw(o) = (r - pr).toByte
        raw(o + 1) = (g - pg).toByte
        raw(o + 2) = (b - pb).toByte
        pr = r; pg = g; pb = b
        x += 1
      y += 1
    val d = Deflater(level.value)
    d.setInput(raw)
    d.finish()
    val z = ByteArrayOutputStream(raw.length / 4)
    val buf = new Array[Byte](65536)
    while !d.finished() do
      val n = d.deflate(buf)
      z.write(buf, 0, n)
    d.end()
    val out = ByteArrayOutputStream(z.size() + 64)
    out.write(Signature)
    val ihdr = java.nio.ByteBuffer
      .allocate(13)
      .putInt(width)
      .putInt(height)
      .put(8.toByte)
      .put(2.toByte)
      .put(0.toByte)
      .put(0.toByte)
      .put(0.toByte)
      .array()
    chunk(out, "IHDR", ihdr)
    chunk(out, "IDAT", z.toByteArray)
    chunk(out, "IEND", Array.emptyByteArray)
    PngBytes(out.toByteArray)

  private def chunk(out: ByteArrayOutputStream, kind: String, data: Array[Byte]): Unit =
    val t = kind.getBytes("US-ASCII")
    out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array())
    out.write(t)
    out.write(data)
    val c = CRC32()
    c.update(t)
    c.update(data)
    out.write(java.nio.ByteBuffer.allocate(4).putInt(c.getValue.toInt).array())

  /** An 8-bit RGB or RGBA, non-interlaced PNG's pixels (`0xAARRGGBB`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decode(png: PngBytes): Either[Problem, Vp8l.Image] =
    if !isPng(png) then Left(Problem.Io.Damage.Foreign.in(Problem.Io.Format.Png, Problem.Where("the signature")))
    else
      val b: Array[Byte] = png.value
      def be32(i: Int) = (b(i) & 0xff) << 24 | (b(i + 1) & 0xff) << 16 | (b(i + 2) & 0xff) << 8 | (b(i + 3) & 0xff)
      var i = 8
      var w, h, depth, colour, interlace = -1
      val idat = ByteArrayOutputStream()
      var done = false
      while !done && i + 8 <= b.length do
        val n = be32(i)
        val t = new String(b, i + 4, 4, "US-ASCII")
        if n < 0 || i + 12 + n > b.length then done = true
        else
          t match
            case "IHDR" =>
              w = be32(i + 8); h = be32(i + 12); depth = b(i + 16) & 0xff; colour = b(i + 17) & 0xff;
              interlace = b(i + 20) & 0xff
            case "IDAT" => idat.write(b, i + 8, n)
            case "IEND" => done = true
            case _      => ()
          i += 12 + n
      val bpp = colour match
        case 2 => 3
        case 6 => 4
        case _ => 0
      if w <= 0 || h <= 0 then
        Left(
          Problem.Io.Flaw.NoImageHeader.in(Problem.Io.Format.Png, AtImageHeader)
        )
      else if depth != 8 || bpp == 0 || interlace != 0 then
        Left(
          Problem.Io.Variant
            .PngLayout(Problem.Count(colour.toLong), Problem.Count(depth.toLong), Problem.Count(interlace.toLong))
            .in(Problem.Io.Format.Png, AtImageHeader)
        )
      else
        val stride = w * bpp
        val raw = new Array[Byte](h * (stride + 1))
        val inf = Inflater()
        inf.setInput(idat.toByteArray)
        var got = 0
        while got < raw.length && !inf.finished() && !inf.needsInput() && !inf.needsDictionary() do
          got += inf.inflate(raw, got, raw.length - got)
        inf.end()
        if got < raw.length then
          Left(Problem.Io.Damage.Truncated.in(Problem.Io.Format.Png, Problem.Where("the image data")))
        else
          val cur = new Array[Int](stride)
          val prev = new Array[Int](stride)
          val px = new Array[Int](w * h)
          var y = 0
          while y < h do
            val row = y * (stride + 1)
            val f = raw(row)
            var x = 0
            while x < stride do
              val v = raw(row + 1 + x) & 0xff
              val a = if x >= bpp then cur(x - bpp) else 0
              val up = prev(x)
              val c = if x >= bpp then prev(x - bpp) else 0
              cur(x) = (f match
                case 1 => v + a
                case 2 => v + up
                case 3 => v + ((a + up) >> 1)
                case 4 =>
                  val p = a + up - c
                  val pa = math.abs(p - a); val pb = math.abs(p - up); val pc = math.abs(p - c)
                  v + (if pa <= pb && pa <= pc then a else if pb <= pc then up else c)
                case _ => v
              ) & 0xff
              x += 1
            var k = 0
            while k < w do
              val o = k * bpp
              val alpha = if bpp == 4 then cur(o + 3) else 255
              px(y * w + k) = alpha << 24 | cur(o) << 16 | cur(o + 1) << 8 | cur(o + 2)
              k += 1
            System.arraycopy(cur, 0, prev, 0, stride)
            y += 1
          Right(Vp8l.Image(Vp8l.Image.Width(w), Vp8l.Image.Height(h), Vp8l.Image.Pixels(px)))
