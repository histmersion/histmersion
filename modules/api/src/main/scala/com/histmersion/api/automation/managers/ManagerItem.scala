package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.RunInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.automation.agentscheduler.WhyInfo

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerItem(
    id: ManagerItem.Id,
    managerId: ManagerInfo.Id,
    kind: ManagerItem.Kind,
    target: ManagerItem.Target,
    status: ManagerItem.Status,
    priority: ManagerItem.Priority = ManagerItem.Priority(0),
    attempts: ManagerItem.Attempts = ManagerItem.Attempts(0),
    failures: ManagerItem.Failures = ManagerItem.Failures(0),
    runId: Option[RunInfo.Id] = None,
    evaluatedRun: Option[RunInfo.Id] = None,
    notBefore: Option[ManagerItem.NotBefore] = None,
    hold: Option[Hold] = None,
    state: ItemState = ItemState(),
    outcome: Option[ManagerItem.Outcome] = None,
    origin: ManagerItem.Origin = ItemOrigin.Selection.wire,
    createdAt: ManagerItem.CreatedAt = ManagerItem.CreatedAt(""),
    updatedAt: ManagerItem.UpdatedAt = ManagerItem.UpdatedAt(""),
    run: Option[RunInfo] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerItem:
  type Ref = Ref.T
  object Ref extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  export WhyInfo.Kind
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  export api.automation.ItemStatus.Wire as Status
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Attempts = Attempts.T
  object Attempts extends Newtype[Int, Pure]
  type Failures = Failures.T
  object Failures extends Newtype[Int, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends Newtype[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export ItemOrigin.Wire as Origin
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
