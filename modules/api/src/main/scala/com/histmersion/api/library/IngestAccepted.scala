package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IngestAccepted(jobId: String, citekey: String, status: String, sha256: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestAccepted:
  given Schema[IngestAccepted] = summon[KindlingsSchema[IngestAccepted]].schema
