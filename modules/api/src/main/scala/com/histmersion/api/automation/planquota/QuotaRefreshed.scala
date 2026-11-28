package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class QuotaRefreshed(pools: List[PoolRefreshed], lifted: List[QuotaRefreshed.Lifted] = Nil)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QuotaRefreshed:
  type Lifted = Lifted.T
  object Lifted extends Newtype[String, Pure]
