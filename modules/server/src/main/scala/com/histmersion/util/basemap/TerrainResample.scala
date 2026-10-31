package com.histmersion.util.basemap

/** The relief deeper than any tile the server holds (docs/research-map-styles.md §4.4): a relief tile of zoom `z` that
  * neither the extract, a focus region nor the on-demand relief has is cut from its nearest ancestor that exists and
  * resampled — bilinearly, IN METRES (resampling terrarium's bytes would mix the high and the low byte) — and answered
  * as a terrarium PNG. So the map's one elevation source reaches the deepest zoom kept anywhere (a focus region's), and
  * the relief is shaded once everywhere: two overlapping elevation sources would shade a focus region twice.
  *
  * Pure: pixels and bytes in, pixels and bytes out.
  */
object TerrainResample:

  /** Terrarium's elevation of a pixel `0x??RRGGBB`, in metres. Pure. */
  def metres(p: Int): Double = ((p >> 16) & 0xff) * 256.0 + ((p >> 8) & 0xff) + (p & 0xff) / 256.0 - 32768.0

  /** The terrarium pixel (`0xffRRGGBB`) of `m` metres. Pure. */
  def encode(m: Double): Int =
    val v = math.max(0.0, math.min(65535.996, m + 32768.0))
    val whole = math.floor(v).toInt
    val r = whole >> 8
    val g = whole & 0xff
    val b = math.min(255, math.floor((v - whole) * 256).toInt)
    0xff000000 | r << 16 | g << 8 | b

  /** The part of an ancestor tile (`src`, `n` × `n` pixels) that tile `(ox, oy)` of the `2^dz` × `2^dz` under it
    * covers, resampled to `n` × `n` pixels bilinearly in metres; terrarium pixels out. Pure.
    */
  def resample(src: Array[Int], n: Int, dz: Int, ox: Long, oy: Long): Array[Int] =
    val s = 1L << dz
    val step = 1.0 / s
    val x0 = (ox % s).toDouble * n / s
    val y0 = (oy % s).toDouble * n / s
    val m = new Array[Double](src.length)
    var k = 0
    while k < src.length do
      m(k) = metres(src(k))
      k += 1
    val out = new Array[Int](n * n)
    val hi = n - 1.000001
    var j = 0
    while j < n do
      val fy = math.max(0.0, math.min(hi, y0 + (j + 0.5) * step - 0.5))
      val iy = fy.toInt
      val ty = fy - iy
      var i = 0
      while i < n do
        val fx = math.max(0.0, math.min(hi, x0 + (i + 0.5) * step - 0.5))
        val ix = fx.toInt
        val tx = fx - ix
        val a = iy * n + ix
        val e = (m(a) * (1 - tx) + m(a + 1) * tx) * (1 - ty) + (m(a + n) * (1 - tx) + m(a + n + 1) * tx) * ty
        out(j * n + i) = encode(e)
        i += 1
      j += 1
    out

  /** A relief tile's pixels: a lossless WebP or a PNG. Pure. */
  def decode(bytes: Array[Byte]): Either[String, Vp8l.Image] =
    if Png.isPng(bytes) then Png.decode(bytes) else Vp8l.decode(bytes)
