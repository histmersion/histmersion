package com.histmersion.domain.catalogue.events

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
import com.histmersion.api.catalogue.Correction
final case class EventSaved(created: Int, updated: Int, rows: Int, corrections: Correction.Counts = Correction.Counts())
