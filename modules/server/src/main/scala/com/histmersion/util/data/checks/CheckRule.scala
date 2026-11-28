package com.histmersion
package util.data.checks

import com.histmersion.api.checks.Lint.{LintRuleReport, Rule}
import com.histmersion.util.data.DataOwner
import com.augustnagro.magnum.DbCon
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.checks.Lint

/** One lint rule or health check of an owner's data: what it is called and checks, how it reports on one run's
  * connection, and the repair that is safe to make without a person, when it has one.
  */
trait CheckRule:
  def owner: DataOwner
  def rule: Rule
  def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using CheckRun)(using DbCon): LintRuleReport
  def fix: Option[SafeFix] = None

object CheckRule:
  export Lint.ItemLimit as MaxItems
  type Name = Name.T
  object Name extends Newtype[String, Pure]
