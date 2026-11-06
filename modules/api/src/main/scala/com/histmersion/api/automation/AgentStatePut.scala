package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class AgentStatePut(
    value: AgentStatePut.Value
) derives KindlingsSchema, com.histmersion.api.json.Schema

object AgentStatePut:


  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given Codec.AsObject[AgentStatePut] = StrictJson.codec(KindlingsCodecAsObject.derived[AgentStatePut])
