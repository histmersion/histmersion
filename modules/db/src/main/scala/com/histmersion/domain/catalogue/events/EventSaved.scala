package com.histmersion
package domain.catalogue.events

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
import com.histmersion.api.catalogue.Correction
final case class EventSaved(
    created: EventSaved.Created,
    updated: EventSaved.Updated,
    rows: EventSaved.Rows,
    corrections: Correction.Counts = Correction.Counts()
)

object EventSaved:
  type Created = RowCount
  val Created: RowCount.type = RowCount
  type Updated = RowCount
  val Updated: RowCount.type = RowCount
  type Rows = RowCount
  val Rows: RowCount.type = RowCount
