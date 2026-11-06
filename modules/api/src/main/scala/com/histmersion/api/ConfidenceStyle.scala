package com.histmersion.api

import com.histmersion.api.library.Confidence

object ConfidenceStyle:
  type Rank = Rank.T
  object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rgb = Rgb.T
  object Rgb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Saturation = Saturation.T
  object Saturation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]



  def saturation(c: Confidence): com.histmersion.api.ConfidenceStyle.Saturation = c match
    case Confidence.High      => _root_.com.histmersion.api.ConfidenceStyle.Saturation(1.0)
    case Confidence.Medium    => _root_.com.histmersion.api.ConfidenceStyle.Saturation(0.7)
    case Confidence.Low       => _root_.com.histmersion.api.ConfidenceStyle.Saturation(0.4)
    case Confidence.Contested => _root_.com.histmersion.api.ConfidenceStyle.Saturation(0.3)

  def rank(c: Confidence): com.histmersion.api.ConfidenceStyle.Rank = c match
    case Confidence.Contested => _root_.com.histmersion.api.ConfidenceStyle.Rank(0)
    case Confidence.Low       => _root_.com.histmersion.api.ConfidenceStyle.Rank(1)
    case Confidence.Medium    => _root_.com.histmersion.api.ConfidenceStyle.Rank(2)
    case Confidence.High      => _root_.com.histmersion.api.ConfidenceStyle.Rank(3)

  def desaturate(rgb: com.histmersion.api.ConfidenceStyle.Rgb, c: Confidence): com.histmersion.api.ConfidenceStyle.Rgb =
    val s = saturation(c)
    "\\d+".r.findAllIn((rgb).value).take(3).map(_.toInt).toList match
      case r :: g :: b :: Nil if (s).value < 1.0 =>
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
        def mix(v: Int) = (grey + ((v) - grey) * s).round.toInt
        s"rgb(${mix((r))},${mix((g))},${mix((b))})"
      case _ => _root_.com.histmersion.api.ConfidenceStyle.Rgb((rgb).value)

  final case class IconPart(d: IconPart.D, filled: IconPart.Filled)

  object IconPart:
    type D = D.T
    object D extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Filled = Filled.T
    object Filled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Ring = IconPart(_root_.com.histmersion.api.ConfidenceStyle.IconPart.D("M5 1a4 4 0 1 1 0 8a4 4 0 1 1 0-8z"), filled = _root_.com.histmersion.api.ConfidenceStyle.IconPart.Filled(false))

  def iconParts(c: Confidence): List[IconPart] = c match
    case Confidence.High      => List(IconPart(_root_.com.histmersion.api.ConfidenceStyle.IconPart.D("M5 0.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z"), filled = _root_.com.histmersion.api.ConfidenceStyle.IconPart.Filled(true)))
    case Confidence.Medium    => List(Ring, IconPart(_root_.com.histmersion.api.ConfidenceStyle.IconPart.D("M5 5V1A4 4 0 1 1 1 5Z"), filled = _root_.com.histmersion.api.ConfidenceStyle.IconPart.Filled(true)))
    case Confidence.Low       => List(Ring, IconPart(_root_.com.histmersion.api.ConfidenceStyle.IconPart.D("M5 1A4 4 0 0 1 5 9Z"), filled = _root_.com.histmersion.api.ConfidenceStyle.IconPart.Filled(true)))
    case Confidence.Contested => List(Ring)

  val IconViewBox = "0 0 10 10"

  def icon(c: Confidence): String =
    val parts = iconParts(c)
      .map(p =>
        if ((p.filled) == _root_.com.histmersion.api.ConfidenceStyle.IconPart.Filled(true)) then s"""<path d="${p.d}" fill="currentColor"/>"""
        else s"""<path d="${p.d}" fill="none" stroke="currentColor"/>"""
      )
      .mkString
    (s"""<svg class="conf-icon" width="10" height="10" viewBox="$IconViewBox" aria-hidden="true">$parts</svg>""")

  def label(c: Confidence): String = (s"${c.toString.toLowerCase} confidence")
