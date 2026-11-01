package com.histmersion.domain.catalogue.events

import com.histmersion.api.Lint
import com.histmersion.api.Lint.*
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.checks.{CheckContext, CheckRule, CheckRun}
import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon

/** The events' share of the catalogue's data contribution: their count, and the check of the corrections a catalogue's
  * later import contradicts.
  */
object EventsData:

  def figures(using DbCon): List[DataCount] = List(DataCount("events", EventRepo.count()))

  def checks(owner: DataOwner): List[CheckRule] = List(Conflicts(owner))

  /** `event-correction-conflict`: a field a person kept by hand that the catalogue changed since. */
  final private class Conflicts(val owner: DataOwner) extends CheckRule:
    val rule: Rule =
      Lint.rule(EventCorrectionConflict).getOrElse(Rule(EventCorrectionConflict, EventCorrectionConflict))
    def report(context: CheckContext, maxItems: Int)(using CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, CorrectionRepo.conflictsIn().map((id, detail) => LintItem(id, detail)), maxItems)
