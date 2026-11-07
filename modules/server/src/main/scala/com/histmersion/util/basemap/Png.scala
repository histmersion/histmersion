package com.histmersion.util.basemap

import java.io.ByteArrayOutputStream
import java.util.zip.{CRC32, Deflater, Inflater}

/** PNG, as much as the relief needs: encoding RGB tiles, decoding 8-bit RGB(A) non-interlaced ones. Pure. */
object Png:
  type ChunkPayload = ChunkPayload.T
  object ChunkPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkType = ChunkType.T
  object ChunkType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompressionLevel = CompressionLevel.T
  object CompressionLevel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pixels = Pixels.T
  object Pixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PngBytes = PngBytes.T
  object PngBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type DecodeB = _root_.com.histmersion.util.basemap.Png.PngBytes
  val DecodeB: _root_.com.histmersion.util.basemap.Png.PngBytes.type = _root_.com.histmersion.util.basemap.Png.PngBytes


  private val Signature = Array[Byte](0x89.toByte, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a)

  def isPng(b: com.histmersion.util.basemap.Png.PngBytes): Boolean = b.length >= 8 && b.take(8).sameElements(Signature)

  /** An RGB PNG of `px` (`0x??RRGGBB`, row-major, `w` × `h`), each row filtered by its left neighbour. */
  def encodeRgb(w: com.histmersion.util.basemap.Png.Width, h: com.histmersion.util.basemap.Png.Height, px: com.histmersion.util.basemap.Png.Pixels, level: com.histmersion.util.basemap.Png.CompressionLevel = 4): com.histmersion.util.basemap.Png.PngBytes =
    val raw = new Array[Byte](h * (1 + 3 * w))
    var y = 0
    while y < h do
      val row = y * (1 + 3 * w)
      raw(row) = 1 // filter: Sub
      var x = 0
      var pr, pg, pb = 0
      while x < w do
        val p = px(y * w + x)
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
    val d = Deflater(level)
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
      .putInt(w)
      .putInt(h)
      .put(8.toByte)
      .put(2.toByte)
      .put(0.toByte)
      .put(0.toByte)
      .put(0.toByte)
      .array()
    chunk(out, "IHDR", ihdr)
    chunk(out, "IDAT", z.toByteArray)
    chunk(out, "IEND", Array.emptyByteArray)
    out.toByteArray

  private def chunk(out: ByteArrayOutputStream, kind: com.histmersion.util.basemap.Png.ChunkType, data: com.histmersion.util.basemap.Png.ChunkPayload): Unit =
    val t = kind.getBytes("US-ASCII")
    out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array())
    out.write(t)
    out.write(data)
    val c = CRC32()
    c.update(t)
    c.update(data)
    out.write(java.nio.ByteBuffer.allocate(4).putInt(c.getValue.toInt).array())

  /** An 8-bit RGB or RGBA, non-interlaced PNG's pixels (`0xAARRGGBB`). */
  def decode(b: com.histmersion.util.basemap.Png.DecodeB): Either[com.histmersion.util.basemap.Png.Problem, Vp8l.Image] =
    if !isPng(b) then Left("not a PNG")
    else
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
      if w <= 0 || h <= 0 || depth != 8 || bpp == 0 || interlace != 0 then
        Left("only 8-bit RGB(A) non-interlaced PNGs are read")
      else
        val stride = w * bpp
        val raw = new Array[Byte](h * (stride + 1))
        val inf = Inflater()
        inf.setInput(idat.toByteArray)
        var got = 0
        while got < raw.length && !inf.finished() && !inf.needsInput() && !inf.needsDictionary() do
          got += inf.inflate(raw, got, raw.length - got)
        inf.end()
        if got < raw.length then Left("truncated PNG data")
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
          Right(Vp8l.Image(w, h, px))
