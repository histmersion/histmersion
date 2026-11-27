package com.histmersion
package cli

import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.{Goal, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, Origin, Pipeline, RunState}
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The filter options of the three listings — `agent approvals`, `agent runs`, `jobs` — over core [[ListFilters]]: the
  * same names as the REST query (kebab-case here), every list option repeatable or comma-separated, and the server
  * checks the values. The "next page" line repeats them ([[PageCmd]]).
  */
@atBoundary(BoundaryKind.CliArgs, "parses the list options of `agent approvals`, `agent runs` and `jobs`")
object ListOpts:

  /** The help lines that name a vocabulary. */
  object Help:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    val approvalState: String =
      s"Only approvals in this state: ${(ApprovalState.values.map(_.wire.value) :+ "all").mkString("|")} (default open)"
    val approvalKind: String = s"Only these kinds: ${ApprovalKind.values.map(_.wire).mkString("|")}"
    val decidedBy: String = s"Only those decided by: ${ListFilters.DecidedBy.values.map(_.wire).mkString("|")}"
    val runStatus: String =
      s"Only runs in these states: ${RunState.values.map(_.wire).mkString("|")} (waiting = for an approval)"
    val pipeline: String = s"Only these pipelines: ${Pipeline.values.map(_.wire).mkString("|")}"
    val origin: String = s"Only runs started by: ${Origin.values.map(_.wire).mkString("|")}"
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    val jobStatus: String =
      s"Only jobs in these states: ${JobState.values.map(_.wire).mkString("|")} (an agent run's too: ${RunState.values.map(_.wire.value).filterNot(JobState.values.map(_.wire.value).contains).mkString("|")})"

  def approvalFilter(
      state: Option[String],
      kind: List[String],
      subject: Option[String],
      goal: Option[String],
      decidedBy: List[String]
  ): ListFilters.ApprovalFilter =
    ListFilters.ApprovalFilter(
      state.map(ListFilters.ApprovalFilter.State(_)),
      kind.map(ListFilters.ApprovalFilter.Kind(_)),
      subject.map(ListFilters.ApprovalFilter.Subject(_)),
      goal.map(Goal.Slug(_)),
      decidedBy.map(ListFilters.ApprovalFilter.DecidedBy(_))
    )

  def runFilter(
      status: List[String],
      pipeline: List[String],
      manager: List[String],
      goal: Option[String],
      model: List[String],
      provider: List[String],
      origin: List[String],
      since: Option[String],
      until: Option[String],
      subject: Option[String]
  ): ListFilters.RunFilter =
    ListFilters.RunFilter(
      status.map(ListFilters.RunFilter.Status(_)),
      pipeline.map(ListFilters.RunFilter.Pipeline(_)),
      manager.map(ListFilters.RunFilter.Manager(_)),
      goal.map(Goal.Slug(_)),
      model.map(ListFilters.RunFilter.Model(_)),
      provider.map(ListFilters.RunFilter.Provider(_)),
      origin.map(ListFilters.RunFilter.Origin(_)),
      since.map(ListFilters.RunFilter.Since(_)),
      until.map(ListFilters.RunFilter.Until(_)),
      subject.map(ListFilters.RunFilter.Subject(_))
    )

  def jobFilter(
      status: List[String],
      kind: List[String],
      since: Option[String],
      until: Option[String],
      subject: Option[String]
  ): ListFilters.JobFilter =
    ListFilters.JobFilter(
      status.map(ListFilters.JobFilter.Status(_)),
      kind.map(ListFilters.JobFilter.Kind(_)),
      since.map(ListFilters.JobFilter.Since(_)),
      until.map(ListFilters.JobFilter.Until(_)),
      subject.map(ListFilters.JobFilter.Subject(_))
    )
