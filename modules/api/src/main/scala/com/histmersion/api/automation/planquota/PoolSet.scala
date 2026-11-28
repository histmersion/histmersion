package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class PoolSet(hold: Option[PoolSet.Hold] = None, floors: Option[PoolSet.Floors] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PoolSet:
  enum Hold derives CanEqual:
    case Hold, Release
  object Hold extends TwoStates[Hold]:
    extension (hold: Hold) def hold: Boolean = hold == Hold
  type Floors = Floors.T
  object Floors extends Newtype[String, Pure]
