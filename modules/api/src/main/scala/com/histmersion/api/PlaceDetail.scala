package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.LocationPrecision

/** `GET /api/places/{id}?asOf=` — the row plus its GeoJSON geometry and the hierarchy AS OF `asOf` (only edges whose
  * own `period` covers it). `geometry` is GeoJSON **text**; a place with no shape of its own reports the union of its
  * children's, which is what a continent's overlay draws.
  */
final case class PlaceDetail(
    place: PlaceSummary,
    geometry: Option[String],
    parents: List[PlaceSummary],
    children: List[PlaceSummary],
    findings: Int,
    asOf: Option[Long],
    /** Mirrors `place.contested`, so a client drawing the overlay does not have to reach inside. */
    contested: Boolean = false,
    footprints: List[PlaceFootprint] = Nil,
    links: List[PlaceLink] = Nil,
    types: List[PlaceType] = Nil,
    /** What the corpus holds for this place — only when asked for (`?counts=true`, the map's hover card); `None`
      * otherwise, because it costs four counting queries.
      */
    counts: Option[PlaceCounts] = None,
    /** The shape is an AREA (a polygon). `false` for a point (and for no geometry): fine for a site, but a region or
      * polity with only a point has no boundary yet ([[PlaceShape]]).
      */
    hasBoundary: Boolean = false,
    /** What the place IS, as its sources describe it (Wikidata's schema:description, an LPF dataset's `descriptions`):
      * English first, then the place's own language.
      */
    descriptions: List[PlaceDescription] = Nil,
    /** On what ground a time-filtered view shows the place ([[PlaceTimeBasis]]). */
    timeBasis: PlaceTimeBasis = PlaceTimeBasis.Dated,
    /** How exact the point is: `grid-cell` for a Pleiades point that is only the centre of an atlas grid square
      * ([[LocationPrecision]]).
      */
    locationPrecision: LocationPrecision = LocationPrecision.Stated,
    /** Pictures of the place from outside providers, best first — only with `?counts=true` (the card), like `counts`
      * (#1446).
      */
    media: List[MediaThumb] = Nil,
    /** What a site IS ([[SiteTypes]]) — the type the map draws its icon for, so its card shows the same icon; `None`
      * for an untyped site and for every other kind.
      */
    siteType: Option[String] = None,
    /** What the place's own record says beyond its names and dates: where its dates come from, its heritage
      * designations and the registers that list it (a Wikidata place's extract carries them).
      */
    record: PlaceRecord = PlaceRecord()
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** A region or polity whose only shape is a point (one with no geometry at all is a hierarchy node, not a missing
    * boundary).
    */
  def pointOnlyArea: Boolean = geometry.isDefined && PlaceShape.pointOnlyArea(place.kind, hasBoundary)

  /** What the shape is: no boundary yet (a point-only region or polity), or a people's point. */
  def shapeNote: Option[String] =
    Option
      .when(pointOnlyArea)(PlaceShape.NoBoundaryNote)
      .orElse(Option.when(geometry.isDefined && PlaceShape.isPeople(place.kind))(PlaceShape.PeopleNote))

  /** How exact the point is, when it is not as stated. */
  def locationNote: Option[String] =
    Option.when(locationPrecision == LocationPrecision.GridCell)(LocationPrecision.Approximate)

  /** The card's lines about the shape and the point: no boundary yet, a people, a grid-square point. */
  def shapeNotes: List[String] = shapeNote.toList ++ locationNote.map(n => s"location: $n").toList

  /** Read at a time, the place is there only by the findings dated then: its card says so. */
  def datedByFindings: Boolean = asOf.isDefined && timeBasis == PlaceTimeBasis.Findings
object PlaceDetail:
  given Schema[PlaceDetail] = summon[KindlingsSchema[PlaceDetail]].schema
