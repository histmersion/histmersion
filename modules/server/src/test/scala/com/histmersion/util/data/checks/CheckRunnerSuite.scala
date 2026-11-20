package com.histmersion
package util.data.checks

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.checks.Lint
import com.histmersion.api.checks.Lint.{LintFixResult, LintItem, LintRuleReport, Rule}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataOwner
import com.histmersion.util.database.Transactor
import com.histmersion.util.database.TestPermits.given
import com.augustnagro.magnum.DbCon
import com.histmersion.api.library.Citekey
import com.histmersion.util.time.SystemTimer

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

  final private class Fake(name: Rule.Name, items: Int, override val fix: Option[SafeFix] = None) extends CheckRule:
    val owner: DataOwner = DataOwner("test")
    val rule: Rule = Rule(name, Rule.Description(s"$name rule"))
    def report(context: CheckContext, maxItems: CheckRule.MaxItems)(using run: CheckRun)(using DbCon): LintRuleReport =
      Lint.report(
        rule,
        List.tabulate(items)(i => LintItem(Citekey(s"${run(shared).head}$i"), LintItem.Detail(name.value))),
        Lint.ItemLimit(maxItems.value)
      )

  private def fixing(
      line: LintFixResult.Applied,
      edges: LintFixResult.EdgesResolved,
      when: FixWhen,
      rank: FixRank
  ): Option[SafeFix] =
    Some(
      SafeFix(
        when,
        rank,
        _ =>
          LintFixResult(
            List(line),
            edges,
            LintFixResult.SourcesReindexed(0),
            LintFixResult.ChunksWritten(0),
            LintFixResult.Remaining(edges.value),
            findingsPlaced = LintFixResult.FindingsPlaced(edges.value)
          )
      )
    )

  private val order = CheckOrder(List(Rule.Name("a"), Rule.Name("b"), Rule.Name("c")))
  private val registered = List(
    Fake(
      Rule.Name("b"),
      1,
      fixing(LintFixResult.Applied("b fixed"), LintFixResult.EdgesResolved(2), FixWhen.WhenNamed, FixRank(3))
    ),
    Fake(Rule.Name("z"), 0),
    Fake(
      Rule.Name("a"),
      3,
      fixing(LintFixResult.Applied("a fixed"), LintFixResult.EdgesResolved(1), FixWhen.EveryFix, FixRank(2))
    ),
    Fake(
      Rule.Name("c"),
      0,
      fixing(LintFixResult.Applied("c fixed"), LintFixResult.EdgesResolved(4), FixWhen.WhenNamed, FixRank(1))
    )
  )
  private val runner = CheckRunner(registered, order, NoDatabase)
  private lazy val context =
    CheckContext(AppConfig.load(), null, _ => (_, _, _) => Nil, new GuardedWrite((_, _, _) => true), new SystemTimer)

  test("the rules in the stated order, an unordered one last"):
    assertEquals(runner.names, List("a", "b", "c", "z"))

  test("selection: all, the named ones in the order named and once, an unknown name refused with the known ones"):
    assertEquals(runner.select(Nil).map(_.map(_.rule.name)), Right(List("a", "b", "c", "z")))
    assertEquals(
      runner
        .select(List(CheckRule.Name(" c"), CheckRule.Name(""), CheckRule.Name("A"), CheckRule.Name("c")))
        .map(_.map(_.rule.name)),
      Right(List("c", "a"))
    )
    assertEquals(
      runner.select(List(CheckRule.Name("a"), CheckRule.Name("nope"))).map(_.map(_.rule.name)).left.map(_.sentence),
      Left("unknown lint rule(s): nope — known rules: a, b, c, z")
    )

  test("the report of the selected rules, capped per rule, counted in full"):
    val rep = runner.report(runner.rules, context, CheckRule.MaxItems(2))
    assertEquals(
      rep.rules.map(r => (r.rule, r.count, r.items.size)),
      List(("a", 3, 2), ("b", 1, 1), ("c", 0, 0), ("z", 0, 0))
    )
    assertEquals(rep.rules.head.items.map(_.citekey), List("x0", "x1"))
    assertEquals(rep.fixed, Nil)

  test("the fixes: every call's always, the named ones only when named, in fix order, summed"):
    assertEquals(
      runner.fix(Nil, context),
      LintFixResult(
        List(LintFixResult.Applied("a fixed")),
        LintFixResult.EdgesResolved(1),
        LintFixResult.SourcesReindexed(0),
        LintFixResult.ChunksWritten(0),
        LintFixResult.Remaining(1),
        findingsPlaced = LintFixResult.FindingsPlaced(1)
      )
    )
    assertEquals(
      runner.fix(List(CheckRule.Name(" B "), CheckRule.Name("C"), CheckRule.Name("unknown")), context),
      LintFixResult(
        List(LintFixResult.Applied("c fixed"), LintFixResult.Applied("a fixed"), LintFixResult.Applied("b fixed")),
        LintFixResult.EdgesResolved(7),
        LintFixResult.SourcesReindexed(0),
        LintFixResult.ChunksWritten(0),
        LintFixResult.Remaining(7),
        findingsPlaced = LintFixResult.FindingsPlaced(7)
      )
    )
