package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/ingest` / `POST /api/sources/{ck}/reocr` — 202 with the job to poll. */
final case class IngestAccepted(jobId: String, citekey: String, status: String, sha256: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestAccepted:
  given Schema[IngestAccepted] = summon[KindlingsSchema[IngestAccepted]].schema
