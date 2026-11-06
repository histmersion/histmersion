package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.given
import com.histmersion.api.catalogue.Place

/** A place's page as the server answers it: the gazetteer's `PlaceCard` with the place's pictures. */
final case class PlaceDetail(
    place: com.histmersion.api.catalogue.Place.Summary,
    geometry: Option[PlaceDetail.Geometry],
    parents: List[com.histmersion.api.catalogue.Place.Summary],
    children: List[com.histmersion.api.catalogue.Place.Summary],
    findings: PlaceDetail.Findings,
    asOf: Option[PlaceDetail.AsOf],
    contested: PlaceDetail.Contested = _root_.com.histmersion.api.views.PlaceDetail.Contested(false),
    footprints: List[com.histmersion.api.catalogue.Place.Footprint] = Nil,
    links: List[com.histmersion.api.catalogue.Place.Link] = Nil,
    types: List[com.histmersion.api.catalogue.Place.Type] = Nil,
    counts: Option[com.histmersion.api.catalogue.Place.Counts] = None,
    hasBoundary: PlaceDetail.HasBoundary = _root_.com.histmersion.api.views.PlaceDetail.HasBoundary(false),
    descriptions: List[com.histmersion.api.catalogue.Place.Description] = Nil,
    timeBasis: com.histmersion.api.catalogue.Place.TimeBasis = com.histmersion.api.catalogue.Place.TimeBasis.Dated,
    /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
    locationPrecision: Place.Precision = Place.Precision.Stated,
    media: List[MediaThumb] = Nil,
    siteType: Option[PlaceDetail.SiteType] = None,
    record: com.histmersion.api.catalogue.Place.Record = com.histmersion.api.catalogue.Place.Record()
) extends com.histmersion.api.catalogue.Place.Card derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object PlaceDetail:
  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsOf = com.histmersion.api.views.Time.AsOf
  val AsOf: _root_.com.histmersion.api.views.Time.AsOf.type = _root_.com.histmersion.api.views.Time.AsOf
  type Contested = Contested.T
  object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasBoundary = HasBoundary.T
  object HasBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteType = SiteType.T
  object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
