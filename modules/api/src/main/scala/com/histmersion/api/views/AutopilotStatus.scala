package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Autopilot.{ApprovalRule, DrainInfo, FocusInfo, GoalProgress, ManagerBrief}
import com.histmersion.api.automation.{Autopilot, AutopilotMarkdown}
import com.histmersion.api.library.{AcquireChecksMarkdown, UpstreamQuota, UpstreamQuotaMarkdown}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Acquire

/** The autopilot as `GET /api/agents/autopilot` shows it: the automation's [[Autopilot.EngineStatus]], then the
  * library's upstreams' quotas and the acquisition list's re-check.
  */
final case class AutopilotStatus(
    on: AutopilotStatus.On,
    since: Option[AutopilotStatus.Since] = None,
    by: Option[AutopilotStatus.By] = None,
    agentsEnabled: AutopilotStatus.AgentsEnabled = _root_.com.histmersion.api.views.AutopilotStatus.AgentsEnabled(false),
    managersEnabled: AutopilotStatus.ManagersEnabled = _root_.com.histmersion.api.views.AutopilotStatus.ManagersEnabled(false),
    cronsEnabled: AutopilotStatus.CronsEnabled = _root_.com.histmersion.api.views.AutopilotStatus.CronsEnabled(true),
    approvals: List[ApprovalRule] = Nil,
    ingestedToday: AutopilotStatus.IngestedToday = _root_.com.histmersion.api.views.AutopilotStatus.IngestedToday(0),
    ingestCap: AutopilotStatus.IngestCap = _root_.com.histmersion.api.views.AutopilotStatus.IngestCap(0),
    maxConcurrentRuns: AutopilotStatus.MaxConcurrentRuns = _root_.com.histmersion.api.views.AutopilotStatus.MaxConcurrentRuns(0),
    lastBackup: Option[AutopilotStatus.LastBackup] = None,
    lastLint: Option[AutopilotStatus.LastLint] = None,
    library: List[ManagerBrief] = Nil,
    goals: List[GoalProgress] = Nil,
    changes: List[AutopilotStatus.Changes] = Nil,
    backlog: AutopilotStatus.Backlog = _root_.com.histmersion.api.views.AutopilotStatus.Backlog(0),
    backlogDays: Option[AutopilotStatus.BacklogDays] = None,
    focus: Option[FocusInfo] = None,
    drain: Option[DrainInfo] = None,
    goalPlansWait: Option[AutopilotStatus.GoalPlansWait] = None,
    upstreams: List[UpstreamQuota] = Nil,
    /** Absent from an older server. */
    acquireRecheck: Option[Acquire.Checks.RecheckStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

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
  def markdown: String =
    AutopilotMarkdown.status(
      engine,
      ingestedBy = acquireRecheck
        .filter(_.fetchedToday.value > 0)
        .fold("")(r => s", ${r.fetchedToday} of them by the acquisition list's re-check"),
      beforeBacklog = upstreams.map(q => (s"- ${UpstreamQuotaMarkdown.line(q)}")),
      afterBacklog = (acquireRecheck.map(r => s"- ${AcquireChecksMarkdown.line(r)}").toList).map(refactorValue8 => (refactorValue8))
    )

object AutopilotStatus:
  export com.histmersion.api.automation.Autopilot.EngineStatus.{On, Since, By, AgentsEnabled, ManagersEnabled, CronsEnabled, IngestedToday, IngestCap, MaxConcurrentRuns, LastBackup, LastLint, Changes, Backlog, BacklogDays, GoalPlansWait}
