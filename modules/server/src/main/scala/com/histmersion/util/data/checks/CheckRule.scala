package com.histmersion.util.data.checks

import com.histmersion.api.checks.Lint.{LintRuleReport, Rule}
import com.histmersion.util.data.DataOwner
import com.augustnagro.magnum.DbCon

/** One lint rule or health check of an owner's data: what it is called and checks, how it reports on one run's
  * connection, and the repair that is safe to make without a person, when it has one.
  */
trait CheckRule:
  def owner: DataOwner
  def rule: Rule
  def report(context: CheckContext, maxItems: com.histmersion.util.data.checks.CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport
  def fix: Option[SafeFix] = None

object CheckRule:
  type MaxItems = MaxItems.T
  object MaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
