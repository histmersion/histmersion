package com.histmersion
package domain.media

import io.github.iltotore.iron.constraint.any.Pure
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

  val placeRoles: List[Wire] = List(Wire(Image), Wire(AerialView), Wire(Plan), Wire(LocatorMap), Wire(RouteMap))

  val all: List[Wire] = placeRoles ++ List(Wire(Depiction), Wire(Object), Wire(Map), Wire(Cover), Wire(Illustration))

  def order(role: Wire): Int = all.indexOf(role) match
    case -1 => (all.size)
    case i  => (i)
