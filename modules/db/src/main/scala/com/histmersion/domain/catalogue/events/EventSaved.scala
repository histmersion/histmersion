package com.histmersion.domain.catalogue.events

import com.histmersion.api.CorrectionCounts

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
final case class EventSaved(created: Int, updated: Int, rows: Int, corrections: CorrectionCounts = CorrectionCounts())
