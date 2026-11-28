package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.AgentScheduler.Rationale
import io.github.iltotore.iron.constraint.any.Pure

final case class WhyInfo(
    item: WhyInfo.Item,
    manager: WhyInfo.Manager = WhyInfo.Manager(""),
    kind: WhyInfo.Kind = WhyInfo.Kind(""),
    target: WhyInfo.Target = WhyInfo.Target(""),
    status: WhyInfo.Status = WhyInfo.Status(""),
    code: WhyInfo.Code = WhyInfo.Code(""),
    why: Rationale = Rationale(""),
    behind: List[WhyInfo.Behind] = Nil,
    position: WhyInfo.Position = WhyInfo.Position(0),
    run: Option[WhyInfo.Run] = None,
    approval: Option[WhyInfo.Approval] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object WhyInfo:
  type Item = Item.T
  object Item extends Newtype[Long, Pure]
  type Manager = Manager.T
  object Manager extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Code = Code.T
  object Code extends Newtype[String, Pure]
  type Behind = Behind.T
  object Behind extends Newtype[String, Pure]
  type Position = Position.T
  object Position extends Newtype[Int, Pure]
  type Run = Run.T
  object Run extends Newtype[String, Pure]
  type Approval = Approval.T
  object Approval extends Newtype[Long, Pure]
