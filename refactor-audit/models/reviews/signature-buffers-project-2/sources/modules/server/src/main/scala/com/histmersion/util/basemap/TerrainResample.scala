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
  type ResampleResult = ResampleResult.T
  object ResampleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResampleSrc = ResampleSrc.T
  object ResampleSrc extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeBytes = DecodeBytes.T
  object DecodeBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MetresResult = MetresResult.T
  object MetresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetresP = MetresP.T
  object MetresP extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeResult = EncodeResult.T
  object EncodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeM = EncodeM.T
  object EncodeM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResampleN = ResampleN.T
  object ResampleN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResampleDz = ResampleDz.T
  object ResampleDz extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResampleOx = ResampleOx.T
  object ResampleOx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResampleOy = ResampleOy.T
  object ResampleOy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResultPart1 = DecodeResultPart1.T
  object DecodeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def metres(p: com.histmersion.util.basemap.TerrainResample.MetresP): com.histmersion.util.basemap.TerrainResample.MetresResult = ((p >> 16) & 0xff) * 256.0 + ((p >> 8) & 0xff) + (p & 0xff) / 256.0 - 32768.0

  /** The terrarium pixel (`0xffRRGGBB`) of `m` metres. Pure. */
  def encode(m: com.histmersion.util.basemap.TerrainResample.EncodeM): com.histmersion.util.basemap.TerrainResample.EncodeResult =
    val v = math.max(0.0, math.min(65535.996, m + 32768.0))
    val whole = math.floor(v).toInt
    val r = whole >> 8
    val g = whole & 0xff
    val b = math.min(255, math.floor((v - whole) * 256).toInt)
    0xff000000 | r << 16 | g << 8 | b

  /** The part of an ancestor tile (`src`, `n` × `n` pixels) that tile `(ox, oy)` of the `2^dz` × `2^dz` under it
    * covers, resampled to `n` × `n` pixels bilinearly in metres; terrarium pixels out. Pure.
    */
  def resample(src: com.histmersion.util.basemap.TerrainResample.ResampleSrc, n: com.histmersion.util.basemap.TerrainResample.ResampleN, dz: com.histmersion.util.basemap.TerrainResample.ResampleDz, ox: com.histmersion.util.basemap.TerrainResample.ResampleOx, oy: com.histmersion.util.basemap.TerrainResample.ResampleOy): com.histmersion.util.basemap.TerrainResample.ResampleResult =
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
  def decode(bytes: com.histmersion.util.basemap.TerrainResample.DecodeBytes): Either[com.histmersion.util.basemap.TerrainResample.DecodeResultPart1, Vp8l.Image] =
    if Png.isPng(bytes) then Png.decode(bytes) else Vp8l.decode(bytes)
