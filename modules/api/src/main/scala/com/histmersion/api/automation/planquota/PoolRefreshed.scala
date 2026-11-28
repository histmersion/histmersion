package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class PoolRefreshed(
    pool: PoolRefreshed.Pool,
    refreshed: PoolRefreshed.Refreshed,
    detail: PoolRefreshed.Detail,
    status: PoolStatus
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PoolRefreshed:
  export PoolId.Wire as Pool
  enum Refreshed derives CanEqual:
    case Refreshed, Unchanged
  object Refreshed extends TwoStates[Refreshed]:
    extension (refreshed: Refreshed) def refreshed: Boolean = refreshed == Refreshed
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
