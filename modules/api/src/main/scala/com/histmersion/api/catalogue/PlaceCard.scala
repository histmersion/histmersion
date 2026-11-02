package com.histmersion.api.catalogue
import com.histmersion.api.catalogue.{
  PlaceCounts,
  PlaceDescription,
  PlaceFootprint,
  PlaceLink,
  PlaceRecord,
  PlaceShape,
  PlaceSummary,
  PlaceTimeBasis,
  PlaceType
}
import com.histmersion.values.LocationPrecision

/** What a place's page shows of the gazetteer: the row, its geometry, the hierarchy around it and its records. The
  * gazetteer's own record ([[PlaceEntry]]) and the page the server answers (`PlaceDetail`, which adds the place's
  * pictures) both are one, so the page renders from either.
  */
trait PlaceCard:
  def place: PlaceSummary
  def geometry: Option[String]
  def parents: List[PlaceSummary]
  def children: List[PlaceSummary]
  def findings: Int
  def asOf: Option[Long]
  def contested: Boolean
  def footprints: List[PlaceFootprint]
  def links: List[PlaceLink]
  def types: List[PlaceType]
  def counts: Option[PlaceCounts]
  def hasBoundary: Boolean
  def descriptions: List[PlaceDescription]
  def timeBasis: PlaceTimeBasis
  def locationPrecision: LocationPrecision
  def siteType: Option[String]
  def record: PlaceRecord

  def pointOnlyArea: Boolean = geometry.isDefined && PlaceShape.pointOnlyArea(place.kind, hasBoundary)

  def shapeNote: Option[String] =
    Option
      .when(pointOnlyArea)(PlaceShape.NoBoundaryNote)
      .orElse(Option.when(geometry.isDefined && PlaceShape.isPeople(place.kind))(PlaceShape.PeopleNote))

  def locationNote: Option[String] =
    Option.when(locationPrecision == LocationPrecision.GridCell)(LocationPrecision.Approximate)

  def shapeNotes: List[String] = shapeNote.toList ++ locationNote.map(n => s"location: $n").toList

  def datedByFindings: Boolean = asOf.isDefined && timeBasis == PlaceTimeBasis.Findings
