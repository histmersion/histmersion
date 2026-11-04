package com.histmersion.web

import com.histmersion.api.{ConfidenceStyle, YearFmt, Years}
import com.histmersion.api.library.Label
import com.histmersion.web.ui.ThemeTokens
import scala.math.*
import com.histmersion.api.library.Confidence

object Fmt:

  /** Aliases of the ONE year formatter ([[YearFmt]]), kept for the many views that call them. */
  def yr(y: Long): String = YearFmt(y)

  def yr(y: Option[Long]): String = YearFmt(y)

  def periodStr(a: Option[Long], b: Option[Long]): String = YearFmt.period(a, b)

  def periodStr(a: Long, b: Long): String = periodStr(Some(a), Some(b))

  /** The period ramp at `t` ∈ [0,1], interpolated between the stylesheet's `--ramp-*` stops. */
  def color(t: Double): String = colorOn(ThemeTokens.ramp, t)

  /** Pure: `t` on a ramp of RGB stops, as `rgb(r,g,b)`; `""` on an empty ramp (no stylesheet). */
  def colorOn(stops: Vector[(Int, Int, Int)], t: Double): String =
    if stops.isEmpty then ""
    else
      val ct = max(0.0, min(1.0, t))
      val f = ct * (stops.length - 1)
      val i = f.toInt
      val g = f - i
      val a = stops(i)
      val b = stops(min(i + 1, stops.length - 1))
      def lerp(av: Int, bv: Int) = (av + (bv - av) * g).round.toInt
      s"rgb(${lerp(a._1, b._1)},${lerp(a._2, b._2)},${lerp(a._3, b._3)})"

  /** A year a person typed ([[Years.parse]]); `None` when it is not one. */
  def parseYear(raw: String): Option[Long] = Years.parse(raw).toOption

  /** Delegates to the shared [[ConfidenceStyle]], so map dots and page badges use one palette. */
  def desaturate(rgbStr: String, confidence: Confidence): String =
    ConfidenceStyle.desaturate(rgbStr, confidence)

  def worstConf(items: Iterable[Confidence]): Confidence =
    items.foldLeft(Confidence.High)((w, c) => if ConfidenceStyle.rank(c) < ConfidenceStyle.rank(w) then c else w)

  def display(s: String): String = Label.display(s)
  def displayTag(s: String): String = display(s)

  def esc(s: String): String =
    val sb = new StringBuilder(s.length)
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&#39;")
        case c    => sb.append(c)
      i += 1
    sb.toString

  def clip(text: String, maxLen: Int): String =
    val s = text.trim
    if s.length <= maxLen then s
    else
      val cut = s.substring(0, maxLen)
      val sp = cut.lastIndexOf(' ')
      val base = if sp > maxLen * 0.6 then cut.substring(0, sp) else cut
      base.replaceAll("[\\s,;:.\\u2014-]+$", "") + "…"
