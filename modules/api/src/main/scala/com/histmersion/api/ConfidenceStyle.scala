package com.histmersion
package api

import com.histmersion.api.library.Confidence
import io.github.iltotore.iron.constraint.any.Pure

object ConfidenceStyle:
  type Rank = Rank.T
  object Rank extends Newtype[Int, Pure]
  type Rgb = Rgb.T
  object Rgb extends Newtype[String, Pure]
  type Saturation = Saturation.T
  object Saturation extends Newtype[Double, Pure]



  def saturation(c: Confidence): Saturation = c match
    case Confidence.High      => Saturation(1.0)
    case Confidence.Medium    => Saturation(0.7)
    case Confidence.Low       => Saturation(0.4)
    case Confidence.Contested => Saturation(0.3)

  def rank(c: Confidence): Rank = c match
    case Confidence.Contested => Rank(0)
    case Confidence.Low       => Rank(1)
    case Confidence.Medium    => Rank(2)
    case Confidence.High      => Rank(3)

  def desaturate(rgb: Rgb, c: Confidence): Rgb =
    val s = saturation(c)
    "\\d+".r.findAllIn((rgb).value).take(3).map(_.toInt).toList match
      case r :: g :: b :: Nil if (s).value < 1.0 =>
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
        def mix(v: Int) = (grey + ((v) - grey) * s.value).round.toInt
        Rgb(s"rgb(${mix((r))},${mix((g))},${mix((b))})")
      case _ => Rgb((rgb).value)

  final case class IconPart(d: IconPart.D, filled: IconPart.Filled)

  object IconPart:
    type D = D.T
    object D extends Newtype[String, Pure]
    type Filled = Filled.T
    object Filled extends Newtype[Boolean, Pure]

  private val Ring = IconPart(IconPart.D("M5 1a4 4 0 1 1 0 8a4 4 0 1 1 0-8z"), filled = IconPart.Filled(false))

  def iconParts(c: Confidence): List[IconPart] = c match
    case Confidence.High      => List(IconPart(IconPart.D("M5 0.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z"), filled = IconPart.Filled(true)))
    case Confidence.Medium    => List(Ring, IconPart(IconPart.D("M5 5V1A4 4 0 1 1 1 5Z"), filled = IconPart.Filled(true)))
    case Confidence.Low       => List(Ring, IconPart(IconPart.D("M5 1A4 4 0 0 1 5 9Z"), filled = IconPart.Filled(true)))
    case Confidence.Contested => List(Ring)

  val IconViewBox = "0 0 10 10"

  def icon(c: Confidence): String =
    val parts = iconParts(c)
      .map(p =>
        if ((p.filled) == IconPart.Filled(true)) then s"""<path d="${p.d}" fill="currentColor"/>"""
        else s"""<path d="${p.d}" fill="none" stroke="currentColor"/>"""
      )
      .mkString
    (s"""<svg class="conf-icon" width="10" height="10" viewBox="$IconViewBox" aria-hidden="true">$parts</svg>""")

  def label(c: Confidence): String = (s"${c.toString.toLowerCase} confidence")
