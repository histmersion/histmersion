package com.histmersion
package util.jobs

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Kind extends RefinedType[String, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Pipeline = Pipeline.T
  object Pipeline extends RefinedType[String, Pure]
  type PipelineVersion = com.histmersion.domain.automation.Pipeline.Version
  val PipelineVersion: com.histmersion.domain.automation.Pipeline.Version.type = com.histmersion.domain.automation.Pipeline.Version
  type TargetKind = TargetKind.T
  object TargetKind extends RefinedType[String, Pure]
  type Target = Target.T
  object Target extends RefinedType[String, Pure]
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: com.histmersion.domain.library.Citekey.type = com.histmersion.domain.library.Citekey
  type Model = Model.T
  object Model extends RefinedType[String, Pure]
  type BudgetTokens = JobRepo.TokenBudget
  val BudgetTokens: JobRepo.TokenBudget.type = JobRepo.TokenBudget
  type Params = Params.T
  object Params extends RefinedType[String, Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends RefinedType[Int, Pure]
  type Goal = Goal.T
  object Goal extends RefinedType[String, Pure]
  type Parent = Parent.T
  object Parent extends RefinedType[String, Pure]
  type Origin = Origin.T
  object Origin extends RefinedType[String, Pure]
  type DryRun = DryRun.T
  object DryRun extends RefinedType[Boolean, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends RefinedType[String, Pure]
  type ManagerItem = ManagerItem.T
  object ManagerItem extends RefinedType[Long, Pure]
