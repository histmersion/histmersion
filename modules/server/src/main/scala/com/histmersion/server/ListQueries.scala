package com.histmersion
package server
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}
import com.histmersion.api.automation.ListFilters.{ApprovalFilter, DecidedBy, RunFilter}

import java.time.Instant
import com.histmersion.api.Api
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.agents.RunQuery
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The engine's list filters a client sent (core [[ListFilters]]: the approvals' and the runs'; the jobs' are
  * `util.jobs.JobListing.query`), checked and turned into the repos' queries: a value outside a closed vocabulary (a
  * state, a kind of approval, a pipeline, an origin) is a 400 naming the words that are allowed, and a time bound is
  * read once, here, against `now`. Free vocabularies (job kinds, managers, models) pass as they are. Pure.
  */
@movesTo(FeaturePlace.AutomationApprovals)
object ListQueries:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def bad(m: String): Api.Error = Api.Error(Problem.Unclassified(Legacy.Text(m)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def known(what: String, vs: List[String], allowed: Seq[String]): Either[Api.Error, List[String]] =
    ListFilters
      .known(ListFilters.FacetName(what), vs.map(ListFilters.Term(_)), allowed.map(ListFilters.Term(_)))
      .left
      .map(p => bad(p.sentence))
      .map(_.map(_.value))

  /** No `state` = the open ones (what the list always showed); `all` = every state. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def approvals(f: ApprovalFilter): Either[Api.Error, ApprovalQuery] =
    val n = f.normalized
    for
      state <- n.state match
        case None                        => Right(Some(ApprovalState.Open))
        case Some(s) if s.value == "all" => Right(None)
        case Some(s)                     =>
          ApprovalState
            .parse(s)
            .map(Some(_))
            .toRight(
              bad(s"unknown state '$s' (one of: ${(ApprovalState.values.map(_.wire.value) :+ "all").mkString(", ")})")
            )
      kinds <- known("kind", n.kind.map(_.value), ApprovalKind.values.map(_.wire.value).toSeq)
      by <- known("decidedBy", n.decidedBy.map(_.value), DecidedBy.values.map(_.wire.value).toSeq)
    yield ApprovalQuery(
      state,
      kinds.flatMap(k => ApprovalKind.parse(ApprovalKind.Wire(k))),
      n.subject.map(s => ApprovalQuery.Subject(s.value)),
      n.goal.map(g => ApprovalQuery.Goal(g.value)),
      by.flatMap(b => DecidedBy.parse(DecidedBy.Wire(b)))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def runs(f: RunFilter, now: Instant): Either[Api.Error, RunQuery] =
    import com.histmersion.domain.automation.agents.RunQuery
    val n = f.normalized
    for
      st <- known("status", n.status.map(_.value), RunState.values.map(_.wire.value).toSeq)
      ps <- known("pipeline", n.pipeline.map(_.value), Pipeline.values.map(_.wire.value).toSeq)
      os <- known("origin", n.origin.map(_.value), Origin.values.map(_.wire.value).toSeq)
      tr <- ListFilters
        .range(n.since.map(v => ListFilters.TimeBound(v.value)), n.until.map(v => ListFilters.TimeBound(v.value)), now)
        .left
        .map(p => bad(p.sentence))
    yield RunQuery(
      st.map(RunQuery.Status(_)),
      ps.map(RunQuery.Pipelines(_)),
      n.manager.map(m => RunQuery.Managers(m.value)),
      n.goal.map(g => RunQuery.Goal(g.value)),
      n.model.map(m => RunQuery.Models(m.value)),
      n.provider.map(p => RunQuery.Providers(p.value)),
      os.map(RunQuery.Origins(_)),
      tr._1,
      tr._2,
      n.subject.map(s => RunQuery.Q(s.value))
    )
