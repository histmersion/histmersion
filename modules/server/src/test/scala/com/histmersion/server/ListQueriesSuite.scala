package com.histmersion
package server

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters.{ApprovalFilter, DecidedBy, JobFilter, RunFilter}

import java.time.Instant
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Agents
import com.histmersion.server.views.JobListView
import com.histmersion.api.jobs.JobState
import com.histmersion.api.jobs.JobKinds

/** The list filters a client sent, checked and turned into the repos' queries (no database). */
class ListQueriesSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-23T12:00:00Z")

  test("approvals: no state is the open ones, `all` every state; kinds and deciders are checked"):
    assertEquals(ListQueries.approvals(ApprovalFilter()).map(_.state), Right(Some(ApprovalState.Open)))
    assertEquals(
      ListQueries.approvals(ApprovalFilter(state = Some(Approval.State.Wire("all")))).map(_.state),
      Right(None)
    )
    val q = ListQueries
      .approvals(
        ApprovalFilter(
          state = Some(Approval.State.Wire("rejected")),
          kind = List(Approval.Kind.Wire("goal-plan,edge")),
          subject = Some(ApprovalFilter.Subject(" tin ")),
          goal = Some(Goal.Slug("g")),
          decidedBy = List(Approval.DecidedBy.Wire("autopilot"))
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
        .approvals(ApprovalFilter(state = Some(Approval.State.Wire("pending"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown state 'pending'"))
    )
    assert(
      ListQueries
        .approvals(ApprovalFilter(kind = List(Approval.Kind.Wire("goal-plan"), Approval.Kind.Wire("bogus"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("kind: unknown list filter term 'bogus'"))
    )
    assert(
      ListQueries
        .approvals(ApprovalFilter(decidedBy = List(Approval.DecidedBy.Wire("robot"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("person, autopilot, auto"))
    )

  test("runs: states, pipelines and origins are checked, managers and models pass, dates are read once"):
    val q = ListQueries
      .runs(
        RunFilter(
          status = List(RunFilter.Status("failed")),
          pipeline = List(RunFilter.Pipeline("review-article")),
          manager = List(RunFilter.Manager("seed:x")),
          model = List(RunFilter.Model("codex:gpt-6")),
          provider = List(RunFilter.Provider("Codex")),
          origin = List(Agents.Origin.Wire("manager")),
          since = Some(RunFilter.Since("7d")),
          until = Some(RunFilter.Until("2026-10-23")),
          subject = Some(RunFilter.Subject("tin"))
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
    assert(
      ListQueries
        .runs(RunFilter(status = List(RunFilter.Status("stuck"))), now)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown list filter term 'stuck'"))
    )
    assert(
      ListQueries
        .runs(RunFilter(pipeline = List(RunFilter.Pipeline("write-novel"))), now)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown list filter term"))
    )
    assert(
      ListQueries
        .runs(RunFilter(origin = List(Agents.Origin.Wire("cron"))), now)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown list filter term"))
    )
    assert(
      ListQueries
        .runs(RunFilter(since = Some(RunFilter.Since("last week"))), now)
        .left
        .exists(_.problems.sentences.mkString("; ").startsWith("since:"))
    )

  test("jobs: states are checked, any kind passes (plugins add their own)"):
    val q = JobListView
      .query(JobFilter(status = List(JobState.Wire("done,failed")), kind = List(JobKinds.Wire("basemap-fetch"))), now)
      .toOption
      .get
    assertEquals((q.status, q.kinds), (List("done", "failed"), List("basemap-fetch")))
    // an agent run is a job too: its states filter the jobs list
    assert(JobListView.query(JobFilter(status = List(JobState.Wire("waiting"))), now).isRight)
    assert(JobListView.query(JobFilter(status = List(JobState.Wire("stuck"))), now).isLeft)
    assert(JobListView.query(JobFilter(until = Some(JobFilter.Until("tomorrow"))), now).isLeft)
