package com.histmersion.api

import com.histmersion.values.Confidence

/** Presentation constants shared by the server's HTML pages and the Scala.js web UI, so a confidence badge on a source
  * page and a dot on the map cannot drift apart. Pure strings — no DOM, no server types.
  */
object ConfidenceStyle:

  /** How much colour survives at each confidence — the same levels the map dots and legend use. */
  def saturation(c: Confidence): Double = c match
    case Confidence.High      => 1.0
    case Confidence.Medium    => 0.7
    case Confidence.Low       => 0.4
    case Confidence.Contested => 0.3

  /** Weakest first: `contested < low < medium < high`, for "the worst confidence in this cluster". */
  def rank(c: Confidence): Int = c match
    case Confidence.Contested => 0
    case Confidence.Low       => 1
    case Confidence.Medium    => 2
    case Confidence.High      => 3

  /** `rgb(r,g,b)` desaturated towards its luminance grey by `saturation(c)`. Anything that is not an `rgb(…)` string
    * comes back unchanged.
    */
  def desaturate(rgb: String, c: Confidence): String =
    val s = saturation(c)
    "\\d+".r.findAllIn(rgb).take(3).map(_.toInt).toList match
      case r :: g :: b :: Nil if s < 1.0 =>
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
        def mix(v: Int) = (grey + (v - grey) * s).round.toInt
        s"rgb(${mix(r)},${mix(g)},${mix(b)})"
      case _ => rgb

  /** One part of the confidence icon: a filled path, or an outline. Data, so the server (as an SVG string) and the web
    * UI (as Laminar SVG elements) draw the same geometry.
    */
  final case class IconPart(d: String, filled: Boolean)

  private val Ring = IconPart("M5 1a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", filled = false)

  /** A full disc for high, three quarters for medium, a half for low, an empty ring for contested — readable without
    * the colour too. 10×10, drawn with `currentColor`.
    */
  def iconParts(c: Confidence): List[IconPart] = c match
    case Confidence.High      => List(IconPart("M5 0.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z", filled = true))
    case Confidence.Medium    => List(Ring, IconPart("M5 5V1A4 4 0 1 1 1 5Z", filled = true))
    case Confidence.Low       => List(Ring, IconPart("M5 1A4 4 0 0 1 5 9Z", filled = true))
    case Confidence.Contested => List(Ring)

  val IconViewBox = "0 0 10 10"

  /** The icon as an inline SVG string. */
  def icon(c: Confidence): String =
    val parts = iconParts(c)
      .map(p =>
        if p.filled then s"""<path d="${p.d}" fill="currentColor"/>"""
        else s"""<path d="${p.d}" fill="none" stroke="currentColor"/>"""
      )
      .mkString
    s"""<svg class="conf-icon" width="10" height="10" viewBox="$IconViewBox" aria-hidden="true">$parts</svg>"""

  /** The human label: `high confidence`. */
  def label(c: Confidence): String = s"${c.toString.toLowerCase} confidence"
