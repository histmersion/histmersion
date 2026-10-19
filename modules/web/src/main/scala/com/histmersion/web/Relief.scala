package com.histmersion.web

import com.histmersion.core.TintStop

import scala.scalajs.js
import scala.scalajs.js.typedarray.{Float32Array, Int16Array, Uint8ClampedArray}

/** The relief's arithmetic over elevation grids (docs/research-terrain-basemap.md, "The Natural style"):
  * decoding a terrarium tile, cutting a window of it with its neighbours' edge pixels (no seams at tile
  * edges), resampling a window smoothly to the pixels it covers (Catmull-Rom bicubic — a zoom deeper
  * than the data is never blocky), shading it from several light directions, and tinting it by
  * elevation. [[Contours]] traces the same grids.
  *
  * Pure: typed arrays in, typed arrays out; no DOM. The loops are `while` loops over `var`s — hundreds of
  * thousands of pixels a tile, on the page's own thread. */
object Relief:

  /** The side of an elevation tile, in pixels. */
  val Tile = 512

  /** Terrarium-encoded RGBA → whole metres (the provider's elevations are whole metres; −32 768…32 767). */
  def decode(rgba: Uint8ClampedArray, n: Int, out: Int16Array): Unit =
    var p = 0
    while p < n do
      val o = p * 4
      val e = rgba(o) * 256.0 + rgba(o + 1) + rgba(o + 2) / 256.0 - 32768.0
      out(p) = math.max(-32768, math.min(32767, math.round(e).toInt)).toShort
      p += 1

  /** A tile and its eight neighbours (`null` where there is none: the edge is then held), as one grid of
    * elevations: [[at]] reads pixel (px, py) of the centre tile's frame, −Tile ≤ px, py < 2·Tile. */
  final class Neighbourhood(centre: Int16Array, neighbours: js.Array[Int16Array]):
    /** `neighbours` by (dy + 1) · 3 + (dx + 1), the centre's own slot unused. */
    def at(px: Int, py: Int): Double =
      val tx = if px < 0 then -1 else if px >= Tile then 1 else 0
      val ty = if py < 0 then -1 else if py >= Tile then 1 else 0
      if tx == 0 && ty == 0 then centre(py * Tile + px)
      else
        val t = neighbours((ty + 1) * 3 + tx + 1)
        if t != null && !js.isUndefined(t) then t((py - ty * Tile) * Tile + (px - tx * Tile))
        else centre(math.max(0, math.min(Tile - 1, py)) * Tile + math.max(0, math.min(Tile - 1, px)))

  /** Which neighbours the `side`² window from (x0, y0) reaches into, as (dx, dy) — so only those are
    * fetched. Pure. */
  def neighboursNeeded(x0: Int, y0: Int, side: Int): List[(Int, Int)] =
    val xs = List(-1).filter(_ => x0 < 0) ++ List(0) ++ List(1).filter(_ => x0 + side > Tile)
    val ys = List(-1).filter(_ => y0 < 0) ++ List(0) ++ List(1).filter(_ => y0 + side > Tile)
    for dy <- ys; dx <- xs if dx != 0 || dy != 0 yield (dx, dy)

  /** The `side`² window of the centre tile's frame from (x0, y0) on, into `out` (row-major). */
  def window(nb: Neighbourhood, x0: Int, y0: Int, side: Int, out: Float32Array): Unit =
    var i = 0
    while i < side do
      var j = 0
      while j < side do
        out(i * side + j) = nb.at(x0 + j, y0 + i).toFloat
        j += 1
      i += 1

  /** Catmull-Rom weights of a sample at fraction `t` (0–1) past the second of four. Pure. */
  def cubic(t: Double, k: Int): Double = k match
    case 0 => ((-0.5 * t + 1.0) * t - 0.5) * t
    case 1 => (1.5 * t - 2.5) * t * t + 1.0
    case 2 => ((-1.5 * t + 2.0) * t + 0.5) * t
    case _ => (0.5 * t - 0.5) * t * t

  /** Per output position u ∈ 0…count−1, sampling source position `offset + (u − 1 + ½)·scale − ½` of a
    * row `sw` long: the first of its four taps (clamped inside the row) and their weights (a tap clamped
    * at an edge takes the weight of the one it stands for). */
  private def taps(count: Int, offset: Double, scale: Double, sw: Int): (js.Array[Int], Float32Array) =
    val first = new js.Array[Int](count)
    val wts   = new Float32Array(count * 4)
    var u = 0
    while u < count do
      val s  = offset + (u - 1 + 0.5) * scale - 0.5
      val s0 = math.floor(s).toInt
      val t  = s - s0
      first(u) = math.max(0, math.min(sw - 4, s0 - 1))
      val shift = s0 - 1 - first(u)
      var k = 0
      while k < 4 do
        val kk = math.max(0, math.min(3, k + shift))
        wts(u * 4 + kk) = (wts(u * 4 + kk) + cubic(t, k)).toFloat
        k += 1
      u += 1
    (first, wts)

  /** Resample a window (`src`, `sw`² values) to `r` pixels a side, bicubic, into `out` ((r + 2)²: one
    * output pixel more on every side, for the shading's neighbours). The window holds the `span` source
    * pixels to cover from (offX, offY) on, in its own pixel coordinates; output pixel u ∈ −1…r samples
    * position off + (u + ½)·span/r − ½. `tmp` holds at least sw·(r + 2) values. */
  def resample(src: Float32Array, sw: Int, offX: Double, offY: Double, span: Double, r: Int, out: Float32Array,
      tmp: Float32Array): Unit =
    val ow = r + 2
    val scale = span / r
    val (fx, wx) = taps(ow, offX, scale, sw)
    val (fy, wy) = taps(ow, offY, scale, sw)
    // rows: every window row resampled across → tmp (sw rows × ow columns)
    var y = 0
    while y < sw do
      val row = y * sw
      var x = 0
      while x < ow do
        val f = fx(x) + row
        val w = x * 4
        tmp(y * ow + x) = (src(f) * wx(w) + src(f + 1) * wx(w + 1) + src(f + 2) * wx(w + 2) + src(f + 3) * wx(w + 3)).toFloat
        x += 1
      y += 1
    // columns: tmp resampled down → out
    var v = 0
    while v < ow do
      val f = fy(v)
      val w = v * 4
      var x = 0
      while x < ow do
        out(v * ow + x) = (tmp(f * ow + x) * wy(w) + tmp((f + 1) * ow + x) * wy(w + 1) +
          tmp((f + 2) * ow + x) * wy(w + 2) + tmp((f + 3) * ow + x) * wy(w + 3)).toFloat
        x += 1
      v += 1

  /** The lights a relief is shaded with: unit vectors towards each light (east, north, up) and their
    * weights, summing to 1. `multi` (0–1) moves weight from the main light to three more around it
    * (−60°, +60°, the opposite side low), so slopes facing away from the main light are modelled too. */
  final case class Lights(x: Array[Double], y: Array[Double], z: Array[Double], w: Array[Double]):
    /** The light on flat ground: what neither shadows nor highlights. */
    def flat: Double = z.indices.map(i => z(i) * w(i)).sum

  def lights(azimuthDeg: Double, altitudeDeg: Double, multi: Double): Lights =
    val m = math.max(0.0, math.min(1.0, multi))
    val dirs = List((azimuthDeg, altitudeDeg, 1 - m * 0.75), (azimuthDeg - 60, altitudeDeg, m * 0.3),
      (azimuthDeg + 60, altitudeDeg, m * 0.3), (azimuthDeg + 180, altitudeDeg + (90 - altitudeDeg) * 0.5, m * 0.15))
      .filter(_._3 > 0)
    val total = dirs.map(_._3).sum
    def v(az: Double, alt: Double) =
      val a = math.toRadians(az); val h = math.toRadians(alt)
      (math.sin(a) * math.cos(h), math.cos(a) * math.cos(h), math.sin(h))
    val vs = dirs.map((a, h, wt) => (v(a, h), wt / total))
    Lights(vs.map(_._1._1).toArray, vs.map(_._1._2).toArray, vs.map(_._1._3).toArray, vs.map(_._2).toArray)

  /** The light on one cell from its 3×3 neighbourhood `a b c / d · f / g h i` (rows north to south),
    * cells `mpp` metres apart, slopes × `ex`: Horn's gradient, the surface normal against each light.
    * Pure. */
  def illumination(a: Double, b: Double, c: Double, d: Double, f: Double, g: Double, h: Double, i: Double,
      mpp: Double, ex: Double, l: Lights): Double =
    val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8 * mpp) * ex   // eastwards
    val dzdy = ((g + 2 * h + i) - (a + 2 * b + c)) / (8 * mpp) * ex   // southwards
    // the normal (east, north, up) = (−dz/dE, −dz/dN, 1) = (−dzdx, dzdy, 1), normalised
    val len = math.sqrt(dzdx * dzdx + dzdy * dzdy + 1)
    val nx = -dzdx / len; val ny = dzdy / len; val nz = 1 / len
    var s = 0.0
    var k = 0
    while k < l.w.length do
      s += l.w(k) * math.max(0.0, nx * l.x(k) + ny * l.y(k) + nz * l.z(k))
      k += 1
    s

  /** Shade a grid `g` ((r + 2)², one margin pixel) into `out` (r² RGBA): flat ground transparent, slopes
    * away from the light `shadow` (opacity to `shadowAlpha` 0–255), towards it `highlight`. */
  def shade(g: Float32Array, r: Int, mpp: Double, ex: Double, l: Lights, shadow: (Int, Int, Int), shadowAlpha: Int,
      highlight: (Int, Int, Int), highlightAlpha: Int, out: Uint8ClampedArray, sea: Float32Array = null, seaShading: Double = 1): Unit =
    val w = r + 2
    val flat = l.flat
    var y = 0
    while y < r do
      var x = 0
      val r0 = y * w; val r1 = r0 + w; val r2 = r1 + w
      while x < r do
        val s = illumination(g(r0 + x), g(r0 + x + 1), g(r0 + x + 2), g(r1 + x), g(r1 + x + 2), g(r2 + x), g(r2 + x + 1),
          g(r2 + x + 2), mpp, ex, l)
        val o = (y * r + x) * 4
        // the sea floor's shading, weakened (`sea`: the share of sea at each grid sample, 0–1)
        val k = if sea == null then 1.0 else 1 - math.max(0.0, math.min(1.0, sea(r1 + x + 1).toDouble)) * (1 - seaShading)
        if s < flat then
          out(o) = shadow._1; out(o + 1) = shadow._2; out(o + 2) = shadow._3
          out(o + 3) = math.round((flat - s) / flat * shadowAlpha * k).toInt
        else
          out(o) = highlight._1; out(o + 1) = highlight._2; out(o + 2) = highlight._3
          out(o + 3) = math.round((s - flat) / (1 - flat) * highlightAlpha * k).toInt
        x += 1
      y += 1

  /** A hypsometric tint as a lookup table: one RGBA entry every `step` metres from `min`. */
  final case class Lut(min: Double, step: Double, rgba: Uint8ClampedArray):
    def size: Int = rgba.length / 4
    /** The entry of elevation `e` (clamped to the table). */
    def index(e: Double): Int = math.max(0, math.min(size - 1, math.round((e - min) / step).toInt))

  /** The table of `stops` (elevation → RGBA, ascending): linear between stops, the end colours beyond,
    * `alpha` (0–1) on every entry. Pure. */
  def lut(stops: List[(Double, (Int, Int, Int, Double))], alpha: Double, step: Double = 2): Lut =
    val lo = stops.headOption.fold(0.0)(_._1)
    val hi = stops.lastOption.fold(0.0)(_._1)
    val n  = math.max(1, math.ceil((hi - lo) / step).toInt + 1)
    val out = new Uint8ClampedArray(n * 4)
    var i = 0
    while i < n do
      val e = lo + i * step
      val (r, g, b, a) = tintAt(stops, e)
      out(i * 4) = math.round(r).toInt; out(i * 4 + 1) = math.round(g).toInt; out(i * 4 + 2) = math.round(b).toInt
      out(i * 4 + 3) = math.round(a * alpha * 255).toInt
      i += 1
    Lut(lo, step, out)

  /** The tint at elevation `e`: linear between the stops around it. Pure. */
  def tintAt(stops: List[(Double, (Int, Int, Int, Double))], e: Double): (Double, Double, Double, Double) =
    stops match
      case Nil => (0, 0, 0, 0)
      case (e0, c0) :: _ if e <= e0 => (c0._1.toDouble, c0._2.toDouble, c0._3.toDouble, c0._4)
      case _ =>
        stops.sliding(2).collectFirst {
          case List((ea, ca), (eb, cb)) if e <= eb =>
            val t = if eb == ea then 1.0 else (e - ea) / (eb - ea)
            (ca._1 + (cb._1 - ca._1) * t, ca._2 + (cb._2 - ca._2) * t, ca._3 + (cb._3 - ca._3) * t, ca._4 + (cb._4 - ca._4) * t)
        }.getOrElse { val c = stops.last._2; (c._1.toDouble, c._2.toDouble, c._3.toDouble, c._4) }

  /** A colour as a canvas states it (`#rrggbb`, `#rgb`, `#rrggbbaa`, `rgb(r, g, b)`, `rgba(r, g, b, a)`) →
    * (r, g, b, a 0–1). Pure. */
  def parseColour(c: String): Option[(Int, Int, Int, Double)] =
    val v = c.trim.toLowerCase
    def hex(h: String) = Integer.parseInt(h, 16)
    if v.startsWith("#") && v.length == 7 then Some((hex(v.slice(1, 3)), hex(v.slice(3, 5)), hex(v.slice(5, 7)), 1.0))
    else if v.startsWith("#") && v.length == 9 then Some((hex(v.slice(1, 3)), hex(v.slice(3, 5)), hex(v.slice(5, 7)), hex(v.slice(7, 9)) / 255.0))
    else if v.startsWith("#") && v.length == 4 then Some((hex(v.slice(1, 2) * 2), hex(v.slice(2, 3) * 2), hex(v.slice(3, 4) * 2), 1.0))
    else if v.startsWith("rgb") then
      "[-0-9.]+".r.findAllIn(v.dropWhile(_ != '(')).map(_.toDouble).toList match
        case r :: g :: b :: Nil      => Some((r.toInt, g.toInt, b.toInt, 1.0))
        case r :: g :: b :: a :: Nil => Some((r.toInt, g.toInt, b.toInt, a))
        case _                       => None
    else None

  /** A style's tint stops with their colours resolved (`rgba` turns a colour into r, g, b, a). */
  def stops(tint: List[TintStop], rgba: String => Option[(Int, Int, Int, Double)]): List[(Double, (Int, Int, Int, Double))] =
    tint.flatMap(t => rgba(t.color).map(t.elevation -> _))

  /** Tint a grid `g` ((r + 2)², one margin pixel) into `out` (r² RGBA): the land through `lut`, the sea
    * (where `sea` > ½; none without a mask) through `seaLut` — either left transparent when it is `None`. */
  def tint(g: Float32Array, r: Int, lut: Option[Lut], out: Uint8ClampedArray, sea: Float32Array = null,
      seaLut: Option[Lut] = None): Unit =
    val w = r + 2
    var y = 0
    while y < r do
      var x = 0
      while x < r do
        val i = (y + 1) * w + x + 1
        val l = if sea != null && sea(i) > 0.5 then seaLut else lut
        val o = (y * r + x) * 4
        l match
          case Some(t) =>
            val k = t.index(g(i)) * 4
            out(o) = t.rgba(k); out(o + 1) = t.rgba(k + 1); out(o + 2) = t.rgba(k + 2); out(o + 3) = t.rgba(k + 3)
          case None => out(o + 3) = 0
        x += 1
      y += 1

  /** Is a relief elevation the sea (the land's elevation tiles have it flat at 0 or −1)? Pure. */
  inline def seaLevel(e: Double): Boolean = e <= 0 && e >= -1

  /** Bilinear sample of a 256-pixel tile `t` at (x, y) (pixel centres at +½), clamped to it. Pure. */
  def bilinear(t: Int16Array, side: Int, x: Double, y: Double): Double =
    val fx = math.max(0.0, math.min(side - 1.0, x)); val fy = math.max(0.0, math.min(side - 1.0, y))
    val x0 = math.min(side - 2, fx.toInt).max(0); val y0 = math.min(side - 2, fy.toInt).max(0)
    val tx = fx - x0; val ty = fy - y0
    val a = t(y0 * side + x0); val b = t(y0 * side + x0 + 1); val c = t((y0 + 1) * side + x0); val d = t((y0 + 1) * side + x0 + 1)
    (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

  /** Where a map tile of zoom `z` (512-pixel grid: column `x`, row `y`) takes its elevation from when the
    * data stops at `dataMax`: the data tile (zoom, column, row) and the part of it the map tile covers —
    * its corner (ox, oy) and side `span` in the data tile's pixels: the whole tile at the data's own
    * zooms, a quarter of it a zoom deeper, and so on. Pure. */
  final case class Source(z: Int, x: Long, y: Long, ox: Double, oy: Double, span: Double):
    /** The integer window holding the part with `pad` pixels around it: its corner and side. */
    def window(pad: Int): (Int, Int, Int) =
      val x0 = math.floor(ox).toInt - pad
      val y0 = math.floor(oy).toInt - pad
      (x0, y0, math.ceil(span).toInt + 2 * pad + 1)

  def source(z: Int, x: Long, y: Long, dataMax: Int): Source =
    val d = math.max(0, math.min(z - 1, dataMax))
    val k = z - 1 - d
    val span = Tile.toDouble / (1L << k)
    val mask = (1L << k) - 1
    Source(d, x >> k, y >> k, (x & mask) * span, (y & mask) * span, span)
