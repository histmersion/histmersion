package com.histmersion.util.data.checks

import com.histmersion.api.checks.Lint.{LintFixResult, LintReport}
import com.histmersion.util.database.Transactor

/** The lint runner over the registered checks: selects rules, runs them on one connection into the report, and applies
  * the safe fixes into one fix result.
  */
final class CheckRunner(registered: List[CheckRule], order: CheckOrder, transactor: Transactor):

  /** Every registered rule, in report order. */
  val rules: List[CheckRule] = order.reported(registered)

  def names: List[com.histmersion.util.data.checks.CheckRule.Name] = rules.map(_.rule.name)

  private def named(name: com.histmersion.util.data.checks.CheckRule.Name): Option[CheckRule] = rules.find(_.rule.name == name.trim.toLowerCase)

  /** The rules a request names, in the order it names them — all of them when it names none. An unknown name is
    * refused, so a typo never reads as a clean corpus.
    */
  def select(requested: List[com.histmersion.util.data.checks.CheckRule.Name]): Either[com.histmersion.util.data.checks.CheckRunner.Problem, List[CheckRule]] =
    val wanted = requested.map(_.trim).filter(_.nonEmpty)
    if wanted.isEmpty then Right(rules)
    else
      val unknown = wanted.filter(w => named(w).isEmpty)
      if unknown.nonEmpty then
        Left(s"unknown lint rule(s): ${unknown.mkString(", ")} — known rules: ${names.mkString(", ")}")
      else Right(wanted.flatMap(named).distinct)

  /** The report of `selected`, on one connection: what several rules read is read once. */
  def report(selected: List[CheckRule], context: CheckContext, maxItems: com.histmersion.util.data.checks.CheckRule.MaxItems): LintReport =
    transactor.read:
      val run = CheckRun()
      LintReport(selected.map(_.report(context, maxItems)(using run)))

  /** The safe fixes: every rule's that runs on every call, and those of the rules `requested` names (by name, ignoring
    * case; an unknown name selects nothing), in fix order.
    */
  def fix(requested: List[com.histmersion.util.data.checks.CheckRule.Name], context: CheckContext): LintFixResult =
    def asked(rule: CheckRule) = requested.exists(_.trim.equalsIgnoreCase(rule.rule.name))
    val results = order.fixing(rules).flatMap { r =>
      r.fix.collect { case f if f.when == FixWhen.EveryFix || asked(r) => f.run(context) }
    }
    LintFixResult(
      applied = results.flatMap(_.applied),
      edgesResolved = results.map(_.edgesResolved).sum,
      sourcesReindexed = results.map(_.sourcesReindexed).sum,
      chunksWritten = results.map(_.chunksWritten).sum,
      remaining = results.map(_.remaining).sum,
      findingsRegeocoded = results.map(_.findingsRegeocoded).sum,
      findingsPlaced = results.map(_.findingsPlaced).sum,
      placesReclassified = results.map(_.placesReclassified).sum,
      placesMerged = results.map(_.placesMerged).sum,
      placesRelocated = results.map(_.placesRelocated).sum,
      pointAreasRemoved = results.map(_.pointAreasRemoved).sum,
      pointAreasRemaining = results.map(_.pointAreasRemaining).sum
    )

object CheckRunner:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
