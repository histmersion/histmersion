package com.histmersion
package domain.catalogue.events

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{LintRule, tolerated}

/** The events' share of the catalogue's data contribution: their count, and the check of the corrections a catalogue's
  * later import contradicts.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventsData:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(using DbCon): List[DataCount] = List(
    DataCount(DataCount.Noun("events"), DataCount.Count(EventRepo.count().value))
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checks(owner: DataOwner): List[CheckRule] = List(Conflicts(owner))

  /** `event-correction-conflict`: a field a person kept by hand that the catalogue changed since. */
  final private class Conflicts(val owner: DataOwner) extends CheckRule:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val rule: Rule =
      Lint
        .rule(Rule.Name(EventCorrectionConflict))
        .getOrElse(Rule(Rule.Name(EventCorrectionConflict), Rule.Description(EventCorrectionConflict)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(
        rule,
        CorrectionRepo.conflictsIn().map((id, detail) => LintItem(Citekey(id.value), detail)),
        Lint.ItemLimit(maxItems.value)
      )
