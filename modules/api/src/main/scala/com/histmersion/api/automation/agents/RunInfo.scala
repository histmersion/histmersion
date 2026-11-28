package com.histmersion
package api.automation.agents

import com.histmersion.util.problem.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.agents.RunParams
import com.histmersion.api.automation.agents.PauseReason
import com.histmersion.api.automation.Agents.Role
import com.histmersion.api.automation.Run
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.managers.ManagerItem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wording
import com.histmersion.api.time.IsoLocal
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.Origin

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunInfo(
    id: RunInfo.Id,
    pipeline: RunInfo.Pipeline,
    target: RunInfo.Target,
    status: RunInfo.Status,
    progress: Option[RunInfo.Progress] = None,
    error: Option[Problem] = None,
    model: Option[RunInfo.Model] = None,
    usage: Usage = Usage.Zero,
    costUsd: RunInfo.CostUsd = RunInfo.CostUsd(0.0),
    budgetUsd: RunInfo.BudgetUsd = RunInfo.BudgetUsd(0.0),
    budgetTokens: RunInfo.BudgetTokens = RunInfo.BudgetTokens((0).toLong),
    warnings: List[RunInfo.Warnings] = Nil,
    createdAt: RunInfo.CreatedAt = RunInfo.CreatedAt(""),
    updatedAt: RunInfo.UpdatedAt = RunInfo.UpdatedAt(""),
    targetKind: RunInfo.TargetKind = RunInfo.TargetKind(""),
    params: RunParams = RunParams(),
    acquireItem: Option[QueueItem.Id] = None,
    goal: Option[Goal.Slug] = None,
    parent: Option[RunInfo.Id] = None,
    origin: Option[RunInfo.Origin] = None,
    resumeAt: Option[RunInfo.ResumeAt] = None,
    dryRun: RunInfo.DryRun = RunInfo.DryRun.Apply,
    managerId: Option[ManagerInfo.Id] = None,
    managerItem: Option[ManagerItem.Id] = None,
    heartbeatAt: Option[RunInfo.HeartbeatAt] = None,
    pausedFor: Option[PauseReason] = None,
    creator: Option[Creation] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema,
      CanEqual:
  def isWaiting: Boolean = (status == Run.State.WaitingApproval.wire)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def modelFor(role: Role): Option[RunInfo.Model] =
    params.overrides.find(o => Role.parse(o.role).contains(role)).map(o => RunInfo.Model(o.model.value)).orElse(model)

object RunInfo:

  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Pipeline = Pipeline.T
  object Pipeline extends Newtype[String, Pure]:
    given Display[T] = Display(pipeline => Wording.humanize(Wording.Id(pipeline.value)))
  type Target = Target.T
  object Target extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export Run.State.Wire as Status
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  type CostUsd = CostUsd.T
  object CostUsd extends Newtype[Double, Pure]
  type BudgetUsd = BudgetUsd.T
  object BudgetUsd extends Newtype[Double, Pure]
  type BudgetTokens = BudgetTokens.T
  object BudgetTokens extends Newtype[Long, Pure]
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]:
    given Display[T] = Display(at => IsoLocal.text(at.value))
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]:
    given Display[T] = Display(at => IsoLocal.text(at.value))
  type TargetKind = TargetKind.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetKind extends Newtype[String, Pure]
  export Origin.Wire as Origin
  type ResumeAt = ResumeAt.T
  object ResumeAt extends Newtype[String, Pure]
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type HeartbeatAt = HeartbeatAt.T
  object HeartbeatAt extends Newtype[String, Pure]
