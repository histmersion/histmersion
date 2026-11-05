package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.{EventGeo, EventLeads}
import com.augustnagro.magnum.DbCodec

/** One event on the map before its outline is read (the map's events). */
final private[events] case class EventGeoRow(
    row: EventRow,
    lat: com.histmersion.Latitude,
    lon: com.histmersion.Longitude,
    anchor: EventGeoRow.Anchor,
    placeLabel: Option[EventGeoRow.PlaceLabel],
    hasArea: EventGeoRow.HasArea,
    vei: Option[EventGeoRow.Vei],
    magnitude: Option[EventGeoRow.Magnitude]
) derives DbCodec:
  /** The event as the map draws it, without its outline. */
  def toGeo: EventGeo =
    val e = row.toSummary
    EventGeo(
      com.histmersion.api.catalogue.Event.Id(e.id),
      e.label,
      e.kind,
      e.scale,
      e.historicity,
      e.timeSpan,
      com.histmersion.api.catalogue.Latitude(lat),
      com.histmersion.api.catalogue.Longitude(lon),
      anchor,
      (e.placeId).map(com.histmersion.api.catalogue.Place.Id.apply),
      placeLabel,
      e.parentId,
      None,
      e.tier,
      EventLeads.size(vei, magnitude),
      e.precision,
      e.datingNote
    )

object EventGeoRow:
  type Anchor = Anchor.T
  object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasArea = HasArea.T
  object HasArea extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
