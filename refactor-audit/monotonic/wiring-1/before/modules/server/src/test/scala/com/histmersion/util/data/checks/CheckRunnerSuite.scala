package com.histmersion.util.data.checks

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.{LintFixResult, LintItem, LintRuleReport, Rule}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataOwner
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.DbCon

/** The lint runner over registered rules, without a database: the stated order whatever the registration order, the
  * selection and its refusal, one run per report, and the fixes — every call's and the named ones — summed in fix
  * order.
  */
class CheckRunnerSuite extends munit.FunSuite:

  // the rules here read no database: the connection is never touched
  private object NoDatabase extends Transactor:
    def read[A](work: DbCon ?=> A): A = work(using null.asInstanceOf[DbCon])
    def transact[A](work: DbCon ?=> A): A = work(using null.asInstanceOf[DbCon])

  private val shared = CheckInput(List("x"))

  final private class Fake(name: String, items: Int, override val fix: Option[SafeFix] = None) extends CheckRule:
    val owner: DataOwner = DataOwner("test")
    val rule: Rule = Rule(name, s"$name rule")
    def report(context: CheckContext, maxItems: Int)(using run: CheckRun)(using DbCon): LintRuleReport =
      Lint.report(rule, List.tabulate(items)(i => LintItem(s"${run(shared).head}$i", name)), maxItems)

  private def fixing(line: String, edges: Int, when: FixWhen, rank: Int): Option[SafeFix] =
    Some(SafeFix(when, FixRank(rank), _ => LintFixResult(List(line), edges, 0, 0, edges, findingsPlaced = edges)))

  private val order = CheckOrder(List("a", "b", "c"))
  private val registered = List(
    Fake("b", 1, fixing("b fixed", 2, FixWhen.WhenNamed, 3)),
    Fake("z", 0),
    Fake("a", 3, fixing("a fixed", 1, FixWhen.EveryFix, 2)),
    Fake("c", 0, fixing("c fixed", 4, FixWhen.WhenNamed, 1))
  )
  private val runner = CheckRunner(registered, order, NoDatabase)
  private lazy val context = CheckContext(AppConfig.load(), null, _ => (_, _, _) => Nil, (_, _, _) => true)

  test("the rules in the stated order, an unordered one last"):
    assertEquals(runner.names, List("a", "b", "c", "z"))

  test("selection: all, the named ones in the order named and once, an unknown name refused with the known ones"):
    assertEquals(runner.select(Nil).map(_.map(_.rule.name)), Right(List("a", "b", "c", "z")))
    assertEquals(runner.select(List(" c", "", "A", "c")).map(_.map(_.rule.name)), Right(List("c", "a")))
    assertEquals(
      runner.select(List("a", "nope")).map(_.map(_.rule.name)),
      Left("unknown lint rule(s): nope — known rules: a, b, c, z")
    )

  test("the report of the selected rules, capped per rule, counted in full"):
    val rep = runner.report(runner.rules, context, 2)
    assertEquals(
      rep.rules.map(r => (r.rule, r.count, r.items.size)),
      List(("a", 3, 2), ("b", 1, 1), ("c", 0, 0), ("z", 0, 0))
    )
    assertEquals(rep.rules.head.items.map(_.citekey), List("x0", "x1"))
    assertEquals(rep.fixed, Nil)

  test("the fixes: every call's always, the named ones only when named, in fix order, summed"):
    assertEquals(runner.fix(Nil, context), LintFixResult(List("a fixed"), 1, 0, 0, 1, findingsPlaced = 1))
    assertEquals(
      runner.fix(List(" B ", "C", "unknown"), context),
      LintFixResult(List("c fixed", "a fixed", "b fixed"), 7, 0, 0, 7, findingsPlaced = 7)
    )
