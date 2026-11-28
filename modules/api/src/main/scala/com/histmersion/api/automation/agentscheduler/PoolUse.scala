package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class PoolUse(pool: PoolUse.Pool, used: PoolUse.Used = PoolUse.Used(0), cap: PoolUse.Cap = PoolUse.Cap(0))
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PoolUse:
  type Pool = Pool.T
  object Pool extends Newtype[String, Pure]
  type Used = Used.T
  object Used extends Newtype[Int, Pure]
  type Cap = Cap.T
  object Cap extends Newtype[Int, Pure]
