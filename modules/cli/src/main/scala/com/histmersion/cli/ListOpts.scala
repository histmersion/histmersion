package com.histmersion
package cli

import com.histmersion.api.automation.listfilters.ApprovalFilter
import com.histmersion.api.automation.listfilters.RunFilter
import com.histmersion.api.automation.listfilters.JobFilter

import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.{Goal, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, Pipeline, RunState}
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
    val origin: String = s"Only runs started by: ${api.automation.agents.Origin.values.map(_.wire).mkString("|")}"
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    val jobStatus: String =
      s"Only jobs in these states: ${JobState.values.map(_.wire).mkString("|")} (an agent run's too: ${RunState.values.map(_.wire.value).filterNot(JobState.values.map(_.wire.value).contains).mkString("|")})"

  def approvalFilter(
      state: Option[String],
      kind: List[String],
      subject: Option[String],
      goal: Option[String],
      decidedBy: List[String]
  ): ApprovalFilter =
    ApprovalFilter(
      state.map(ApprovalFilter.State(_)),
      kind.map(ApprovalFilter.Kind(_)),
      subject.map(ApprovalFilter.Subject(_)),
      goal.map(Goal.Slug(_)),
      decidedBy.map(ApprovalFilter.DecidedBy(_))
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
  ): RunFilter =
    RunFilter(
      status.map(RunFilter.Status(_)),
      pipeline.map(RunFilter.Pipeline(_)),
      manager.map(RunFilter.Manager(_)),
      goal.map(Goal.Slug(_)),
      model.map(RunFilter.Model(_)),
      provider.map(RunFilter.Provider(_)),
      origin.map(RunFilter.Origin(_)),
      since.map(RunFilter.Since(_)),
      until.map(RunFilter.Until(_)),
      subject.map(RunFilter.Subject(_))
    )

  def jobFilter(
      status: List[String],
      kind: List[String],
      since: Option[String],
      until: Option[String],
      subject: Option[String]
  ): JobFilter =
    JobFilter(
      status.map(JobFilter.Status(_)),
      kind.map(JobFilter.Kind(_)),
      since.map(JobFilter.Since(_)),
      until.map(JobFilter.Until(_)),
      subject.map(JobFilter.Subject(_))
    )
