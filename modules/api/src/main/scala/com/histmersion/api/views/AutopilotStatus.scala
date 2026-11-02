package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Autopilot.{ApprovalRule, DrainInfo, FocusInfo, GoalProgress, ManagerBrief}
import com.histmersion.api.automation.{Autopilot, AutopilotMarkdown}
import com.histmersion.api.library.{AcquireChecks, AcquireChecksMarkdown, UpstreamQuota, UpstreamQuotaMarkdown}
import com.histmersion.api.given

/** The autopilot as `GET /api/agents/autopilot` shows it: the automation's [[Autopilot.EngineStatus]], then the
  * library's upstreams' quotas and the acquisition list's re-check.
  */
final case class AutopilotStatus(
    on: Boolean,
    since: Option[String] = None,
    by: Option[String] = None,
    agentsEnabled: Boolean = false,
    managersEnabled: Boolean = false,
    cronsEnabled: Boolean = true,
    approvals: List[ApprovalRule] = Nil,
    ingestedToday: Int = 0,
    ingestCap: Int = 0,
    maxConcurrentRuns: Int = 0,
    lastBackup: Option[String] = None,
    lastLint: Option[String] = None,
    library: List[ManagerBrief] = Nil,
    goals: List[GoalProgress] = Nil,
    changes: List[String] = Nil,
    backlog: Int = 0,
    backlogDays: Option[Int] = None,
    focus: Option[FocusInfo] = None,
    drain: Option[DrainInfo] = None,
    goalPlansWait: Option[String] = None,
    upstreams: List[UpstreamQuota] = Nil,
    /** Absent from an older server. */
    acquireRecheck: Option[AcquireChecks.RecheckStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:

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
  given Schema[AutopilotStatus] = summon[KindlingsSchema[AutopilotStatus]].schema
