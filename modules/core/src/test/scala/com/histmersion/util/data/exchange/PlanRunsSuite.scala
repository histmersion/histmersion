package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.Problem

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.{MatchCounts, MergeDecision, MergeIssue, MergePlan}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.DecisionKind

import java.util.UUID
import com.histmersion.api.exchange.PlanMatches
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.data.exchange.Issue
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.MergePlanner
import com.histmersion.api.exchange.MergeTally
import com.histmersion.api.library.Title
import com.histmersion.api.library.Source
import com.histmersion.lint.{LintRule, tolerated}

/** Several kinds' runs of a plan as one: each stage in the given order; the first answer wins where one is asked for.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PlanRunsSuite extends munit.FunSuite:

  private def uid(s: String) = UUID.nameUUIDFromBytes(s.getBytes)

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final private class Named(name: String, answers: Boolean) extends PlanRun:
    override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
      es.modify(_.aliases)
        .setTo(
          es.aliases :+ api.exchange.Bundle.Alias(
            api.exchange.Bundle.Alias.Value(name),
            uid(name),
            api.exchange.Bundle.Alias.CreatedAt("2026-01-01T00:00:00.000000Z")
          )
        )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def notices: List[PairQuestions.InfoLine] = List(PairQuestions.InfoLine(s"$name notice"))
    override def ownerSource(foreign: UUID, kind: api.exchange.Bundle.Kind): Option[UUID] =
      Option.when(answers)(uid(name))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def bodiesWanted(
        pairs: List[(api.exchange.Bundle.Source, api.exchange.Bundle.Source)]
    ): Set[api.exchange.Bundle.Source.BodySha256] = Set(
      api.exchange.Bundle.Source.BodySha256(name)
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def argument(
        issue: MergeIssue,
        decision: MergeDecision
    ): Option[Either[Problem, Unit]] =
      Option.when(answers)(Left(Problem.Invalid.Missing.here(Problem.Field(name))))
    override def pairIssue(kind: MergeIssue.Kind): Boolean = answers && kind == MergeIssue.Kind.AttestationUnanchored
    override def dropped: Set[UUID] = Set(uid(name))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def bodyIssue(right: UUID): Option[MergeIssue.Id] = Option.when(answers)(MergeIssue.Id(name))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def citekeyArgument(
        issue: MergeIssue,
        decision: MergeDecision
    ): Option[Either[Problem, Unit]] =
      Option.when(answers)(Left(Problem.Invalid.Missing.here(Problem.Field(s"$name citekey"))))
    override def foldSources(
        right: api.exchange.Bundle.Source,
        left: api.exchange.Bundle.Source,
        citekey: Citekey,
        folds: PlanFolds
    ): Boolean =
      seen += s"$name fold"
      answers && name == "c"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def renamed(source: UUID, citekey: Citekey): Unit = seen += s"$name renamed $citekey"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def planned(plan: MergePlan): MergePlan = plan.modify(_.info).setTo(plan.info :+ MergePlan.Info(name))

  private val seen = scala.collection.mutable.ListBuffer.empty[String]

  /** Folds no run here asks anything of. */
  private object NoFolds extends PlanFolds:
    def input: MergePlanner.Input = fail("not asked")
    def localized: BundleEntities = BundleEntities()
    def fold(foreign: UUID, here: UUID, kind: api.exchange.Bundle.Kind): Unit = ()
    def foldedTo(foreign: UUID): Option[UUID] = None
    def here(u: UUID): Boolean = false
    def toLocal(u: UUID): UUID = u
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def decisionIn(issue: MergeIssue.Id): Option[MergeDecision] = None
    def count(f: MergeTally => MergeTally): Unit = ()
    def keepLocal(foreign: UUID): Unit = ()

  private val runs = PlanRuns(List(Named("a", answers = false), Named("b", answers = true), Named("c", answers = true)))
  private val issue =
    MergeIssue(
      MergeIssue.Id("E-00000000"),
      MergeIssue.Kind.LabelTaken,
      MergeIssue.Severity.Blocking,
      api.exchange.Bundle.Kind.Event,
      MergeIssue.Title("x"),
      None,
      None,
      Nil,
      Nil
    )

  test("the stages that transform or collect run every kind in order"):
    assertEquals(runs.localized(BundleEntities(), Map.empty).aliases.map(_.alias), List("a", "b", "c"))
    assertEquals(runs.notices, List("a notice", "b notice", "c notice").map(PairQuestions.InfoLine(_)))
    assertEquals(
      runs.bodiesWanted(Nil),
      Set("a", "b", "c").map(api.exchange.Bundle.Source.BodySha256(_))
    )
    assertEquals(runs.dropped, Set(uid("a"), uid("b"), uid("c")))

  test("the stages that ask take the first kind that answers"):
    assertEquals(runs.ownerSource(uid("x"), api.exchange.Bundle.Kind.Attestation), Some(uid("b")))
    assertEquals(
      runs.argument(issue, MergeDecision(MergeDecision.Kind.RenameRight, Some(MergeDecision.Argument("y")))),
      Some(Left(Problem.Invalid.Missing.here(Problem.Field("b"))))
    )
    assert(runs.pairIssue(MergeIssue.Kind.AttestationUnanchored))
    assert(!runs.pairIssue(MergeIssue.Kind.TitleDiffers))
    assertEquals(PlanRuns(Nil).ownerSource(uid("x"), api.exchange.Bundle.Kind.Attestation), None)
    assertEquals(runs.bodyIssue(uid("x")), Some(MergeIssue.Id("b")))
    assertEquals(
      runs.citekeyArgument(issue, MergeDecision(MergeDecision.Kind.Map, Some(MergeDecision.Argument("y")))),
      Some(Left(Problem.Invalid.Missing.here(Problem.Field("b citekey"))))
    )
    assertEquals(
      PlanRuns(Nil).citekeyArgument(issue, MergeDecision(MergeDecision.Kind.Map, Some(MergeDecision.Argument("y")))),
      None
    )

  test("the sources' fold and rename reach every kind; the plan passes through each in turn"):
    seen.clear()
    val s = api.exchange.Bundle.Source(
      uid("s"),
      uid("o"),
      api.library.Citekey("a2000_x"),
      Title("X"),
      Nil,
      None,
      None,
      None,
      Source.Status.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None,
      api.exchange.Bundle.Source.AddedDate("2026-01-01"),
      None,
      None,
      None,
      api.exchange.Bundle.Source.UpdatedAt("2026-01-01T00:00:00.000000Z")
    )
    assert(runs.foldSources(s, s, Citekey("a2000_x"), NoFolds), "c keeps the bundle's body")
    assert(!PlanRuns(List(Named("a", answers = false))).foldSources(s, s, Citekey("a2000_x"), NoFolds))
    runs.renamed(uid("s"), Citekey("a2000_y"))
    assertEquals(
      seen.toList,
      List("a fold", "b fold", "c fold", "a fold", "a renamed a2000_y", "b renamed a2000_y", "c renamed a2000_y")
    )
    val inst = api.exchange.Bundle.Instance(uid("i"), None)
    val plan =
      MergePlan(
        uid("p"),
        inst,
        inst,
        MergePlan.FormatVersion(1),
        MergePlan.SchemaVersion("1"),
        None,
        MergePlan.PlanHash("h"),
        MatchCounts(),
        PlanMatches.empty,
        Nil,
        Nil,
        Nil,
        Nil,
        MergePlan.Resolved(0)
      )
    assertEquals(runs.planned(plan).info, List("a", "b", "c").map(MergePlan.Info(_)))
