package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AgentStateDeleted(
    key: String,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AgentStateDeleted:
  given Schema[AgentStateDeleted] = summon[KindlingsSchema[AgentStateDeleted]].schema
