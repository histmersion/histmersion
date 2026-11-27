package com.histmersion
package domain.catalogue.events

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
import com.histmersion.api.catalogue.Correction
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class EventSaved(
    created: EventSaved.Created,
    updated: EventSaved.Updated,
    rows: EventSaved.Rows,
    corrections: Correction.Counts = Correction.Counts()
)

object EventSaved:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Created = RowCount
  val Created: RowCount.type = RowCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Updated = RowCount
  val Updated: RowCount.type = RowCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Rows = RowCount
  val Rows: RowCount.type = RowCount
