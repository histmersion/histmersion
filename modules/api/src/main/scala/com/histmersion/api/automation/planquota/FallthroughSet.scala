package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TwoStates

final case class FallthroughSet(fallthrough: FallthroughSet.Fallthrough)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FallthroughSet:
  enum Fallthrough derives CanEqual:
    case Fallthrough, Stop
  object Fallthrough extends TwoStates[Fallthrough]:
    extension (fallthrough: Fallthrough) def fallthrough: Boolean = fallthrough == Fallthrough
