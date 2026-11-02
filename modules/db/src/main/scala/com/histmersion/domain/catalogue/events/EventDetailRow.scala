package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.EventSummary
import com.augustnagro.magnum.DbCodec

/** An event's row with its own geometry as GeoJSON. */
final private[events] case class EventDetailRow(row: EventRow, geometry: Option[String]) derives DbCodec:
  def toSummary: EventSummary = row.toSummary
