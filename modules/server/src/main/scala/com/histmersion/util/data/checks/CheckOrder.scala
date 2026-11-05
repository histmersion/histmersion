package com.histmersion.util.data.checks

/** The order of the checks whichever owner registered them: the report lists the rules in `report` order (by rule name;
  * a rule it does not name comes after them, in registration order), and the safe fixes run by their rank.
  */
final case class CheckOrder(report: List[CheckOrder.Report]):
  def reported(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(r => CheckOrder.rank(report, r.rule.name))
  def fixing(rules: List[CheckRule]): List[CheckRule] = rules.sortBy(_.fix.fold(Int.MaxValue)(_.rank.value))

object CheckOrder:
  type Report = Report.T
  object Report extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def rank(names: List[String], name: String): Int =
    val i = names.indexOf(name)
    if i < 0 then Int.MaxValue else i
