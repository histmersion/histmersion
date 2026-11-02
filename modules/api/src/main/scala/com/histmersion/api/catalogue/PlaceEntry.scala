package com.histmersion.api.catalogue
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
import com.histmersion.values.LocationPrecision

/** The gazetteer's record of one place: the row, its GeoJSON geometry and the hierarchy around it at `asOf`, its
  * footprints, links, types and records; `counts`, what the corpus holds for it, filled by whoever shows its card. Not
  * on the wire: the server answers the page (`PlaceDetail`), which adds the place's pictures.
  */
final case class PlaceEntry(
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
    siteType: Option[String] = None,
    record: PlaceRecord = PlaceRecord()
) extends PlaceCard
