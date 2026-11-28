package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class WouldWrite(what: WouldWrite.What, payload: WouldWrite.Payload)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object WouldWrite:
  type What = What.T
  object What extends Newtype[String, Pure]
  type Payload = Payload.T
  object Payload extends Newtype[String, Pure]
