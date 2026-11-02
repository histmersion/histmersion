package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.{
  BundleAlias,
  BundleInstance,
  BundleSource,
  MatchCounts,
  MergeDecision,
  MergeIssue,
  MergePlan
}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind, IssueSeverity}

import java.util.UUID
import com.histmersion.api.exchange.PlanMatches

/** Several kinds' runs of a plan as one: each stage in the given order; the first answer wins where one is asked for.
  */
class PlanRunsSuite extends munit.FunSuite:

  private def uid(s: String) = UUID.nameUUIDFromBytes(s.getBytes)

  final private class Named(name: String, answers: Boolean) extends PlanRun:
    override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
      es.copy(aliases = es.aliases :+ BundleAlias(name, uid(name), "2026-01-01T00:00:00.000000Z"))
    override def notices: List[String] = List(s"$name notice")
    override def ownerSource(foreign: UUID, kind: BundleKind): Option[UUID] = Option.when(answers)(uid(name))
    override def bodiesWanted(pairs: List[(BundleSource, BundleSource)]): Set[String] = Set(name)
    override def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
      Option.when(answers)(Left(name))
    override def pairIssue(kind: IssueKind): Boolean = answers && kind == IssueKind.AttestationUnanchored
    override def dropped: Set[UUID] = Set(uid(name))
    override def bodyIssue(right: UUID): Option[String] = Option.when(answers)(name)
    override def citekeyArgument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
      Option.when(answers)(Left(s"$name citekey"))
    override def foldSources(right: BundleSource, left: BundleSource, citekey: String, folds: PlanFolds): Boolean =
      seen += s"$name fold"
      answers && name == "c"
    override def renamed(source: UUID, citekey: String): Unit = seen += s"$name renamed $citekey"
    override def planned(plan: MergePlan): MergePlan = plan.copy(info = plan.info :+ name)

  private val seen = scala.collection.mutable.ListBuffer.empty[String]

  /** Folds no run here asks anything of. */
  private object NoFolds extends PlanFolds:
    def input: com.histmersion.domain.MergePlanner.Input = fail("not asked")
    def localized: BundleEntities = BundleEntities()
    def fold(foreign: UUID, here: UUID, kind: BundleKind): Unit = ()
    def foldedTo(foreign: UUID): Option[UUID] = None
    def here(u: UUID): Boolean = false
    def toLocal(u: UUID): UUID = u
    def decisionIn(issue: String): Option[MergeDecision] = None
    def count(f: com.histmersion.api.exchange.MergeTally => com.histmersion.api.exchange.MergeTally): Unit = ()
    def keepLocal(foreign: UUID): Unit = ()

  private val runs = PlanRuns(List(Named("a", answers = false), Named("b", answers = true), Named("c", answers = true)))
  private val issue =
    MergeIssue("E-00000000", IssueKind.LabelTaken, IssueSeverity.Blocking, BundleKind.Event, "x", None, None, Nil, Nil)

  test("the stages that transform or collect run every kind in order"):
    assertEquals(runs.localized(BundleEntities(), Map.empty).aliases.map(_.alias), List("a", "b", "c"))
    assertEquals(runs.notices, List("a notice", "b notice", "c notice"))
    assertEquals(runs.bodiesWanted(Nil), Set("a", "b", "c"))
    assertEquals(runs.dropped, Set(uid("a"), uid("b"), uid("c")))

  test("the stages that ask take the first kind that answers"):
    assertEquals(runs.ownerSource(uid("x"), BundleKind.Attestation), Some(uid("b")))
    assertEquals(runs.argument(issue, MergeDecision(DecisionKind.RenameRight, Some("y"))), Some(Left("b")))
    assert(runs.pairIssue(IssueKind.AttestationUnanchored))
    assert(!runs.pairIssue(IssueKind.TitleDiffers))
    assertEquals(PlanRuns(Nil).ownerSource(uid("x"), BundleKind.Attestation), None)
    assertEquals(runs.bodyIssue(uid("x")), Some("b"))
    assertEquals(runs.citekeyArgument(issue, MergeDecision(DecisionKind.Map, Some("y"))), Some(Left("b citekey")))
    assertEquals(PlanRuns(Nil).citekeyArgument(issue, MergeDecision(DecisionKind.Map, Some("y"))), None)

  test("the sources' fold and rename reach every kind; the plan passes through each in turn"):
    seen.clear()
    val s = BundleSource(
      uid("s"),
      uid("o"),
      "a2000_x",
      "X",
      Nil,
      None,
      None,
      None,
      com.histmersion.values.SourceStatus.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None,
      "2026-01-01",
      None,
      None,
      None,
      "2026-01-01T00:00:00.000000Z"
    )
    assert(runs.foldSources(s, s, "a2000_x", NoFolds), "c keeps the bundle's body")
    assert(!PlanRuns(List(Named("a", answers = false))).foldSources(s, s, "a2000_x", NoFolds))
    runs.renamed(uid("s"), "a2000_y")
    assertEquals(
      seen.toList,
      List("a fold", "b fold", "c fold", "a fold", "a renamed a2000_y", "b renamed a2000_y", "c renamed a2000_y")
    )
    val inst = BundleInstance(uid("i"), None)
    val plan =
      MergePlan(uid("p"), inst, inst, 1, "1", None, "h", MatchCounts(), PlanMatches.empty, Nil, Nil, Nil, Nil, 0)
    assertEquals(runs.planned(plan).info, List("a", "b", "c"))
