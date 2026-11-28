package com.histmersion
package api.automation.agents

import com.histmersion.util.problem.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.RoleModel
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.modelchains.RoleChain
import com.histmersion.api.automation.modelchains.AllowanceStatus
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class ModelsInfo(
    enabled: ModelsInfo.Enabled,
    roles: List[RoleModel],
    pipelines: List[ModelsInfo.Pipelines],
    maxConcurrentRuns: ModelsInfo.MaxConcurrentRuns,
    problems: List[Problem],
    chains: List[RoleChain] = Nil,
    allowances: List[AllowanceStatus] = Nil,
    now: ModelsInfo.Now = ModelsInfo.Now("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ModelsInfo:
  enum Enabled derives CanEqual:
    case Enabled, Disabled
  object Enabled extends TwoStates[Enabled]:
    extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
  type Pipelines = Pipelines.T
  object Pipelines extends Newtype[String, Pure]
  type MaxConcurrentRuns = MaxConcurrentRuns.T
  object MaxConcurrentRuns extends Newtype[Int, Pure]
  type Now = Now.T
  object Now extends Newtype[String, Pure]
