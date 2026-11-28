package com.histmersion
package api.automation.autopilot

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.AgentMode

final case class EngineStatus(
    on: EngineStatus.On,
    since: Option[EngineStatus.Since] = None,
    by: Option[EngineStatus.By] = None,
    agentsEnabled: EngineStatus.AgentSwitch = EngineStatus.AgentSwitch.Disabled,
    managersEnabled: EngineStatus.ManagingSwitch = EngineStatus.ManagingSwitch.Disabled,
    cronsEnabled: EngineStatus.CronsEnabled = EngineStatus.CronsEnabled.Enabled,
    approvals: List[ApprovalRule] = Nil,
    ingestedToday: EngineStatus.IngestedToday = EngineStatus.IngestedToday(0),
    ingestCap: EngineStatus.IngestCap = EngineStatus.IngestCap(0),
    maxConcurrentRuns: EngineStatus.MaxConcurrentRuns = EngineStatus.MaxConcurrentRuns(0),
    lastBackup: Option[EngineStatus.LastBackup] = None,
    lastLint: Option[EngineStatus.LastLint] = None,
    library: List[ManagerBrief] = Nil,
    goals: List[GoalProgress] = Nil,
    changes: List[EngineStatus.Changes] = Nil,
    backlog: EngineStatus.Backlog = EngineStatus.Backlog(0),
    backlogDays: Option[EngineStatus.BacklogDays] = None,
    focus: Option[FocusInfo] = None,
    drain: Option[DrainInfo] = None,
    goalPlansWait: Option[EngineStatus.GoalPlansWait] = None
)

object EngineStatus:
  export AgentMode.Step.Autopilot.On
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
  enum AgentSwitch derives CanEqual:
    case Enabled, Disabled
  object AgentSwitch extends TwoStates[AgentSwitch]:
    extension (agentsEnabled: AgentSwitch) def agentsEnabled: Boolean = agentsEnabled == Enabled
  enum ManagingSwitch derives CanEqual:
    case Enabled, Disabled
  object ManagingSwitch extends TwoStates[ManagingSwitch]:
    extension (managersEnabled: ManagingSwitch) def managersEnabled: Boolean = managersEnabled == Enabled
  enum CronsEnabled derives CanEqual:
    case Enabled, Disabled
  object CronsEnabled extends TwoStates[CronsEnabled]:
    extension (cronsEnabled: CronsEnabled) def cronsEnabled: Boolean = cronsEnabled == Enabled
  type IngestedToday = IngestedToday.T
  object IngestedToday extends Newtype[Int, Pure]
  type IngestCap = IngestCap.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object IngestCap extends Newtype[Int, Pure]
  type MaxConcurrentRuns = MaxConcurrentRuns.T
  object MaxConcurrentRuns extends Newtype[Int, Pure]
  type LastBackup = LastBackup.T
  object LastBackup extends Newtype[String, Pure]
  type LastLint = LastLint.T
  object LastLint extends Newtype[String, Pure]
  type Changes = Changes.T
  object Changes extends Newtype[String, Pure]
  type Backlog = Backlog.T
  object Backlog extends Newtype[Int, Pure]
  type BacklogDays = BacklogDays.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object BacklogDays extends Newtype[Int, Pure]
  type GoalPlansWait = GoalPlansWait.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object GoalPlansWait extends Newtype[String, Pure]

  val CronsOff = "the managers' crons are off (agents.scheduler.enabled = false, HISTMERSION_AGENTS_SCHEDULER): " +
    "each watcher sweeps once and never again, and no seeding manager proposes again — new sources are not looked for"
