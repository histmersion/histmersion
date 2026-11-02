package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.LocationPrecision
import com.histmersion.api.catalogue.{
  PlaceCounts,
  PlaceDescription,
  PlaceFootprint,
  PlaceLink,
  PlaceRecord,
  PlaceSummary,
  PlaceTimeBasis,
  PlaceType
}
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.given

/** A place's page as the server answers it: the gazetteer's `PlaceCard` with the place's pictures. */
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
) extends com.histmersion.api.catalogue.PlaceCard derives KindlingsCodecAsObject, KindlingsSchema
object PlaceDetail:
  given Schema[PlaceDetail] = summon[KindlingsSchema[PlaceDetail]].schema
