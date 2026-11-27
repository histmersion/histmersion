package com.histmersion
package domain.media

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
object MediaRole:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Image = "image"
  val LocatorMap = "locator-map"
  val Plan = "plan"
  val AerialView = "aerial-view"
  val RouteMap = "route-map"

  val Cover = "cover"

  val Illustration = "illustration"

  val Depiction = "depiction"

  val Object = "object"

  val Map = "map"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val placeRoles: List[Wire] = List(Wire(Image), Wire(AerialView), Wire(Plan), Wire(LocatorMap), Wire(RouteMap))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Wire] = placeRoles ++ List(Wire(Depiction), Wire(Object), Wire(Map), Wire(Cover), Wire(Illustration))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def order(role: Wire): Int = all.indexOf(role) match
    case -1 => (all.size)
    case i  => (i)
