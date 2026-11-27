package com.histmersion
package api.views
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Autopilot.{ApprovalRule, DrainInfo, FocusInfo, GoalProgress, ManagerBrief}
import com.histmersion.api.automation.{Autopilot, AutopilotMarkdown}
import com.histmersion.api.library.{AcquireChecksMarkdown, UpstreamQuota, UpstreamQuotaMarkdown}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Acquire
import com.histmersion.lint.{tolerated, LintRule}

/** The autopilot as `GET /api/agents/autopilot` shows it: the automation's [[Autopilot.EngineStatus]], then the
  * library's upstreams' quotas and the acquisition list's re-check.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class AutopilotStatus(
    on: AutopilotStatus.On,
    since: Option[AutopilotStatus.Since] = None,
    by: Option[AutopilotStatus.By] = None,
    agentsEnabled: AutopilotStatus.AgentsEnabled = AutopilotStatus.AgentsEnabled(false),
    managersEnabled: AutopilotStatus.ManagersEnabled = AutopilotStatus.ManagersEnabled(false),
    cronsEnabled: AutopilotStatus.CronsEnabled = AutopilotStatus.CronsEnabled(true),
    approvals: List[ApprovalRule] = Nil,
    ingestedToday: AutopilotStatus.IngestedToday = AutopilotStatus.IngestedToday(0),
    ingestCap: AutopilotStatus.IngestCap = AutopilotStatus.IngestCap(0),
    maxConcurrentRuns: AutopilotStatus.MaxConcurrentRuns = AutopilotStatus.MaxConcurrentRuns(0),
    lastBackup: Option[AutopilotStatus.LastBackup] = None,
    lastLint: Option[AutopilotStatus.LastLint] = None,
    library: List[ManagerBrief] = Nil,
    goals: List[GoalProgress] = Nil,
    changes: List[AutopilotStatus.Changes] = Nil,
    backlog: AutopilotStatus.Backlog = AutopilotStatus.Backlog(0),
    backlogDays: Option[AutopilotStatus.BacklogDays] = None,
    focus: Option[FocusInfo] = None,
    drain: Option[DrainInfo] = None,
    goalPlansWait: Option[AutopilotStatus.GoalPlansWait] = None,
    upstreams: List[UpstreamQuota] = Nil,
    /** Absent from an older server. */
    acquireRecheck: Option[Acquire.Checks.RecheckStatus] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:

  /** The automation's part of the page. */
  def engine: Autopilot.EngineStatus = Autopilot.EngineStatus(
    on,
    since,
    by,
    agentsEnabled,
    managersEnabled,
    cronsEnabled,
    approvals,
    ingestedToday,
    ingestCap,
    maxConcurrentRuns,
    lastBackup,
    lastLint,
    library,
    goals,
    changes,
    backlog,
    backlogDays,
    focus,
    drain,
    goalPlansWait
  )

  /** The page in Markdown: the automation's status with the library's lines in their places. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown: String =
    AutopilotMarkdown.status(
      engine,
      ingestedBy = acquireRecheck
        .filter(_.fetchedToday.value > 0)
        .fold("")(r => s", ${r.fetchedToday} of them by the acquisition list's re-check"),
      beforeBacklog = upstreams.map(q => (s"- ${UpstreamQuotaMarkdown.line(q)}")),
      afterBacklog =
        (acquireRecheck.map(r => s"- ${AcquireChecksMarkdown.line(r)}").toList).map(refactorValue8 => (refactorValue8))
    )

object AutopilotStatus:
  export Autopilot.EngineStatus.{
    AgentsEnabled,
    Backlog,
    BacklogDays,
    By,
    Changes,
    CronsEnabled,
    GoalPlansWait,
    IngestCap,
    IngestedToday,
    LastBackup,
    LastLint,
    ManagersEnabled,
    MaxConcurrentRuns,
    On,
    Since
  }
