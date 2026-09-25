package com.histmersion.core

/** Presentation constants shared by the server's HTML pages and the Scala.js web UI, so a
  * confidence badge on a source page and a dot on the map cannot drift apart. Pure strings — no DOM,
  * no server types. */
object ConfidenceStyle:

  /** The map's base dot colour (viridis at its midpoint); lower confidence desaturates towards grey. */
  val Base: (Int, Int, Int) = (33, 145, 140)

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

  /** `rgb(r,g,b)` desaturated towards its luminance grey by `saturation(c)`. Anything that is not an
    * `rgb(…)` string comes back unchanged. */
  def desaturate(rgb: String, c: Confidence): String =
    val s = saturation(c)
    "\\d+".r.findAllIn(rgb).take(3).map(_.toInt).toList match
      case r :: g :: b :: Nil if s < 1.0 =>
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
        def mix(v: Int) = (grey + (v - grey) * s).round.toInt
        s"rgb(${mix(r)},${mix(g)},${mix(b)})"
      case _ => rgb

  /** The badge colour of one confidence level: the map's base colour at that saturation. */
  def color(c: Confidence): String =
    val (r, g, b) = Base
    desaturate(s"rgb($r,$g,$b)", c)

  /** A 10px inline SVG drawn with `currentColor`: a full disc for high, a three-quarter disc for
    * medium, a half disc for low, a ring for contested — readable without the colour too. */
  def icon(c: Confidence): String =
    val shape = c match
      case Confidence.High      => """<circle cx="5" cy="5" r="4.5" fill="currentColor"/>"""
      case Confidence.Medium    =>
        """<circle cx="5" cy="5" r="4" fill="none" stroke="currentColor"/>""" +
          """<path d="M5 5V1A4 4 0 1 1 1 5Z" fill="currentColor"/>"""
      case Confidence.Low       =>
        """<circle cx="5" cy="5" r="4" fill="none" stroke="currentColor"/>""" +
          """<path d="M5 1A4 4 0 0 1 5 9Z" fill="currentColor"/>"""
      case Confidence.Contested => """<circle cx="5" cy="5" r="4" fill="none" stroke="currentColor" stroke-width="1.5"/>"""
    s"""<svg class="conf-icon" width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">$shape</svg>"""

  /** The human label: `high confidence`. */
  def label(c: Confidence): String = s"${c.toString.toLowerCase} confidence"
