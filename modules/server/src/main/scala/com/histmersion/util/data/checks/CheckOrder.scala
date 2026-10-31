package com.histmersion.util.data.checks

/** The stated order of the checks, by rule name, whichever owner registered them: the report lists the rules in
  * `report` order and the fixes run in `fixes` order. A rule neither names comes after them, in registration order.
  */
final case class CheckOrder(report: List[String], fixes: List[String]):
  def reported(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(r => CheckOrder.rank(report, r.rule.name))
  def fixing(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(r => CheckOrder.rank(fixes, r.rule.name))

object CheckOrder:
  private def rank(names: List[String], name: String): Int =
    val i = names.indexOf(name)
    if i < 0 then Int.MaxValue else i
