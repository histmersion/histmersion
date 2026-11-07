package com.histmersion.domain.catalogue.events

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
import com.histmersion.api.catalogue.Correction
final case class EventSaved(created: EventSaved.Created, updated: EventSaved.Updated, rows: EventSaved.Rows, corrections: Correction.Counts = Correction.Counts())

object EventSaved:
  type Created = com.histmersion.RowCount
  val Created: _root_.com.histmersion.RowCount.type = _root_.com.histmersion.RowCount
  type Updated = com.histmersion.RowCount
  val Updated: _root_.com.histmersion.RowCount.type = _root_.com.histmersion.RowCount
  type Rows = com.histmersion.RowCount
  val Rows: _root_.com.histmersion.RowCount.type = _root_.com.histmersion.RowCount
