package com.histmersion.web

import scala.scalajs.js.typedarray.{Float32Array, Uint8ClampedArray}

/** The terrain base map's relief, computed in the page from elevation tiles (Mapterhorn's
  * terrarium-encoded WebP, docs/research-terrain-basemap.md): Horn's slope and aspect over a 3×3
  * window, lit from the north-west, drawn as translucent shadow on the slopes facing away from the
  * light and a fainter highlight on those facing it. Flat ground (and the sea) stays transparent, so
  * the vector layer's land, water and land cover show through unchanged.
  *
  * Pure: pixels in, pixels out. ~2–8 ms for a 512-pixel tile, off the interaction path (a tile
  * arrives, it is shaded once, it is drawn). */
object Hillshade:

  /** The light and how strongly it shades. `exaggeration` multiplies the slopes; [[exaggeration]]
    * gives the zoom's. */
  final case class Light(azimuthDeg: Double = 315, altitudeDeg: Double = 45)

  /** An RGB colour and the most opacity (0–255) it is drawn with. */
  final case class Tint(r: Int, g: Int, b: Int, maxAlpha: Int)

  /** Terrarium encoding: metres = R·256 + G + B/256 − 32768. */
  def elevation(r: Int, g: Int, b: Int): Double = r * 256.0 + g + b / 256.0 - 32768.0

  /** Ground metres per pixel of a `size`-pixel tile in row `y` of zoom `z` (at the row's centre). */
  def metresPerPixel(z: Int, y: Double, size: Int): Double =
    val n   = math.pow(2, z)
    val lat = math.atan(math.sinh(math.Pi * (1 - 2 * (y + 0.5) / n)))
    40075016.686 * math.cos(lat) / (n * size)

  /** How much the slopes are steepened at zoom `z`: relief is invisible at a continent's scale
    * without it (a pixel there is kilometres wide), and needs none close up. */
  def exaggeration(z: Int): Double = 1.0 + math.max(0, 11 - z) * 0.6

  /** The light falling on one cell (0 dark … 1 facing the sun) from its 3×3 neighbourhood,
    * `a b c / d · f / g h i`, with cells `mpp` metres apart. */
  def illumination(a: Double, b: Double, c: Double, d: Double, f: Double, g: Double, h: Double, i: Double,
      mpp: Double, ex: Double, light: Light): Double =
    val dzdx   = ((c + 2 * f + i) - (a + 2 * d + g)) / (8 * mpp) * ex
    val dzdy   = ((g + 2 * h + i) - (a + 2 * b + c)) / (8 * mpp) * ex
    val slope  = math.atan(math.sqrt(dzdx * dzdx + dzdy * dzdy))
    val aspect = math.atan2(dzdy, -dzdx)
    val zen    = math.toRadians(90 - light.altitudeDeg)
    val az     = math.toRadians(360 - light.azimuthDeg + 90)
    math.max(0.0, math.cos(zen) * math.cos(slope) + math.sin(zen) * math.sin(slope) * math.cos(az - aspect))

  /** Shade a `w`×`h` terrarium tile (`rgba`, 4 bytes a pixel) into `out` (the same size): flat
    * ground transparent, slopes away from the light `shadow`, slopes towards it `highlight`. Edge
    * pixels use their nearest neighbours inside the tile. */
  def shade(rgba: Uint8ClampedArray, w: Int, h: Int, mpp: Double, ex: Double, light: Light,
      shadow: Tint, highlight: Tint, out: Uint8ClampedArray): Unit =
    val n  = w * h
    val el = new Float32Array(n)
    // `var`s and while loops: 262 144 pixels a tile, in the page's own thread
    var p = 0
    while p < n do
      val o = p * 4
      el(p) = elevation(rgba(o), rgba(o + 1), rgba(o + 2)).toFloat
      p += 1
    val flat = math.cos(math.toRadians(90 - light.altitudeDeg))
    var y = 0
    while y < h do
      val y0 = if y > 0 then y - 1 else y
      val y1 = if y < h - 1 then y + 1 else y
      var x = 0
      while x < w do
        val x0 = if x > 0 then x - 1 else x
        val x1 = if x < w - 1 then x + 1 else x
        val s = illumination(el(y0 * w + x0), el(y0 * w + x), el(y0 * w + x1), el(y * w + x0), el(y * w + x1),
          el(y1 * w + x0), el(y1 * w + x), el(y1 * w + x1), mpp, ex, light)
        val o = (y * w + x) * 4
        if s < flat then
          out(o) = shadow.r; out(o + 1) = shadow.g; out(o + 2) = shadow.b
          out(o + 3) = math.round((flat - s) / flat * shadow.maxAlpha).toInt
        else
          out(o) = highlight.r; out(o + 1) = highlight.g; out(o + 2) = highlight.b
          out(o + 3) = math.round((s - flat) / (1 - flat) * highlight.maxAlpha).toInt
        x += 1
      y += 1
