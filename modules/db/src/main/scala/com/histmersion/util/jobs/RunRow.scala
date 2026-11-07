package com.histmersion.util.jobs

/** A job that runs as checkpointed steps (an agent run): what its row records when it is created. Its `status`, `kind`
  * and `params` are the owner's words, stored as given; `citekey` is the source it works on, when it works on one.
  */
final case class RunRow(
    kind: RunRow.Kind,
    status: RunRow.Status,
    pipeline: RunRow.Pipeline,
    pipelineVersion: RunRow.PipelineVersion,
    targetKind: RunRow.TargetKind,
    target: RunRow.Target,
    citekey: Option[RunRow.Citekey],
    model: Option[RunRow.Model],
    budgetUsd: BigDecimal,
    budgetTokens: RunRow.BudgetTokens,
    params: RunRow.Params,
    acquireItem: Option[RunRow.AcquireItem],
    goal: Option[RunRow.Goal],
    parent: Option[RunRow.Parent],
    origin: RunRow.Origin,
    dryRun: RunRow.DryRun,
    managerId: Option[RunRow.ManagerId],
    managerItem: Option[RunRow.ManagerItem]
)

object RunRow:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pipeline = Pipeline.T
  object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineVersion = com.histmersion.domain.automation.Pipeline.Version
  val PipelineVersion: _root_.com.histmersion.domain.automation.Pipeline.Version.type = _root_.com.histmersion.domain.automation.Pipeline.Version
  type TargetKind = TargetKind.T
  object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey
  type Model = Model.T
  object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetTokens = com.histmersion.util.jobs.JobRepo.TokenBudget
  val BudgetTokens: _root_.com.histmersion.util.jobs.JobRepo.TokenBudget.type = _root_.com.histmersion.util.jobs.JobRepo.TokenBudget
  type Params = Params.T
  object Params extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Parent = Parent.T
  object Parent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Origin = Origin.T
  object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerItem = ManagerItem.T
  object ManagerItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
