package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── request bodies (the operations that write) ────────────────────────────────────────────────

final case class RefsRequest(id: String, apply: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RefsRequest:
  given Schema[RefsRequest] = summon[KindlingsSchema[RefsRequest]].schema
