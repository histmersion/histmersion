package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.LocationPrecision

final case class PlaceDetail(
    place: PlaceSummary,
    geometry: Option[String],
    parents: List[PlaceSummary],
    children: List[PlaceSummary],
    findings: Int,
    asOf: Option[Long],
    contested: Boolean = false,
    footprints: List[PlaceFootprint] = Nil,
    links: List[PlaceLink] = Nil,
    types: List[PlaceType] = Nil,
    counts: Option[PlaceCounts] = None,
    hasBoundary: Boolean = false,
    descriptions: List[PlaceDescription] = Nil,
    timeBasis: PlaceTimeBasis = PlaceTimeBasis.Dated,
    /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
    locationPrecision: LocationPrecision = LocationPrecision.Stated,
    media: List[MediaThumb] = Nil,
    siteType: Option[String] = None,
    record: PlaceRecord = PlaceRecord()
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def pointOnlyArea: Boolean = geometry.isDefined && PlaceShape.pointOnlyArea(place.kind, hasBoundary)

  def shapeNote: Option[String] =
    Option
      .when(pointOnlyArea)(PlaceShape.NoBoundaryNote)
      .orElse(Option.when(geometry.isDefined && PlaceShape.isPeople(place.kind))(PlaceShape.PeopleNote))

  def locationNote: Option[String] =
    Option.when(locationPrecision == LocationPrecision.GridCell)(LocationPrecision.Approximate)

  def shapeNotes: List[String] = shapeNote.toList ++ locationNote.map(n => s"location: $n").toList

  def datedByFindings: Boolean = asOf.isDefined && timeBasis == PlaceTimeBasis.Findings
object PlaceDetail:
  given Schema[PlaceDetail] = summon[KindlingsSchema[PlaceDetail]].schema
