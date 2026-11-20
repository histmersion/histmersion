package com.histmersion
package util.data.checks
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.checks.Lint.{LintFixResult, LintReport}
import com.histmersion.util.database.{Transactor, WritePermit}
import io.github.iltotore.iron.constraint.any.Pure

/** The lint runner over the registered checks: selects rules, runs them on one connection into the report, and applies
  * the safe fixes into one fix result.
  */
final class CheckRunner(registered: List[CheckRule], order: CheckOrder, transactor: Transactor):

  /** Every registered rule, in report order. */
  val rules: List[CheckRule] = order.reported(registered)

  def names: List[CheckRule.Name] = rules.map(r => CheckRule.Name(r.rule.name.value))

  private def named(name: String): Option[CheckRule] = rules.find(_.rule.name.value == name.trim.toLowerCase)

  /** The rules a request names, in the order it names them — all of them when it names none. An unknown name is
    * refused, so a typo never reads as a clean corpus.
    */
  def select(requested: List[CheckRule.Name]): Either[Problem, List[CheckRule]] =
    val wanted = requested.map(_.value.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(rules)
    else
      val unknown = wanted.filter(w => named(w).isEmpty)
      if unknown.nonEmpty then
        Left(
          Problem.Unclassified(
            Legacy.Text(s"unknown lint rule(s): ${unknown.mkString(", ")} — known rules: ${names.mkString(", ")}")
          )
        )
      else Right(wanted.flatMap(named).distinct)

  /** The report of `selected`, on one connection: what several rules read is read once. */
  def report(selected: List[CheckRule], context: CheckContext, maxItems: CheckRule.MaxItems): LintReport =
    transactor.read:
      val run = CheckRun()
      LintReport(selected.map(_.report(context, maxItems)(using run)))

  /** The safe fixes: every rule's that runs on every call, and those of the rules `requested` names (by name, ignoring
    * case; an unknown name selects nothing), in fix order.
    */
  def fix(requested: List[CheckRule.Name], context: CheckContext)(using WritePermit): LintFixResult =
    def asked(rule: CheckRule) = requested.exists(_.value.trim.equalsIgnoreCase(rule.rule.name.value))
    val results = order.fixing(rules).flatMap { r =>
      r.fix.collect { case f if f.when == FixWhen.EveryFix || asked(r) => f.run(context) }
    }
    LintFixResult(
      applied = results.flatMap(_.applied),
      edgesResolved = LintFixResult.EdgesResolved(results.map(_.edgesResolved.value: Int).sum),
      sourcesReindexed = LintFixResult.SourcesReindexed(results.map(_.sourcesReindexed.value: Int).sum),
      chunksWritten = LintFixResult.ChunksWritten(results.map(_.chunksWritten.value: Int).sum),
      remaining = LintFixResult.Remaining(results.map(_.remaining.value: Int).sum),
      findingsRegeocoded = LintFixResult.FindingsRegeocoded(results.map(_.findingsRegeocoded.value: Int).sum),
      findingsPlaced = LintFixResult.FindingsPlaced(results.map(_.findingsPlaced.value: Int).sum),
      placesReclassified = LintFixResult.PlacesReclassified(results.map(_.placesReclassified.value: Int).sum),
      placesMerged = LintFixResult.PlacesMerged(results.map(_.placesMerged.value: Int).sum),
      placesRelocated = LintFixResult.PlacesRelocated(results.map(_.placesRelocated.value: Int).sum),
      pointAreasRemoved = LintFixResult.PointAreasRemoved(results.map(_.pointAreasRemoved.value: Int).sum),
      pointAreasRemaining = LintFixResult.PointAreasRemaining(results.map(_.pointAreasRemaining.value: Int).sum)
    )
