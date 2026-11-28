package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.modelchains.ChainEntry

final case class Cooldown(model: Cooldown.Model, until: Cooldown.Until, reason: Rationale)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Cooldown:
  export ChainEntry.Model
  type Until = Until.T
  object Until extends Newtype[String, Pure]
