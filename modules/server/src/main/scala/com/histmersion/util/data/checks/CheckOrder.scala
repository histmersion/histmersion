package com.histmersion
package util.data.checks

import com.histmersion.api.checks.lint.Rule

import com.histmersion.api.checks.Lint
import com.histmersion.lint.{tolerated, LintRule}

/** The order of the checks whichever owner registered them: the report lists the rules in `report` order (by rule name;
  * a rule it does not name comes after them, in registration order), and the safe fixes run by their rank.
  */
final case class CheckOrder(report: List[Rule.Name]):
  def reported(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(r => CheckOrder.rank(report, r.rule.name))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fixing(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(_.fix.fold(Int.MaxValue)(_.rank.value))

object CheckOrder:

  private def rank(names: List[Rule.Name], name: Rule.Name): Int =
    val i = names.indexOf(name)
    if i < 0 then Int.MaxValue else i
