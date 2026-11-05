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
    agentsEnabled: AutopilotStatus.AgentsEnabled = false,
    managersEnabled: AutopilotStatus.ManagersEnabled = false,
    cronsEnabled: AutopilotStatus.CronsEnabled = true,
    approvals: List[ApprovalRule] = Nil,
    ingestedToday: AutopilotStatus.IngestedToday = 0,
    ingestCap: AutopilotStatus.IngestCap = 0,
    maxConcurrentRuns: AutopilotStatus.MaxConcurrentRuns = 0,
    lastBackup: Option[AutopilotStatus.LastBackup] = None,
    lastLint: Option[AutopilotStatus.LastLint] = None,
    library: List[ManagerBrief] = Nil,
    goals: List[GoalProgress] = Nil,
    changes: List[AutopilotStatus.Changes] = Nil,
    backlog: AutopilotStatus.Backlog = 0,
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
        .filter(_.fetchedToday > 0)
        .fold("")(r => s", ${r.fetchedToday} of them by the acquisition list's re-check"),
      beforeBacklog = upstreams.map(q => s"- ${UpstreamQuotaMarkdown.line(q)}"),
      afterBacklog = acquireRecheck.map(r => s"- ${AcquireChecksMarkdown.line(r)}").toList
    )

object AutopilotStatus:
  type On = On.T
  object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type By = By.T
  object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentsEnabled = AgentsEnabled.T
  object AgentsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagersEnabled = ManagersEnabled.T
  object ManagersEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CronsEnabled = CronsEnabled.T
  object CronsEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestedToday = IngestedToday.T
  object IngestedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestCap = IngestCap.T
  object IngestCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxConcurrentRuns = MaxConcurrentRuns.T
  object MaxConcurrentRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastBackup = LastBackup.T
  object LastBackup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastLint = LastLint.T
  object LastLint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changes = Changes.T
  object Changes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Backlog = Backlog.T
  object Backlog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogDays = BacklogDays.T
  object BacklogDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlansWait = GoalPlansWait.T
  object GoalPlansWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
