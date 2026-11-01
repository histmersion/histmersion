package com.histmersion.util.data.exchange

import com.histmersion.api.{BundleAlias, BundleSource, MergeDecision, MergeIssue}
import com.histmersion.files.BundleEntities
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind, IssueSeverity}

import java.util.UUID

/** Several kinds' runs of a plan as one: each stage in the given order; the first answer wins where one is asked for. */
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
