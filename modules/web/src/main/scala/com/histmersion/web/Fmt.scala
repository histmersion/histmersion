package com.histmersion
package web

import com.histmersion.api.{ConfidenceStyle, YearFmt, Years}
import com.histmersion.api.library.Label
import com.histmersion.web.ui.ThemeTokens
import scala.math.*
import com.histmersion.api.library.Confidence
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.Wcag
import com.histmersion.api.UiLabels
import com.histmersion.api.Markdown
import com.histmersion.lint.{tolerated, LintRule}

object Fmt:
  type PeriodText = PeriodText.T
  object PeriodText extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type RampPosition = RampPosition.T
  object RampPosition extends Newtype[Double, Pure]

  /** Aliases of the ONE year formatter ([[YearFmt]]), kept for the many views that call them. */

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ColorT = RampPosition
  val ColorT: RampPosition.type = RampPosition
  export UiLabels.Text as DisplayTagS

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def yr(y: Year): Years.Text = Years.Text(YearFmt(y))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def yr(y: Option[Year]): Years.Text = Years.Text(YearFmt(y))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def periodStr(a: Option[Year], b: Option[Year]): PeriodText = PeriodText(YearFmt.period(a, b))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def periodStr(a: Year, b: Year): PeriodText = periodStr(Some(a), Some(b))

  /** The period ramp at `t` ∈ [0,1], interpolated between the stylesheet's `--ramp-*` stops. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def color(t: ColorT): Wcag.Hex = colorOn(ThemeTokens.ramp, t)

  /** Pure: `t` on a ramp of RGB stops, as `rgb(r,g,b)`; `""` on an empty ramp (no stylesheet). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def colorOn(stops: Vector[(Wcag.Channel, Wcag.Channel, Wcag.Channel)], t: RampPosition): Wcag.Hex =
    if stops.isEmpty then Wcag.Hex("")
    else
      val ct = max(0.0, min(1.0, t.value))
      val f = ct * (stops.length - 1)
      val i = f.toInt
      val g = f - i
      val a = stops(i)
      val b = stops(min(i + 1, stops.length - 1))
      def lerp(av: Wcag.Channel, bv: Wcag.Channel) = (av.value + (bv.value - av.value) * g).round.toInt
      Wcag.Hex(s"rgb(${lerp(a._1, b._1)},${lerp(a._2, b._2)},${lerp(a._3, b._3)})")

  /** A year a person typed ([[Years.parse]]); `None` when it is not one. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def parseYear(raw: Years.Text): Option[Year] = Years.parse(raw).toOption

  /** Delegates to the shared [[ConfidenceStyle]], so map dots and page badges use one palette. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def desaturate(rgbStr: Wcag.Hex, confidence: Confidence): Wcag.Hex =
    Wcag.Hex(ConfidenceStyle.desaturate(ConfidenceStyle.Rgb(rgbStr.value), confidence).value)

  def worstConf(items: Iterable[Confidence]): Confidence =
    items.foldLeft(Confidence.High)((w, c) => if ConfidenceStyle.rank(c) < ConfidenceStyle.rank(w) then c else w)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def display(s: UiLabels.Text): UiLabels.Text = UiLabels.Text(Label.display(Label.Text(s.value)).value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def displayTag(s: Fmt.DisplayTagS): UiLabels.Text = display(s)

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def clip(text: String, maxLen: Markdown.CharLimit): String =
    val s = text.trim
    val limit = maxLen.value
    if s.length <= limit then s
    else
      val cut = s.substring(0, limit)
      val sp = cut.lastIndexOf(' ')
      val base = if sp > limit * 0.6 then cut.substring(0, sp) else cut
      base.replaceAll("[\\s,;:.\\u2014-]+$", "") + "…"
