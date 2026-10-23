package com.histmersion.server

import com.histmersion.core.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.core.ListFilters.{ApprovalFilter, DecidedBy, JobFilter, RunFilter}

import java.time.Instant

/** The list filters a client sent, checked and turned into the repos' queries (no database). */
class ListQueriesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("approvals: no state is the open ones, `all` every state; kinds and deciders are checked"):
    assertEquals(ListQueries.approvals(ApprovalFilter()).map(_.state), Right(Some(ApprovalState.Open)))
    assertEquals(ListQueries.approvals(ApprovalFilter(state = Some("all"))).map(_.state), Right(None))
    val q = ListQueries.approvals(ApprovalFilter(state = Some("rejected"), kind = List("goal-plan,edge"),
      subject = Some(" tin "), goal = Some("g"), decidedBy = List("autopilot"))).toOption.get
    assertEquals((q.state, q.kinds, q.subject, q.goal, q.decidedBy),
      (Some(ApprovalState.Rejected), List(ApprovalKind.GoalPlan, ApprovalKind.Edge), Some("tin"), Some("g"), List(DecidedBy.Autopilot)))
    assert(ListQueries.approvals(ApprovalFilter(state = Some("pending"))).left.exists(_.message.contains("unknown state 'pending'")))
    assert(ListQueries.approvals(ApprovalFilter(kind = List("goal-plan", "bogus"))).left.exists(_.message.contains("unknown kind 'bogus'")))
    assert(ListQueries.approvals(ApprovalFilter(decidedBy = List("robot"))).left.exists(_.message.contains("person, autopilot, auto")))

  test("runs: states, pipelines and origins are checked, managers and models pass, dates are read once"):
    val q = ListQueries.runs(RunFilter(status = List("failed"), pipeline = List("review-article"), manager = List("seed:x"),
      model = List("codex:gpt-6"), provider = List("Codex"), origin = List("manager"), since = Some("7d"), until = Some("2026-10-23"),
      subject = Some("tin")), now).toOption.get
    assertEquals((q.status, q.pipelines, q.managers, q.models, q.providers, q.origins, q.q),
      (List("failed"), List("review-article"), List("seed:x"), List("codex:gpt-6"), List("codex"), List("manager"), Some("tin")))
    assertEquals((q.since, q.until), (Some(Instant.parse("2026-10-16T12:00:00Z")), Some(Instant.parse("2026-10-24T00:00:00Z"))))
    assert(ListQueries.runs(RunFilter(status = List("stuck")), now).left.exists(_.message.contains("unknown status")))
    assert(ListQueries.runs(RunFilter(pipeline = List("write-novel")), now).left.exists(_.message.contains("unknown pipeline")))
    assert(ListQueries.runs(RunFilter(origin = List("cron")), now).left.exists(_.message.contains("unknown origin")))
    assert(ListQueries.runs(RunFilter(since = Some("last week")), now).left.exists(_.message.startsWith("since:")))

  test("jobs: states are checked, any kind passes (plugins add their own)"):
    val q = ListQueries.jobs(JobFilter(status = List("done,failed"), kind = List("basemap-fetch")), now).toOption.get
    assertEquals((q.status, q.kinds), (List("done", "failed"), List("basemap-fetch")))
    // an agent run is a job too: its states filter the jobs list
    assert(ListQueries.jobs(JobFilter(status = List("waiting")), now).isRight)
    assert(ListQueries.jobs(JobFilter(status = List("stuck")), now).isLeft)
    assert(ListQueries.jobs(JobFilter(until = Some("tomorrow")), now).isLeft)
