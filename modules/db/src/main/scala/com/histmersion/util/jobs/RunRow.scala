package com.histmersion
package util.jobs

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
  object Kind extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Pipeline = Pipeline.T
  object Pipeline extends Newtype[String, Pure]
  export domain.automation.Pipeline.Version as PipelineVersion
  type TargetKind = TargetKind.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetKind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Citekey = domain.library.Citekey
  val Citekey: domain.library.Citekey.type = domain.library.Citekey
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  export JobRepo.TokenBudget as BudgetTokens
  type Params = Params.T
  object Params extends Newtype[String, Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends Newtype[Int, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type Parent = Parent.T
  object Parent extends Newtype[String, Pure]
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type ManagerItem = ManagerItem.T
  object ManagerItem extends Newtype[Long, Pure]
