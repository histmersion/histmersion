package com.histmersion.web

import com.histmersion.api.{ConfidenceStyle, YearFmt, Years}
import com.histmersion.api.library.Label
import com.histmersion.web.ui.ThemeTokens
import scala.math.*
import com.histmersion.api.library.Confidence

object Fmt:
  type Clipped = Clipped.T
  object Clipped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Escaped = Escaped.T
  object Escaped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodText = PeriodText.T
  object PeriodText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RampPosition = RampPosition.T
  object RampPosition extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Aliases of the ONE year formatter ([[YearFmt]]), kept for the many views that call them. */

  type ColorT = _root_.com.histmersion.web.Fmt.RampPosition
  val ColorT: _root_.com.histmersion.web.Fmt.RampPosition.type = _root_.com.histmersion.web.Fmt.RampPosition
  type DisplayTagS = _root_.com.histmersion.api.UiLabels.Text
  val DisplayTagS: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text

  def yr(y: com.histmersion.api.Years.Astronomical): com.histmersion.api.Years.Text = YearFmt(y)

  def yr(y: Option[com.histmersion.api.Years.Astronomical]): com.histmersion.api.Years.Text = YearFmt(y)

  def periodStr(a: Option[com.histmersion.api.Years.Astronomical], b: Option[com.histmersion.api.Years.Astronomical]): com.histmersion.web.Fmt.PeriodText = YearFmt.period(a, b)

  def periodStr(a: com.histmersion.api.Years.Astronomical, b: com.histmersion.api.Years.Astronomical): com.histmersion.web.Fmt.PeriodText = periodStr(Some(a), Some(b))

  /** The period ramp at `t` ∈ [0,1], interpolated between the stylesheet's `--ramp-*` stops. */
  def color(t: com.histmersion.web.Fmt.ColorT): com.histmersion.api.Wcag.Hex = colorOn(ThemeTokens.ramp, t)

  /** Pure: `t` on a ramp of RGB stops, as `rgb(r,g,b)`; `""` on an empty ramp (no stylesheet). */
  def colorOn(stops: Vector[(com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel)], t: com.histmersion.web.Fmt.RampPosition): com.histmersion.api.Wcag.Hex =
    if stops.isEmpty then com.histmersion.api.Wcag.Hex("")
    else
      val ct = max(0.0, min(1.0, t))
      val f = ct * (stops.length - 1)
      val i = f.toInt
      val g = f - i
      val a = stops(i)
      val b = stops(min(i + 1, stops.length - 1))
      def lerp(av: com.histmersion.api.Wcag.Channel, bv: com.histmersion.api.Wcag.Channel) = (av + (bv - av) * g).round.toInt
      s"rgb(${lerp(a._1, b._1)},${lerp(a._2, b._2)},${lerp(a._3, b._3)})"

  /** A year a person typed ([[Years.parse]]); `None` when it is not one. */
  def parseYear(raw: com.histmersion.api.Years.Text): Option[com.histmersion.api.Years.Astronomical] = Years.parse(raw).toOption

  /** Delegates to the shared [[ConfidenceStyle]], so map dots and page badges use one palette. */
  def desaturate(rgbStr: com.histmersion.api.Wcag.Hex, confidence: Confidence): com.histmersion.api.Wcag.Hex =
    ConfidenceStyle.desaturate(rgbStr, confidence)

  def worstConf(items: Iterable[Confidence]): Confidence =
    items.foldLeft(Confidence.High)((w, c) => if ConfidenceStyle.rank(c) < ConfidenceStyle.rank(w) then c else w)

  def display(s: com.histmersion.api.UiLabels.Text): com.histmersion.api.UiLabels.Text = Label.display(s)
  def displayTag(s: com.histmersion.web.Fmt.DisplayTagS): com.histmersion.api.UiLabels.Text = display(s)

  def esc(s: com.histmersion.web.Fmt.Text): com.histmersion.web.Fmt.Escaped =
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

  def clip(text: com.histmersion.web.Fmt.Text, maxLen: com.histmersion.api.Markdown.CharLimit): com.histmersion.web.Fmt.Clipped =
    val s = text.trim
    if s.length <= maxLen then s
    else
      val cut = s.substring(0, maxLen)
      val sp = cut.lastIndexOf(' ')
      val base = if sp > maxLen * 0.6 then cut.substring(0, sp) else cut
      base.replaceAll("[\\s,;:.\\u2014-]+$", "") + "…"
