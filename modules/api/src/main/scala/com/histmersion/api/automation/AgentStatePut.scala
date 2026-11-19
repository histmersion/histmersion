package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class AgentStatePut(
    value: AgentStatePut.Value
) derives StrictJson,
      KindlingsSchema,
      api.json.Schema

object AgentStatePut:

  type Value = Value.T
  object Value extends Newtype[String, Pure]
