package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── agent state ────────────────────────────────────────────────────────────────────────────────

/** Value is stored as a JSON string — the server handles JSONB storage and round-trips it as a string through the wire
  * so Kindlings/tapir Schema derivation works without a custom Schema for io.circe.Json. The agent parses/emits JSON at
  * the CLI/MCP boundary.
  */
final case class AgentStateEntry(
    key: String,
    value: String,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AgentStateEntry:
  given Schema[AgentStateEntry] = summon[KindlingsSchema[AgentStateEntry]].schema
