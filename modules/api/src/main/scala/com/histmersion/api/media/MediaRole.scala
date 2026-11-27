package com.histmersion
package api.media

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
object MediaRole:
  type Role = Role.T
  object Role extends Newtype[String, Pure]

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val placeRoles: List[Role] = List(Role(Image), Role(AerialView), Role(Plan), Role(LocatorMap), Role(RouteMap))

  /** Every role, in card order: a place's own picture before an object found there and a map of it; a source shows its
    * cover before an illustration.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Role] = placeRoles ++ List(Depiction, Object, Map, Cover, Illustration).map(Role(_))

  /** Pure: the rank of a role on a card (unknown roles last). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def order(role: Role): Int = all.indexOf(role) match
    case -1 => (all.size)
    case i  => (i)
