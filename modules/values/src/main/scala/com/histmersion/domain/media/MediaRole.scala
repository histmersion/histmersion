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

  /** A book's cover (a source's picture). */
  val Cover = "cover"

  /** One of a paper's own figures, chosen to illustrate it (the domain's `FigurePick`). */
  val Illustration = "illustration"

  /** A picture that depicts an event (Commons' `P180 depicts`, Wikidata's `P1299 depicted by`). */
  val Depiction = "depiction"

  /** A museum object found at (or made in) a place, of the place's period. */
  val Object = "object"

  /** A historical map that shows a place or region (a shape reference; not georeferenced). */
  val Map = "map"

  /** The roles a place's or event's picture can have — what `media.wikidata.*-properties` may name. */
  val placeRoles: List[Wire] = List(Wire(Image), Wire(AerialView), Wire(Plan), Wire(LocatorMap), Wire(RouteMap))

  /** Every role, in card order: a place's own picture before an object found there and a map of it; a source shows its
    * cover before an illustration.
    */
  val all: List[Wire] = placeRoles ++ List(Wire(Depiction), Wire(Object), Wire(Map), Wire(Cover), Wire(Illustration))

  /** Pure: the rank of a role on a card (unknown roles last). */
  def order(role: Wire): Int = all.indexOf(role) match
    case -1 => (all.size)
    case i  => (i)
