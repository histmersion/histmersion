package com.histmersion.server

import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}
import com.histmersion.api.automation.ListFilters.{ApprovalFilter, DecidedBy, RunFilter}

import java.time.Instant
import com.histmersion.api.Api

/** The engine's list filters a client sent (core [[ListFilters]]: the approvals' and the runs'; the jobs' are
  * `util.jobs.JobListing.query`), checked and turned into the repos' queries: a value outside a closed vocabulary (a
  * state, a kind of approval, a pipeline, an origin) is a 400 naming the words that are allowed, and a time bound is
  * read once, here, against `now`. Free vocabularies (job kinds, managers, models) pass as they are. Pure.
  */
object ListQueries:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def bad(m: com.histmersion.server.ListQueries.Problem): Api.Error = Api.Error(m)

  /** No `state` = the open ones (what the list always showed); `all` = every state. */
  def approvals(f: ApprovalFilter): Either[Api.Error, com.histmersion.domain.automation.approvals.ApprovalQuery] =
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

  def runs(f: RunFilter, now: Instant): Either[Api.Error, com.histmersion.domain.automation.agents.RunQuery] =
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
