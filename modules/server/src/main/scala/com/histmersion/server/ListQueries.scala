package com.histmersion.server

import com.histmersion.api.{ApiError, ListFilters}
import com.histmersion.api.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}
import com.histmersion.api.ListFilters.{ApprovalFilter, DecidedBy, RunFilter}

import java.time.Instant

/** The engine's list filters a client sent (core [[ListFilters]]: the approvals' and the runs'; the jobs' are
  * `util.jobs.JobListing.query`), checked and turned into the repos' queries: a value outside a closed vocabulary (a
  * state, a kind of approval, a pipeline, an origin) is a 400 naming the words that are allowed, and a time bound is
  * read once, here, against `now`. Free vocabularies (job kinds, managers, models) pass as they are. Pure.
  */
object ListQueries:

  private def bad(m: String): ApiError = ApiError(m)

  /** No `state` = the open ones (what the list always showed); `all` = every state. */
  def approvals(f: ApprovalFilter): Either[ApiError, com.histmersion.domain.automation.approvals.ApprovalQuery] =
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
    yield com.histmersion.domain.automation.approvals.ApprovalQuery(
      state,
      kinds.flatMap(ApprovalKind.parse),
      n.subject,
      n.goal,
      by.flatMap(DecidedBy.parse)
    )

  def runs(f: RunFilter, now: Instant): Either[ApiError, com.histmersion.domain.automation.agents.RunQuery] =
    val n = f.normalized
    for
      st <- ListFilters.known("status", n.status, RunState.values.map(_.wire).toSeq).left.map(bad)
      ps <- ListFilters.known("pipeline", n.pipeline, Pipeline.values.map(_.wire).toSeq).left.map(bad)
      os <- ListFilters.known("origin", n.origin, Origin.values.map(_.wire).toSeq).left.map(bad)
      tr <- ListFilters.range(n.since, n.until, now).left.map(bad)
    yield com.histmersion.domain.automation.agents.RunQuery(
      st,
      ps,
      n.manager,
      n.goal,
      n.model,
      n.provider,
      os,
      tr._1,
      tr._2,
      n.subject
    )
