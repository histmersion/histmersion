package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.modelchains.AllowanceStatus
import io.github.iltotore.iron.constraint.any.Pure

final case class QuotaInfo(
    plans: List[PoolStatus],
    metered: List[AllowanceStatus],
    fallthrough: List[QuotaInfo.Fallthrough],
    now: QuotaInfo.Now,
    cooldowns: List[Cooldown] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QuotaInfo:
  type Fallthrough = Fallthrough.T
  object Fallthrough extends Newtype[String, Pure]
  type Now = Now.T
  object Now extends Newtype[String, Pure]
