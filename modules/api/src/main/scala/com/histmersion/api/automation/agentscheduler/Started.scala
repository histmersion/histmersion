package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.AgentScheduler.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.Wording

final case class Started(
    item: Started.Item,
    manager: Started.Manager,
    kind: Started.Kind,
    jobType: Started.JobType,
    target: Started.Target,
    goal: Option[Goal.Slug] = None,
    why: Rationale = Rationale("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Started:
  type Item = Item.T
  object Item extends Newtype[Long, Pure]
  type Manager = Manager.T
  object Manager extends Newtype[String, Pure]:
    given Display[T] = Display(manager => Wording.humanize(Wording.Id(manager.value)))
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export JobKinds.Wire as JobType
  type Target = Target.T
  object Target extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
