package com.histmersion
package api.views
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Creation

/** A place's page as the server answers it: the gazetteer's `PlaceCard` with the place's pictures. */
final case class PlaceDetail(
    place: Place.Summary,
    geometry: Option[Place.Geometry],
    parents: List[Place.Summary],
    children: List[Place.Summary],
    findings: Place.Counts.Findings,
    asOf: Option[Year],
    contested: Place.Entry.Contested = Place.Entry.Contested(false),
    footprints: List[Place.Footprint] = Nil,
    links: List[Place.Link] = Nil,
    types: List[Place.Type] = Nil,
    counts: Option[Place.Counts] = None,
    hasBoundary: Place.Entry.HasBoundary = Place.Entry.HasBoundary(false),
    descriptions: List[Place.Description] = Nil,
    timeBasis: Place.TimeBasis = Place.TimeBasis.Dated,
    /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
    locationPrecision: Place.Precision = Place.Precision.Stated,
    media: List[MediaThumb] = Nil,
    siteType: Option[Place.Entry.SiteType] = None,
    record: Place.Record = Place.Record(),
    creator: Option[Creation] = None,
    createdAt: Option[Creation.At] = None
) extends Place.Card derives JsonCodec, KindlingsSchema, api.json.Schema
