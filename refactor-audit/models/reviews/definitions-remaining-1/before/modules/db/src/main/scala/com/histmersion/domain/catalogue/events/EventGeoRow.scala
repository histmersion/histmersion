package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.{EventGeo, EventLeads}
import com.augustnagro.magnum.DbCodec

/** One event on the map before its outline is read (the map's events). */
final private[events] case class EventGeoRow(
    row: EventRow,
    lat: Double,
    lon: Double,
    anchor: String,
    placeLabel: Option[String],
    hasArea: Boolean,
    vei: Option[Int],
    magnitude: Option[Double]
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
