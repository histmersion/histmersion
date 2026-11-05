package com.histmersion.web

import com.histmersion.api.{ConfidenceStyle, YearFmt, Years}
import com.histmersion.api.library.Label
import com.histmersion.web.ui.ThemeTokens
import scala.math.*
import com.histmersion.api.library.Confidence

object Fmt:

  /** Aliases of the ONE year formatter ([[YearFmt]]), kept for the many views that call them. */
  type YrResult = YrResult.T
  object YrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YrY = YrY.T
  object YrY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStrResult = PeriodStrResult.T
  object PeriodStrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStrA = PeriodStrA.T
  object PeriodStrA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStrB = PeriodStrB.T
  object PeriodStrB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorResult = ColorResult.T
  object ColorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorT = ColorT.T
  object ColorT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorOnResult = ColorOnResult.T
  object ColorOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorOnStopsPart1 = ColorOnStopsPart1.T
  object ColorOnStopsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorOnStopsPart2 = ColorOnStopsPart2.T
  object ColorOnStopsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorOnStopsPart3 = ColorOnStopsPart3.T
  object ColorOnStopsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColorOnT = ColorOnT.T
  object ColorOnT extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseYearResult = ParseYearResult.T
  object ParseYearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseYearRaw = ParseYearRaw.T
  object ParseYearRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DesaturateResult = DesaturateResult.T
  object DesaturateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DesaturateRgbStr = DesaturateRgbStr.T
  object DesaturateRgbStr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayResult = DisplayResult.T
  object DisplayResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayS = DisplayS.T
  object DisplayS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayTagResult = DisplayTagResult.T
  object DisplayTagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayTagS = DisplayTagS.T
  object DisplayTagS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscResult = EscResult.T
  object EscResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipResult = ClipResult.T
  object ClipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipText = ClipText.T
  object ClipText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClipMaxLen = ClipMaxLen.T
  object ClipMaxLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def yr(y: com.histmersion.web.Fmt.YrY): com.histmersion.web.Fmt.YrResult = YearFmt(y)

  def yr(y: Option[com.histmersion.web.Fmt.YrY]): com.histmersion.web.Fmt.YrResult = YearFmt(y)

  def periodStr(a: Option[com.histmersion.web.Fmt.PeriodStrA], b: Option[com.histmersion.web.Fmt.PeriodStrB]): com.histmersion.web.Fmt.PeriodStrResult = YearFmt.period(a, b)

  def periodStr(a: com.histmersion.web.Fmt.PeriodStrA, b: com.histmersion.web.Fmt.PeriodStrB): com.histmersion.web.Fmt.PeriodStrResult = periodStr(Some(a), Some(b))

  /** The period ramp at `t` ∈ [0,1], interpolated between the stylesheet's `--ramp-*` stops. */
  def color(t: com.histmersion.web.Fmt.ColorT): com.histmersion.web.Fmt.ColorResult = colorOn(ThemeTokens.ramp, t)

  /** Pure: `t` on a ramp of RGB stops, as `rgb(r,g,b)`; `""` on an empty ramp (no stylesheet). */
  def colorOn(stops: Vector[(com.histmersion.web.Fmt.ColorOnStopsPart1, com.histmersion.web.Fmt.ColorOnStopsPart2, com.histmersion.web.Fmt.ColorOnStopsPart3)], t: com.histmersion.web.Fmt.ColorOnT): com.histmersion.web.Fmt.ColorOnResult =
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
  def parseYear(raw: com.histmersion.web.Fmt.ParseYearRaw): Option[com.histmersion.web.Fmt.ParseYearResult] = Years.parse(raw).toOption

  /** Delegates to the shared [[ConfidenceStyle]], so map dots and page badges use one palette. */
  def desaturate(rgbStr: com.histmersion.web.Fmt.DesaturateRgbStr, confidence: Confidence): com.histmersion.web.Fmt.DesaturateResult =
    ConfidenceStyle.desaturate(rgbStr, confidence)

  def worstConf(items: Iterable[Confidence]): Confidence =
    items.foldLeft(Confidence.High)((w, c) => if ConfidenceStyle.rank(c) < ConfidenceStyle.rank(w) then c else w)

  def display(s: com.histmersion.web.Fmt.DisplayS): com.histmersion.web.Fmt.DisplayResult = Label.display(s)
  def displayTag(s: com.histmersion.web.Fmt.DisplayTagS): com.histmersion.web.Fmt.DisplayTagResult = display(s)

  def esc(s: com.histmersion.web.Fmt.EscS): com.histmersion.web.Fmt.EscResult =
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

  def clip(text: com.histmersion.web.Fmt.ClipText, maxLen: com.histmersion.web.Fmt.ClipMaxLen): com.histmersion.web.Fmt.ClipResult =
    val s = text.trim
    if s.length <= maxLen then s
    else
      val cut = s.substring(0, maxLen)
      val sp = cut.lastIndexOf(' ')
      val base = if sp > maxLen * 0.6 then cut.substring(0, sp) else cut
      base.replaceAll("[\\s,;:.\\u2014-]+$", "") + "…"
