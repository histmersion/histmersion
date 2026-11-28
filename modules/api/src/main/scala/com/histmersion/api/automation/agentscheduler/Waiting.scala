package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.AgentScheduler.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.Wording

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final case class Waiting(
    item: Waiting.Item,
    manager: Waiting.Manager,
    kind: Waiting.Kind,
    jobType: Waiting.JobType,
    target: Waiting.Target,
    goal: Option[Goal.Slug] = None,
    priority: Waiting.Priority = Waiting.Priority(0),
    waitedMinutes: Waiting.WaitedMinutes = Waiting.WaitedMinutes((0).toLong),
    code: Waiting.Code = Waiting.Code(""),
    why: Rationale = Rationale(""),
    behind: List[Waiting.Behind] = Nil,
    position: Waiting.Position = Waiting.Position(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Waiting:
  type Item = Item.T
  object Item extends Newtype[Long, Pure]
  type Manager = Manager.T
  object Manager extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export JobKinds.Wire as JobType
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type WaitedMinutes = WaitedMinutes.T
  object WaitedMinutes extends Newtype[Long, Pure]
  type Code = Code.T
  object Code extends Newtype[String, Pure]:
    given Display[T] = Display(code => Wording.humanize(Wording.Id(code.value)))
  type Behind = Behind.T
  object Behind extends Newtype[String, Pure]
  type Position = Position.T
  object Position extends Newtype[Int, Pure]
