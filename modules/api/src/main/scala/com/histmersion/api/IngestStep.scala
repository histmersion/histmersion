package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── ingest by identifier ──────────────────────────────────────────────────────────────────────

/** One line of the ingest report: what was attempted and how it went. */
final case class IngestStep(step: String, ok: Boolean, detail: String) derives KindlingsCodecAsObject, KindlingsSchema
object IngestStep:
  given Schema[IngestStep] = summon[KindlingsSchema[IngestStep]].schema
