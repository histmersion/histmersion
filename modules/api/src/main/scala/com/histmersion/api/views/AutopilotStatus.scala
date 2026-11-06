package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Autopilot.{ApprovalRule, DrainInfo, FocusInfo, GoalProgress, ManagerBrief}
import com.histmersion.api.automation.{Autopilot, AutopilotMarkdown}
import com.histmersion.api.library.{AcquireChecksMarkdown, UpstreamQuota, UpstreamQuotaMarkdown}
import com.histmersion.api.given
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
  type On = com.histmersion.api.automation.Autopilot.EngineStatus.On
  val On: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.On.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.On
  type Since = com.histmersion.api.automation.Autopilot.EngineStatus.Since
  val Since: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Since.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Since
  type By = com.histmersion.api.automation.Autopilot.EngineStatus.By
  val By: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.By.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.By
  type AgentsEnabled = com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled
  val AgentsEnabled: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.AgentsEnabled
  type ManagersEnabled = com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled
  val ManagersEnabled: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.ManagersEnabled
  type CronsEnabled = com.histmersion.api.automation.Autopilot.EngineStatus.CronsEnabled
  val CronsEnabled: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.CronsEnabled.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.CronsEnabled
  type IngestedToday = com.histmersion.api.automation.Autopilot.EngineStatus.IngestedToday
  val IngestedToday: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestedToday.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestedToday
  type IngestCap = com.histmersion.api.automation.Autopilot.EngineStatus.IngestCap
  val IngestCap: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestCap.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.IngestCap
  type MaxConcurrentRuns = com.histmersion.api.automation.Autopilot.EngineStatus.MaxConcurrentRuns
  val MaxConcurrentRuns: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.MaxConcurrentRuns.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.MaxConcurrentRuns
  type LastBackup = com.histmersion.api.automation.Autopilot.EngineStatus.LastBackup
  val LastBackup: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.LastBackup.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.LastBackup
  type LastLint = com.histmersion.api.automation.Autopilot.EngineStatus.LastLint
  val LastLint: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.LastLint.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.LastLint
  type Changes = com.histmersion.api.automation.Autopilot.EngineStatus.Changes
  val Changes: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Changes.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Changes
  type Backlog = com.histmersion.api.automation.Autopilot.EngineStatus.Backlog
  val Backlog: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Backlog.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.Backlog
  type BacklogDays = com.histmersion.api.automation.Autopilot.EngineStatus.BacklogDays
  val BacklogDays: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.BacklogDays.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.BacklogDays
  type GoalPlansWait = com.histmersion.api.automation.Autopilot.EngineStatus.GoalPlansWait
  val GoalPlansWait: _root_.com.histmersion.api.automation.Autopilot.EngineStatus.GoalPlansWait.type = _root_.com.histmersion.api.automation.Autopilot.EngineStatus.GoalPlansWait
