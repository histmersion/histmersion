package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters.{ApprovalFilter, DecidedBy, JobFilter, RunFilter}

import java.time.Instant

/** The list filters a client sent, checked and turned into the repos' queries (no database). */
class ListQueriesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("approvals: no state is the open ones, `all` every state; kinds and deciders are checked"):
    assertEquals(ListQueries.approvals(ApprovalFilter()).map(_.state), Right(Some(ApprovalState.Open)))
    assertEquals(ListQueries.approvals(ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire("all")))).map(_.state), Right(None))
    val q = ListQueries
      .approvals(
        ApprovalFilter(
          state = Some(com.histmersion.api.automation.Approval.State.Wire("rejected")),
          kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan,edge")),
          subject = Some(com.histmersion.api.automation.ListFilters.ApprovalFilter.Subject(" tin ")),
          goal = Some(com.histmersion.api.automation.Goal.Slug("g")),
          decidedBy = List(com.histmersion.api.automation.Approval.DecidedBy.Wire("autopilot"))
        )
      )
      .toOption
      .get
    assertEquals(
      (q.state, q.kinds, q.subject, q.goal, q.decidedBy),
      (
        Some(ApprovalState.Rejected),
        List(ApprovalKind.GoalPlan, ApprovalKind.Edge),
        Some("tin"),
        Some("g"),
        List(DecidedBy.Autopilot)
      )
    )
    assert(
      ListQueries
        .approvals(ApprovalFilter(state = Some(com.histmersion.api.automation.Approval.State.Wire("pending"))))
        .left
        .exists(_.message.contains("unknown state 'pending'"))
    )
    assert(
      ListQueries
        .approvals(ApprovalFilter(kind = List(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan"), com.histmersion.api.automation.Approval.Kind.Wire("bogus"))))
        .left
        .exists(_.message.contains("unknown kind 'bogus'"))
    )
    assert(
      ListQueries
        .approvals(ApprovalFilter(decidedBy = List(com.histmersion.api.automation.Approval.DecidedBy.Wire("robot"))))
        .left
        .exists(_.message.contains("person, autopilot, auto"))
    )

  test("runs: states, pipelines and origins are checked, managers and models pass, dates are read once"):
    val q = ListQueries
      .runs(
        RunFilter(
          status = List(com.histmersion.api.automation.ListFilters.RunFilter.Status("failed")),
          pipeline = List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("review-article")),
          manager = List(com.histmersion.api.automation.ListFilters.RunFilter.Manager("seed:x")),
          model = List(com.histmersion.api.automation.ListFilters.RunFilter.Model("codex:gpt-6")),
          provider = List(com.histmersion.api.automation.ListFilters.RunFilter.Provider("Codex")),
          origin = List(com.histmersion.api.automation.Agents.Origin.Wire("manager")),
          since = Some(com.histmersion.api.automation.ListFilters.RunFilter.Since("7d")),
          until = Some(com.histmersion.api.automation.ListFilters.RunFilter.Until("2026-10-23")),
          subject = Some(com.histmersion.api.automation.ListFilters.RunFilter.Subject("tin"))
        ),
        now
      )
      .toOption
      .get
    assertEquals(
      (q.status, q.pipelines, q.managers, q.models, q.providers, q.origins, q.q),
      (
        List("failed"),
        List("review-article"),
        List("seed:x"),
        List("codex:gpt-6"),
        List("codex"),
        List("manager"),
        Some("tin")
      )
    )
    assertEquals(
      (q.since, q.until),
      (Some(Instant.parse("2026-10-16T12:00:00Z")), Some(Instant.parse("2026-10-24T00:00:00Z")))
    )
    assert(ListQueries.runs(RunFilter(status = List(com.histmersion.api.automation.ListFilters.RunFilter.Status("stuck"))), now).left.exists(_.message.contains("unknown status")))
    assert(
      ListQueries
        .runs(RunFilter(pipeline = List(com.histmersion.api.automation.ListFilters.RunFilter.Pipeline("write-novel"))), now)
        .left
        .exists(_.message.contains("unknown pipeline"))
    )
    assert(ListQueries.runs(RunFilter(origin = List(com.histmersion.api.automation.Agents.Origin.Wire("cron"))), now).left.exists(_.message.contains("unknown origin")))
    assert(ListQueries.runs(RunFilter(since = Some(com.histmersion.api.automation.ListFilters.RunFilter.Since("last week"))), now).left.exists(_.message.startsWith("since:")))

  test("jobs: states are checked, any kind passes (plugins add their own)"):
    val q = com.histmersion.server.views.JobListView
      .query(JobFilter(status = List(com.histmersion.api.jobs.JobState.Wire("done,failed")), kind = List(com.histmersion.api.jobs.JobKinds.Wire("basemap-fetch"))), now)
      .toOption
      .get
    assertEquals((q.status, q.kinds), (List("done", "failed"), List("basemap-fetch")))
    // an agent run is a job too: its states filter the jobs list
    assert(com.histmersion.server.views.JobListView.query(JobFilter(status = List(com.histmersion.api.jobs.JobState.Wire("waiting"))), now).isRight)
    assert(com.histmersion.server.views.JobListView.query(JobFilter(status = List(com.histmersion.api.jobs.JobState.Wire("stuck"))), now).isLeft)
    assert(com.histmersion.server.views.JobListView.query(JobFilter(until = Some(com.histmersion.api.automation.ListFilters.JobFilter.Until("tomorrow"))), now).isLeft)
