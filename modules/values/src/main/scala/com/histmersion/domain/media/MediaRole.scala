package com.histmersion.domain.media
object MediaRole:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  val placeRoles: List[com.histmersion.domain.media.MediaRole.Wire] = List(_root_.com.histmersion.domain.media.MediaRole.Wire(Image), _root_.com.histmersion.domain.media.MediaRole.Wire(AerialView), _root_.com.histmersion.domain.media.MediaRole.Wire(Plan), _root_.com.histmersion.domain.media.MediaRole.Wire(LocatorMap), _root_.com.histmersion.domain.media.MediaRole.Wire(RouteMap))

  /** Every role, in card order: a place's own picture before an object found there and a map of it; a source shows its
    * cover before an illustration.
    */
  val all: List[com.histmersion.domain.media.MediaRole.Wire] = placeRoles ++ List(Depiction, Object, Map, Cover, Illustration)

  /** Pure: the rank of a role on a card (unknown roles last). */
  def order(role: com.histmersion.domain.media.MediaRole.Wire): Int = all.indexOf(role) match
    case -1 => (all.size)
    case i  => (i)
