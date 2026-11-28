package com.histmersion
package api.automation.agents

import com.histmersion.util.problem.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.AgentRole
import com.histmersion.Newtype

final case class RoleModel(
    role: RoleModel.Role,
    model: Option[RoleModel.Model],
    ready: RoleModel.Ready,
    problems: List[Problem]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RoleModel:
  export AgentRole.Wire as Role
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  enum Ready derives CanEqual:
    case Ready, Unready
  object Ready extends TwoStates[Ready]:
    extension (ready: Ready) def ready: Boolean = ready == Ready
