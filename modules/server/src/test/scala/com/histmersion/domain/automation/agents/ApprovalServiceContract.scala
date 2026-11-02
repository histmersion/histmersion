package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters.DecidedBy
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.values.{EdgeKind, SourceStatus}

import java.time.Instant

/** The contract the approvals keep: a source's open proposal is not doubled; a run's step approval opens once; a
  * decision is taken once; the lists' pages, filters and facets; the digest's, the autopilot's and the scheduler's
  * reads.
  */
abstract class ApprovalServiceContract extends EngineStoreSuite:
  private def a(s: EngineStores) = s.approvals
  private def propose(
      s: EngineStores,
      p: Proposal,
      current: Option[SourceStatus],
      st: ApprovalState = ApprovalState.Open,
      result: Option[String] = None
  ) =
    a(s).record(
      None,
      p.kind,
      p.citekey,
      p.status,
      current,
      p.edgeKind,
      p.target,
      com.histmersion.util.database.Jsonb.write(p),
      st,
      result
    )
  private def before(s: EngineStores, p: Proposal) =
    a(s).proposedBefore(p.kind, p.citekey, p.status, p.edgeKind, p.target)
  private val status = Proposal(ApprovalKind.StatusChange, "zz2020_a", Some(SourceStatus.Contested), evidence = "e")
  private val edge = Proposal(ApprovalKind.Edge, "zz2020_a", None, Some(EdgeKind.Refutes), Some("zz2021_b"))

  stest("a source's open proposal is not doubled; once decided, it may be proposed again; ever proposed is known") {
    s =>
      val first = propose(s, status, Some(SourceStatus.Current)).get
      assertEquals(
        (first.kind, first.citekey, first.status, first.current, first.state, first.evidence, first.decidedAt),
        ("status-change", "zz2020_a", Some("contested"), Some("current"), "open", "e", None)
      )
      assertEquals(propose(s, status, None), None, "identical and open")
      assert(propose(s, edge, None).isDefined, "another change")
      assertEquals(a(s).payload(first.id).flatMap(com.histmersion.util.database.Jsonb.read[Proposal]), Some(status))
      assert(before(s, status))
      assert(!before(s, status.copy(citekey = "zz2020_z")))
      val auto = propose(s, status, None, ApprovalState.Approved, Some("applied automatically")).get
      assertEquals(
        (auto.state, auto.result, auto.decidedAt.isDefined),
        ("approved", Some("applied automatically"), true)
      )
      assert(a(s).decide(first.id, ApprovalState.Rejected, Some("no"), None))
      assert(!a(s).decide(first.id, ApprovalState.Approved, None, None), "decided once")
      assert(propose(s, status, None).isDefined, "proposed again once decided")
      a(s).settle(auto.id, ApprovalState.Failed, "it failed")
      assertEquals(a(s).approval(auto.id).map(x => (x.state, x.result)), Some(("failed", Some("it failed"))))
      assertEquals(a(s).approval(-1), None)
  }

  stest("a run's step approval opens once; its approvals oldest first; a cancelled run's open step ones rejected") {
    s =>
      val run = s.run("zz2020_a", goal = Some("zz-goal"))
      val plan = a(s).openForStep(run, 2, ApprovalKind.GoalPlan, "zz-goal", """{"goal":"zz-goal"}""")
      assertEquals(a(s).openForStep(run, 2, ApprovalKind.GoalPlan, "zz-goal", "{}").id, plan.id, "once per step")
      assertEquals(a(s).forStep(run, 2).map(_.id), Some(plan.id))
      assertEquals(a(s).forStep(run, 3), None)
      val filter = a(s).openSubject(Some(run), ApprovalKind.ArticleFilter, "zz-art", """{"filter":"x"}""")
      val again = a(s).openSubject(Some(run), ApprovalKind.ArticleFilter, "zz-art", """{"filter":"x"}""")
      assertNotEquals(filter.id, again.id, "a subject's proposal is not deduplicated")
      assertEquals(a(s).approvalsOf(run).map(_.id), List(plan.id, filter.id, again.id))
      assertEquals(a(s).approvalsOf(run, Some(ApprovalKind.GoalPlan)).map(_.id), List(plan.id))
      assertEquals(
        a(s).openLight().map(o => (o.id, o.kind, o.subject)),
        List(
          (plan.id, "goal-plan", Some("zz-goal")),
          (filter.id, "article-filter", Some("zz-art")),
          (again.id, "article-filter", Some("zz-art"))
        )
      )
      assertEquals(a(s).openCount(ApprovalKind.ArticleFilter), 2)
      assertEquals(a(s).openBySubject(), Map("zz-goal" -> 1, "zz-art" -> 2))
      assertEquals(
        a(s).openOfKinds(List(ApprovalKind.GoalPlan, ApprovalKind.ArticleFilter), 2).map(_.id),
        List(plan.id, filter.id)
      )
      assertEquals(a(s).closeForRun(run, "rejected: the run was cancelled"), 1, "only the step's")
      assertEquals(
        a(s).approval(plan.id).map(x => (x.state, x.result)),
        Some(("rejected", Some("rejected: the run was cancelled")))
      )
      assertEquals(a(s).closeForRun(run, "again"), 0)
  }

  stest("what the autopilot approved since a moment, and who decided each") { s =>
    val since = Instant.parse("2020-01-01T00:00:00Z")
    val p1 = a(s).openSubject(None, ApprovalKind.GoalPlan, "zz-g1", "{}")
    val p2 = a(s).openSubject(None, ApprovalKind.GoalPlan, "zz-g2", "{}")
    val p3 = a(s).openSubject(None, ApprovalKind.GoalPlan, "zz-g3", "{}")
    val p4 = a(s).openSubject(
      None,
      ApprovalKind.GoalPlan,
      "zz-g4",
      "{}",
      ApprovalState.Approved,
      Some("applied automatically: x")
    )
    assert(a(s).decide(p1.id, ApprovalState.Approved, Some("autopilot: approved (checks pass)"), None))
    assert(a(s).decide(p2.id, ApprovalState.Approved, Some("looks right"), None))
    assertEquals(a(s).autoApprovedSince(ApprovalKind.GoalPlan, since).map(_.id), List(p1.id))
    assertEquals(a(s).autoApprovedSince(ApprovalKind.GoalPlan, Instant.parse("2099-01-01T00:00:00Z")), Nil)
    val (_, facets) = a(s).approvalFacets(ApprovalQuery())
    assertEquals(facets.toMap.apply("decidedBy").toMap, Map("autopilot" -> 1, "person" -> 1, "auto" -> 1))
    assertEquals(
      a(s).approvalPage(ApprovalQuery(decidedBy = List(DecidedBy.Auto)), 10, None, None).map(_._1.map(_.id)),
      Right(List(p4.id))
    )
    assertEquals(a(s).approvalCounts(), List("approved" -> 3, "open" -> 1))
    assertEquals(a(s).approvals(Some(ApprovalState.Open), 10).map(_.id), List(p3.id))
  }

  stest("pages newest first, with cursors; filters by kind, text (the run's goal and manager too), goal; facets") { s =>
    val run = s.run("zz2020_x", goal = Some("zz-tin"), manager = Some("watch:zz-tin"))
    val ids = (1 to 5).toList.map(i => a(s).openSubject(None, ApprovalKind.ArticlePlan, s"zz-subj-$i", "{}").id)
    val src = a(s)
      .record(
        Some(run),
        ApprovalKind.Edge,
        "zz2020_x",
        None,
        None,
        Some(EdgeKind.Refutes),
        Some("zz2019_y"),
        "{}",
        ApprovalState.Open,
        None
      )
      .get
    val Right((first, more)) = a(s).approvalPage(None, 2, None, None): @unchecked
    assertEquals((first.map(_.id), more), (List(src.id, ids(4)), true))
    val Right((next, more2)) = a(s).approvalPage(None, 2, Some(first.last.id.toString), None): @unchecked
    assertEquals((next.map(_.id), more2), (List(ids(3), ids(2)), true))
    val Right((upto, more3)) = a(s).approvalPage(None, 10, None, Some(ids(3).toString)): @unchecked
    assertEquals((upto.map(_.id), more3), (List(src.id, ids(4), ids(3)), true))
    val Right((last, more4)) = a(s).approvalPage(None, 10, Some(ids(1).toString), None): @unchecked
    assertEquals((last.map(_.id), more4), (List(ids(0)), false))
    assert(a(s).approvalPage(None, 2, Some("nope"), None).left.exists(_.contains("No approval 'nope'")))
    assert(a(s).approvalPage(None, 2, Some("999999"), None).isLeft)
    def page(q: ApprovalQuery) = a(s).approvalPage(q, 50, None, None).map(_._1.map(_.id))
    assertEquals(page(ApprovalQuery(kinds = List(ApprovalKind.Edge))), Right(List(src.id)))
    assertEquals(page(ApprovalQuery(subject = Some("SUBJ-2"))), Right(List(ids(1))))
    assertEquals(page(ApprovalQuery(subject = Some("Tin"))), Right(List(src.id)), "the run's goal")
    assertEquals(page(ApprovalQuery(subject = Some("watch:"))), Right(List(src.id)), "the run's manager")
    assertEquals(page(ApprovalQuery(goal = Some("zz-tin"))), Right(List(src.id)))
    assertEquals(page(ApprovalQuery(goal = Some("zz-subj-3"))), Right(List(ids(2))))
    assert(a(s).decide(ids(0), ApprovalState.Rejected, Some("no"), None))
    val (states, facets) =
      a(s).approvalFacets(ApprovalQuery(state = Some(ApprovalState.Open), kinds = List(ApprovalKind.ArticlePlan)))
    assertEquals(states, List("open" -> 4, "rejected" -> 1))
    assertEquals(facets.toMap.apply("kind"), List("article-plan" -> 4, "edge" -> 1))
    assertEquals(facets.toMap.apply("decidedBy"), Nil, "open ones are decided by nobody")
  }
