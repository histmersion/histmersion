package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

// ── what the endpoints answer ─────────────────────────────────────────────────────────────────────

/** `GET /api/instance` — this collection's identity. */
final case class InstanceInfo(id: UUID, label: Option[String], createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object InstanceInfo:
  given Schema[InstanceInfo] = summon[KindlingsSchema[InstanceInfo]].schema
