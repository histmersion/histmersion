package com.histmersion.api

import com.histmersion.api.library.Confidence

object ConfidenceStyle:

  type SaturationResult = SaturationResult.T
  object SaturationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RankResult = RankResult.T
  object RankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DesaturateResult = DesaturateResult.T
  object DesaturateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DesaturateRgb = DesaturateRgb.T
  object DesaturateRgb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IconResult = IconResult.T
  object IconResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def saturation(c: Confidence): com.histmersion.api.ConfidenceStyle.SaturationResult = c match
    case Confidence.High      => 1.0
    case Confidence.Medium    => 0.7
    case Confidence.Low       => 0.4
    case Confidence.Contested => 0.3

  def rank(c: Confidence): com.histmersion.api.ConfidenceStyle.RankResult = c match
    case Confidence.Contested => 0
    case Confidence.Low       => 1
    case Confidence.Medium    => 2
    case Confidence.High      => 3

  def desaturate(rgb: com.histmersion.api.ConfidenceStyle.DesaturateRgb, c: Confidence): com.histmersion.api.ConfidenceStyle.DesaturateResult =
    val s = saturation(c)
    "\\d+".r.findAllIn(rgb).take(3).map(_.toInt).toList match
      case r :: g :: b :: Nil if s < 1.0 =>
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
        def mix(v: Int) = (grey + (v - grey) * s).round.toInt
        s"rgb(${mix(r)},${mix(g)},${mix(b)})"
      case _ => rgb

  final case class IconPart(d: IconPart.D, filled: IconPart.Filled)

  object IconPart:
    type D = D.T
    object D extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Filled = Filled.T
    object Filled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Ring = IconPart("M5 1a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", filled = false)

  def iconParts(c: Confidence): List[IconPart] = c match
    case Confidence.High      => List(IconPart("M5 0.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z", filled = true))
    case Confidence.Medium    => List(Ring, IconPart("M5 5V1A4 4 0 1 1 1 5Z", filled = true))
    case Confidence.Low       => List(Ring, IconPart("M5 1A4 4 0 0 1 5 9Z", filled = true))
    case Confidence.Contested => List(Ring)

  val IconViewBox = "0 0 10 10"

  def icon(c: Confidence): com.histmersion.api.ConfidenceStyle.IconResult =
    val parts = iconParts(c)
      .map(p =>
        if p.filled then s"""<path d="${p.d}" fill="currentColor"/>"""
        else s"""<path d="${p.d}" fill="none" stroke="currentColor"/>"""
      )
      .mkString
    s"""<svg class="conf-icon" width="10" height="10" viewBox="$IconViewBox" aria-hidden="true">$parts</svg>"""

  def label(c: Confidence): com.histmersion.api.ConfidenceStyle.LabelResult = s"${c.toString.toLowerCase} confidence"
