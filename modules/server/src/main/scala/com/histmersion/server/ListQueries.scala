package com.histmersion.server

import com.histmersion.api.{ApiError, JobState, ListFilters}
import com.histmersion.api.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}
import com.histmersion.api.ListFilters.{ApprovalFilter, DecidedBy, JobFilter, RunFilter}
import com.histmersion.db.{AgentRepo, JobRepo}

import java.time.Instant

/** The list filters a client sent (core [[ListFilters]]), checked and turned into the repos' queries: a value outside a
  * closed vocabulary (a state, a kind of approval, a pipeline, an origin) is a 400 naming the words that are allowed,
  * and a time bound is read once, here, against `now`. Free vocabularies (job kinds, managers, models) pass as they
  * are. Pure.
  */
object ListQueries:

  private def bad(m: String): ApiError = ApiError(m)

  /** No `state` = the open ones (what the list always showed); `all` = every state. */
  def approvals(f: ApprovalFilter): Either[ApiError, AgentRepo.ApprovalQuery] =
    val n = f.normalized
    for
      state <- n.state match
        case None        => Right(Some(ApprovalState.Open))
        case Some("all") => Right(None)
        case Some(s)     =>
          ApprovalState
            .parse(s)
            .map(Some(_))
            .toRight(bad(s"unknown state '$s' (one of: ${(ApprovalState.values.map(_.wire) :+ "all").mkString(", ")})"))
      kinds <- ListFilters.known("kind", n.kind, ApprovalKind.values.map(_.wire).toSeq).left.map(bad)
      by <- ListFilters.known("decidedBy", n.decidedBy, DecidedBy.values.map(_.wire).toSeq).left.map(bad)
    yield AgentRepo.ApprovalQuery(
      state,
      kinds.flatMap(ApprovalKind.parse),
      n.subject,
      n.goal,
      by.flatMap(DecidedBy.parse)
    )

  def runs(f: RunFilter, now: Instant): Either[ApiError, AgentRepo.RunQuery] =
    val n = f.normalized
    for
      st <- ListFilters.known("status", n.status, RunState.values.map(_.wire).toSeq).left.map(bad)
      ps <- ListFilters.known("pipeline", n.pipeline, Pipeline.values.map(_.wire).toSeq).left.map(bad)
      os <- ListFilters.known("origin", n.origin, Origin.values.map(_.wire).toSeq).left.map(bad)
      tr <- ListFilters.range(n.since, n.until, now).left.map(bad)
    yield AgentRepo.RunQuery(st, ps, n.manager, n.goal, n.model, n.provider, os, tr._1, tr._2, n.subject)

  /** The states a job can be in: a background job's, and — the agent runs being jobs too — a run's. */
  val JobStatuses: Seq[String] = (JobState.values.map(_.wire) ++ RunState.values.map(_.wire)).distinct.toSeq

  def jobs(f: JobFilter, now: Instant): Either[ApiError, JobRepo.JobQuery] =
    val n = f.normalized
    for
      st <- ListFilters.known("status", n.status, JobStatuses).left.map(bad)
      tr <- ListFilters.range(n.since, n.until, now).left.map(bad)
    yield JobRepo.JobQuery(st, n.kind, tr._1, tr._2, n.subject)
