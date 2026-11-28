package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class QuotaRefresh(pool: Option[QuotaRefresh.Pool] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QuotaRefresh:
  export PoolId.Wire as Pool
