package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Place

/** A place's page as the server answers it: the gazetteer's `PlaceCard` with the place's pictures. */
final case class PlaceDetail(
    place: com.histmersion.api.catalogue.Place.Summary,
    geometry: Option[Place.Geometry],
    parents: List[com.histmersion.api.catalogue.Place.Summary],
    children: List[com.histmersion.api.catalogue.Place.Summary],
    findings: Place.Counts.Findings,
    asOf: Option[com.histmersion.api.catalogue.Year],
    contested: Place.Entry.Contested = Place.Entry.Contested(false),
    footprints: List[com.histmersion.api.catalogue.Place.Footprint] = Nil,
    links: List[com.histmersion.api.catalogue.Place.Link] = Nil,
    types: List[com.histmersion.api.catalogue.Place.Type] = Nil,
    counts: Option[com.histmersion.api.catalogue.Place.Counts] = None,
    hasBoundary: Place.Entry.HasBoundary = Place.Entry.HasBoundary(false),
    descriptions: List[com.histmersion.api.catalogue.Place.Description] = Nil,
    timeBasis: com.histmersion.api.catalogue.Place.TimeBasis = com.histmersion.api.catalogue.Place.TimeBasis.Dated,
    /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
    locationPrecision: Place.Precision = Place.Precision.Stated,
    media: List[MediaThumb] = Nil,
    siteType: Option[Place.Entry.SiteType] = None,
    record: com.histmersion.api.catalogue.Place.Record = com.histmersion.api.catalogue.Place.Record()
) extends com.histmersion.api.catalogue.Place.Card derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
