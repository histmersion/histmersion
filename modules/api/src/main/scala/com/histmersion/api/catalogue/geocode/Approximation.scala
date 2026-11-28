package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Geocode.Compass
import com.histmersion.api.catalogue.Geocode.key

enum Approximation:
  case Qualifier(dropped: Qualifier.Dropped)

  case OnlyWithin(missing: OnlyWithin.Missing)

  case Rough(places: Rough.Places)

  case GridSquare

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String = this match
    case Qualifier(d)  => (s"'$d' dropped")
    case OnlyWithin(m) => (s"'$m' not found there; the enclosing place's point")
    case Rough(n)      => (s"a placeholder point shared by $n places")
    case GridSquare    => ("the centre of an atlas grid square")

object Approximation:
  object Qualifier:
    type Dropped = Dropped.T
    object Dropped extends Newtype[String, Pure]

  object OnlyWithin:
    type Missing = Missing.T
    object Missing extends Newtype[String, Pure]

  object Rough:
    type Places = Places.T
    object Places extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def compassBox(
      dropped: Qualifier.Dropped
  ): Option[(Compass.Fraction, Compass.Fraction, Compass.Fraction, Compass.Fraction)] =
    val words = dropped.value.split("[\\s\\-]+").map(w => key(w).value).filter(_.nonEmpty).toList
    val dirs = words.flatMap(w => Boxes.get(w.stripSuffix("ern")).orElse(Boxes.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def box(x0: Double, y0: Double, x1: Double, y1: Double) =
    (Compass.Fraction(x0), Compass.Fraction(y0), Compass.Fraction(x1), Compass.Fraction(y1))

  private val Boxes: Map[String, (Compass.Fraction, Compass.Fraction, Compass.Fraction, Compass.Fraction)] = Map(
    "north" -> box(0.0, 0.5, 1.0, 1.0),
    "south" -> box(0.0, 0.0, 1.0, 0.5),
    "east" -> box(0.5, 0.0, 1.0, 1.0),
    "west" -> box(0.0, 0.0, 0.5, 1.0),
    "northeast" -> box(0.5, 0.5, 1.0, 1.0),
    "northwest" -> box(0.0, 0.5, 0.5, 1.0),
    "southeast" -> box(0.5, 0.0, 1.0, 0.5),
    "southwest" -> box(0.0, 0.0, 0.5, 0.5),
    "central" -> box(0.25, 0.25, 0.75, 0.75),
    "mid" -> box(0.25, 0.25, 0.75, 0.75),
    "middle" -> box(0.25, 0.25, 0.75, 0.75)
  )
