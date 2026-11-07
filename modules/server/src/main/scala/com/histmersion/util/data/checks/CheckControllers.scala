package com.histmersion.util.data.checks

import com.histmersion.api.checks.Lint.{FormatChecker, LintFixResult, LintReport}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.maintenance.WriteGate
import hearth.kindlings.optics.syntax.*

import javax.sql.DataSource
import com.histmersion.api.Api

/** The registered checks as REST, MCP and the CLI reach them: the lint report (an unknown rule name is a 400: a typo
  * must never read as a clean corpus), the safe fixes (a write, under the maintenance guard) and the report after them.
  * `format` is the formatting check the composition gives the checks, `write` the guarded write a fix makes through a
  * record's own patch.
  */
final class CheckControllers(
    checks: CheckRunner,
    config: () => AppConfig,
    ds: DataSource,
    format: (com.histmersion.api.text.WikiLinks.Occurrence.Target => Boolean) => FormatChecker,
    write: GuardedWrite,
    maintenance: WriteGate
)(using timer: com.histmersion.util.time.Timer):
  /** The rule names, in report order. */
  def lintRules: List[com.histmersion.util.data.checks.CheckRule.Name] = checks.names

  private def context: CheckContext = CheckContext(config(), ds, format, write, timer)

  /** `GET /api/lint` — read-only. */
  def lint(rules: List[com.histmersion.util.data.checks.CheckRule.Name]): Either[Api.Error, LintReport] =
    checks
      .select(rules)
      .left
      .map(m => Api.Error(Api.Error.Message(m.value)))
      .map(rs => checks.report(rs, context, CheckRule.MaxItems(config().lint.maxItems.value)))

  /** `POST /api/lint/fix` — the repairs that are safe to make without a person, each owner's, in their ranks: the ones
    * that run on every call, and those of the rules `rules` names. Each is bounded per call; `remaining > 0` (or
    * `pointAreasRemaining > 0`) means "call me again".
    */
  def lintFix(rules: List[com.histmersion.util.data.checks.CheckRule.Name] = Nil): LintFixResult = checks.fix(rules, context)

  /** `lint --fix` rewrites edges and re-chunks sources — a WRITE, and it was the one repair path that ran straight
    * through a restore.
    */
  def guardedLintFix(rules: List[com.histmersion.util.data.checks.CheckRule.Name] = Nil): Either[Api.Error, LintFixResult] =
    maintenance.guard(WriteGate.OperationName("lint/fix"))(Right(lintFix(rules)))

  /** Lint, optionally after applying the safe fixes — what `histmersion lint --fix` and the MCP tool do, so the report
    * the caller sees is the state AFTER the repair.
    */
  def lintWithFix(rules: List[com.histmersion.util.data.checks.CheckRule.Name], fix: com.histmersion.util.data.checks.CheckControllers.ApplyFix): Either[Api.Error, LintReport] =
    if !fix.value then lint(rules)
    else guardedLintFix(rules).flatMap(f => lint(rules).map(_.modify(_.fixed).setTo(f.applied.map(a => com.histmersion.api.checks.Lint.LintReport.Fixed(a.value)))))

object CheckControllers:
  type ApplyFix = ApplyFix.T
  object ApplyFix extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

