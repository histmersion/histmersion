package com.histmersion
package util.data.checks

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.checks.Lint.{FormatChecker, LintFixResult, LintReport}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.database.WritePermit
import hearth.kindlings.optics.syntax.*

import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.text.WikiLinks
import com.histmersion.util.time.Timer
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problems
import com.histmersion.lint.{LintRule, tolerated}

/** The registered checks as REST, MCP and the CLI reach them: the lint report (an unknown rule name is a 400: a typo
  * must never read as a clean corpus), the safe fixes (a write, under the maintenance guard) and the report after them.
  * `format` is the formatting check the composition gives the checks, `write` the guarded write a fix makes through a
  * record's own patch.
  */
final class CheckControllers(
    checks: CheckRunner,
    config: () => AppConfig,
    ds: DataSource,
    format: (WikiLinks.Occurrence.Target => Boolean) => FormatChecker,
    write: GuardedWrite,
    maintenance: WriteGate
)(using timer: Timer):
  /** The rule names, in report order. */
  def lintRules: List[CheckRule.Name] = checks.names

  private def context: CheckContext = CheckContext(config(), ds, format, write, timer)

  /** `GET /api/lint` — read-only. */
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def lint(rules: List[CheckRule.Name]): Either[Problems, LintReport] =
    checks
      .select(rules)
      .map(rs => checks.report(rs, context, CheckRule.MaxItems(config().lint.maxItems.value)))

  /** `POST /api/lint/fix` — the repairs that are safe to make without a person, each owner's, in their ranks: the ones
    * that run on every call, and those of the rules `rules` names. Each is bounded per call; `remaining > 0` (or
    * `pointAreasRemaining > 0`) means "call me again".
    */
  def lintFix(rules: List[CheckRule.Name] = Nil)(using WritePermit): LintFixResult = checks.fix(rules, context)

  /** `lint --fix` rewrites edges and re-chunks sources — a WRITE, and it was the one repair path that ran straight
    * through a restore.
    */
  def guardedLintFix(rules: List[CheckRule.Name] = Nil): Either[Api.Error, LintFixResult] =
    maintenance.guard(WriteGate.OperationName("lint/fix"))(Right(lintFix(rules)))

  /** `POST /api/lint/fix`: [[lintFix]] under the maintenance gate, a refusal thrown (the route has no error output). */
  def heldLintFix(rules: List[CheckRule.Name] = Nil): LintFixResult = maintenance.writing(lintFix(rules))

  /** Lint, optionally after applying the safe fixes — what `histmersion lint --fix` and the MCP tool do, so the report
    * the caller sees is the state AFTER the repair.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def lintWithFix(rules: List[CheckRule.Name], fix: CheckControllers.ApplyFix): Either[Api.Error, LintReport] =
    if !fix.value then lint(rules).left.map(Api.Error(_))
    else
      guardedLintFix(rules).flatMap(f =>
        lint(rules).left.map(Api.Error(_)).map(_.modify(_.fixed).setTo(f.applied.map(a => LintReport.Fixed(a.value))))
      )

object CheckControllers:
  type ApplyFix = ApplyFix.T
  object ApplyFix extends Newtype[Boolean, Pure]
