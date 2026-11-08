package com.histmersion
package domain.catalogue.events

import com.histmersion.api.catalogue.EventSummary
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** An event's row with its own geometry as GeoJSON. */
final private[events] case class EventDetailRow(row: EventRow, geometry: Option[EventDetailRow.Geometry]) derives DbCodec:
  def toSummary: EventSummary = row.toSummary

object EventDetailRow:
  type Geometry = Geometry.T
  object Geometry extends Newtype[String, Pure]
