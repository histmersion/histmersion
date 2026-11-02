package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class AgentStatePut(
    value: String
) derives KindlingsSchema

object AgentStatePut:
  given Schema[AgentStatePut] = summon[KindlingsSchema[AgentStatePut]].schema

  given Codec.AsObject[AgentStatePut] = StrictJson.codec(KindlingsCodecAsObject.derived[AgentStatePut])
