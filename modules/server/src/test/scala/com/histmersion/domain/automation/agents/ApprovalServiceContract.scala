package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters.DecidedBy
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.util.jobs.{Job, RunRow}

import java.time.Instant
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.util.database.Jsonb
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.TestPermits.given

/** The contract the approvals keep: a source's open proposal is not doubled; a run's step approval opens once; a
  * decision is taken once; the lists' pages, filters and facets; the digest's, the autopilot's and the scheduler's
  * reads.
  */
abstract class ApprovalServiceContract extends EngineStoreSuite:
  private def a(s: EngineStores) = s.approvals
  private def idOf(i: ApprovalInfo): Approval.Id = Approval.Id(i.id.value)
  private def propose(
      s: EngineStores,
      p: Proposal,
      current: Option[Source.Status],
      st: ApprovalState = ApprovalState.Open,
      result: Option[ApprovalRecord.Result] = None
  ) =
    s.sourceApprovals.record(
      None,
      p.kind,
      p.citekey,
      io.scalaland.chimney.Transformer
        .derive[Option[Source.Status], Option[domain.library.Source.Status]]
        .transform(p.status),
      io.scalaland.chimney.Transformer
        .derive[Option[Source.Status], Option[domain.library.Source.Status]]
        .transform(current),
      io.scalaland.chimney.Transformer
        .derive[Option[Edge.Kind], Option[domain.library.Edge.Kind]]
        .transform(p.edgeKind),
      p.target,
      Jsonb.write(p),
      st,
      result
    )
  private def before(s: EngineStores, p: Proposal) =
    s.sourceApprovals.proposedBefore(
      p.kind,
      p.citekey,
      io.scalaland.chimney.Transformer
        .derive[Option[Source.Status], Option[domain.library.Source.Status]]
        .transform(p.status),
      io.scalaland.chimney.Transformer
        .derive[Option[Edge.Kind], Option[domain.library.Edge.Kind]]
        .transform(p.edgeKind),
      p.target
    )
  private val status = Proposal(
    ApprovalKind.StatusChange,
    Citekey("zz2020_a"),
    Some(Source.Status.Contested),
    evidence = Proposal.Evidence("e")
  )
  private val edge =
    Proposal(ApprovalKind.Edge, Citekey("zz2020_a"), None, Some(Edge.Kind.Refutes), Some(Citekey("zz2021_b")))

  stest("a source's open proposal is not doubled; once decided, it may be proposed again; ever proposed is known") {
    s =>
      val first = propose(s, status, Some(Source.Status.Current)).get
      assertEquals(
        (first.kind, first.citekey, first.status, first.current, first.state, first.evidence, first.decidedAt),
        ("status-change", "zz2020_a", Some("contested"), Some("current"), "open", "e", None)
      )
      assertEquals(propose(s, status, None), None, "identical and open")
      assert(propose(s, edge, None).isDefined, "another change")
      assertEquals(
        a(s).payload(idOf(first)).map(p => Jsonb.JsonText(p.value)).flatMap(Jsonb.read[Proposal]),
        Some(status)
      )
      assert(before(s, status))
      assert(!before(s, status.modify(_.citekey).setTo(Citekey("zz2020_z"))))
      val auto =
        propose(s, status, None, ApprovalState.Approved, Some(ApprovalRecord.Result("applied automatically"))).get
      assertEquals(
        (auto.state, auto.result, auto.decidedAt.isDefined),
        ("approved", Some("applied automatically"), true)
      )
      assert(a(s).decide(idOf(first), ApprovalState.Rejected, Some(Approval.Note("no")), None))
      assert(!a(s).decide(idOf(first), ApprovalState.Approved, None, None), "decided once")
      assert(propose(s, status, None).isDefined, "proposed again once decided")
      a(s).settle(idOf(auto), ApprovalState.Failed, Approval.Outcome("it failed"))
      assertEquals(a(s).approval(idOf(auto)).map(x => (x.state, x.result)), Some(("failed", Some("it failed"))))
      assertEquals(a(s).approval(Approval.Id(-1)), None)
  }

  stest("a run's step approval opens once; its approvals oldest first; a cancelled run's open step ones rejected") {
    s =>
      val run = Job.Id.assume(s.run(RunRow.Target("zz2020_a"), goal = Some(RunRow.Goal("zz-goal"))).value)
      val plan = a(s).openForStep(
        run,
        ApprovalService.StepSeq(2),
        ApprovalKind.GoalPlan,
        ApprovalQuery.Subject("zz-goal"),
        ApprovalService.Payload("""{"goal":"zz-goal"}""")
      )
      assertEquals(
        a(s)
          .openForStep(
            run,
            ApprovalService.StepSeq(2),
            ApprovalKind.GoalPlan,
            ApprovalQuery.Subject("zz-goal"),
            ApprovalService.Payload("{}")
          )
          .id,
        plan.id,
        "once per step"
      )
      assertEquals(a(s).forStep(run, ApprovalService.StepSeq(2)).map(_.id), Some(plan.id))
      assertEquals(a(s).forStep(run, ApprovalService.StepSeq(3)), None)
      val filter = a(s).openSubject(
        Some(run),
        ApprovalKind.ArticleFilter,
        ApprovalQuery.Subject("zz-art"),
        ApprovalService.Payload("""{"filter":"x"}""")
      )
      val again = a(s).openSubject(
        Some(run),
        ApprovalKind.ArticleFilter,
        ApprovalQuery.Subject("zz-art"),
        ApprovalService.Payload("""{"filter":"x"}""")
      )
      assertNotEquals(filter.id, again.id, "a subject's proposal is not deduplicated")
      assertEquals(a(s).approvalsOf(run).map(_.id), List(plan.id, filter.id, again.id))
      assertEquals(a(s).approvalsOf(run, Some(ApprovalKind.GoalPlan)).map(_.id), List(plan.id))
      assertEquals(
        a(s).openLight().map(o => (o.id.value: Long, o.kind, o.subject)),
        List(
          (plan.id.value: Long, "goal-plan", Some("zz-goal")),
          (filter.id.value: Long, "article-filter", Some("zz-art")),
          (again.id.value: Long, "article-filter", Some("zz-art"))
        )
      )
      assertEquals(a(s).openCount(ApprovalKind.ArticleFilter), 2)
      assertEquals(a(s).openBySubject(), Map("zz-goal" -> 1, "zz-art" -> 2))
      assertEquals(
        a(s).openOfKinds(List(ApprovalKind.GoalPlan, ApprovalKind.ArticleFilter), ApprovalQuery.Limit(2)).map(_.id),
        List(plan.id, filter.id)
      )
      assertEquals(
        a(s).closeForRun(run, Problem.Unclassified(Legacy.Text("rejected: the run was cancelled"))),
        1,
        "only the step's"
      )
      assertEquals(
        a(s).approval(idOf(plan)).map(x => (x.state, x.result)),
        Some(("rejected", Some("rejected: the run was cancelled")))
      )
      assertEquals(a(s).closeForRun(run, Problem.Unclassified(Legacy.Text("again"))), 0)
  }

  stest("what the autopilot approved since a moment, and who decided each") { s =>
    val since = Instant.parse("2020-01-01T00:00:00Z")
    val p1 =
      a(s).openSubject(None, ApprovalKind.GoalPlan, ApprovalQuery.Subject("zz-g1"), ApprovalService.Payload("{}"))
    val p2 =
      a(s).openSubject(None, ApprovalKind.GoalPlan, ApprovalQuery.Subject("zz-g2"), ApprovalService.Payload("{}"))
    val p3 =
      a(s).openSubject(None, ApprovalKind.GoalPlan, ApprovalQuery.Subject("zz-g3"), ApprovalService.Payload("{}"))
    val p4 = a(s).openSubject(
      None,
      ApprovalKind.GoalPlan,
      ApprovalQuery.Subject("zz-g4"),
      ApprovalService.Payload("{}"),
      ApprovalState.Approved,
      Some(ApprovalService.SubjectOutcome("applied automatically: x"))
    )
    assert(
      a(s).decide(idOf(p1), ApprovalState.Approved, Some(Approval.Note("autopilot: approved (checks pass)")), None)
    )
    assert(a(s).decide(idOf(p2), ApprovalState.Approved, Some(Approval.Note("looks right")), None))
    assertEquals(a(s).autoApprovedSince(ApprovalKind.GoalPlan, since).map(_.id), List(p1.id))
    assertEquals(a(s).autoApprovedSince(ApprovalKind.GoalPlan, Instant.parse("2099-01-01T00:00:00Z")), Nil)
    val (_, facets) = a(s).approvalFacets(ApprovalQuery())
    assertEquals(facets.toMap.apply("decidedBy").toMap, Map("autopilot" -> 1, "person" -> 1, "auto" -> 1))
    assertEquals(
      a(s)
        .approvalPage(ApprovalQuery(decidedBy = List(DecidedBy.Auto)), ApprovalQuery.Limit(10), None, None)
        .map(_._1.map(_.id)),
      Right(List(p4.id))
    )
    assertEquals(a(s).approvalCounts(), List("approved" -> 3, "open" -> 1))
    assertEquals(a(s).approvals(Some(ApprovalState.Open), ApprovalQuery.Limit(10)).map(_.id), List(p3.id))
  }

  stest("pages newest first, with cursors; filters by kind, text (the run's goal and manager too), goal; facets") { s =>
    val run = Job.Id.assume(
      s.run(
        RunRow.Target("zz2020_x"),
        goal = Some(RunRow.Goal("zz-tin")),
        manager = Some(RunRow.ManagerId("watch:zz-tin"))
      ).value
    )
    val ids = (1 to 5).toList.map(i =>
      a(s)
        .openSubject(
          None,
          ApprovalKind.ArticlePlan,
          ApprovalQuery.Subject(s"zz-subj-$i"),
          ApprovalService.Payload("{}")
        )
        .id
    )
    val src = s.sourceApprovals
      .record(
        Some(run),
        ApprovalKind.Edge,
        Citekey("zz2020_x"),
        None,
        None,
        Some(domain.library.Edge.Kind.Refutes),
        Some(Citekey("zz2019_y")),
        Jsonb.JsonText("{}"),
        ApprovalState.Open,
        None
      )
      .get
    val Right((first, more)) = a(s).approvalPage(None, ApprovalQuery.Limit(2), None, None): @unchecked
    assertEquals((first.map(_.id), more), (List(src.id, ids(4)), true))
    val Right((next, more2)) = a(s).approvalPage(
      None,
      ApprovalQuery.Limit(2),
      Some(ApprovalQuery.Cursor(first.last.id.toString)),
      None
    ): @unchecked
    assertEquals((next.map(_.id), more2), (List(ids(3), ids(2)), true))
    val Right((upto, more3)) =
      a(s).approvalPage(None, ApprovalQuery.Limit(10), None, Some(ApprovalQuery.UpTo(ids(3).toString))): @unchecked
    assertEquals((upto.map(_.id), more3), (List(src.id, ids(4), ids(3)), true))
    val Right((last, more4)) =
      a(s).approvalPage(None, ApprovalQuery.Limit(10), Some(ApprovalQuery.Cursor(ids(1).toString)), None): @unchecked
    assertEquals((last.map(_.id), more4), (List(ids(0)), false))
    assert(
      a(s)
        .approvalPage(None, ApprovalQuery.Limit(2), Some(ApprovalQuery.Cursor("nope")), None)
        .left
        .exists(_.isNotFound)
    )
    assert(a(s).approvalPage(None, ApprovalQuery.Limit(2), Some(ApprovalQuery.Cursor("999999")), None).isLeft)
    def page(q: ApprovalQuery) = a(s).approvalPage(q, ApprovalQuery.Limit(50), None, None).map(_._1.map(_.id))
    assertEquals(page(ApprovalQuery(kinds = List(ApprovalKind.Edge))), Right(List(src.id)))
    assertEquals(page(ApprovalQuery(subject = Some(ApprovalQuery.Subject("SUBJ-2")))), Right(List(ids(1))))
    assertEquals(
      page(ApprovalQuery(subject = Some(ApprovalQuery.Subject("Tin")))),
      Right(List(src.id)),
      "the run's goal"
    )
    assertEquals(
      page(ApprovalQuery(subject = Some(ApprovalQuery.Subject("watch:")))),
      Right(List(src.id)),
      "the run's manager"
    )
    assertEquals(page(ApprovalQuery(goal = Some(ApprovalQuery.Goal("zz-tin")))), Right(List(src.id)))
    assertEquals(page(ApprovalQuery(goal = Some(ApprovalQuery.Goal("zz-subj-3")))), Right(List(ids(2))))
    assert(a(s).decide(Approval.Id(ids(0).value), ApprovalState.Rejected, Some(Approval.Note("no")), None))
    val (states, facets) =
      a(s).approvalFacets(ApprovalQuery(state = Some(ApprovalState.Open), kinds = List(ApprovalKind.ArticlePlan)))
    assertEquals(states, List("open" -> 4, "rejected" -> 1))
    assertEquals(facets.toMap.apply("kind"), List("article-plan" -> 4, "edge" -> 1))
    assertEquals(facets.toMap.apply("decidedBy"), Nil, "open ones are decided by nobody")
  }
