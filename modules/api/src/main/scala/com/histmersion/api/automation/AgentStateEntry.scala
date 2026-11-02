package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** `value` is JSON carried as a string, so Kindlings/tapir derive its Schema without a custom one for `io.circe.Json`.
  */
final case class AgentStateEntry(
    key: String,
    value: String,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AgentStateEntry:
  given Schema[AgentStateEntry] = summon[KindlingsSchema[AgentStateEntry]].schema
