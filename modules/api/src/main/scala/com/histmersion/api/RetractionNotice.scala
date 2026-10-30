package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── assess (Retraction Watch + Crossref update notices + citation metrics) ─────────────────────

final case class RetractionNotice(nature: Option[String], reason: Option[String], date: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object RetractionNotice:
  given Schema[RetractionNotice] = summon[KindlingsSchema[RetractionNotice]].schema
