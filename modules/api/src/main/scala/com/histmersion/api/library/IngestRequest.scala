package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IngestRequest(
    id: String,
    citekey: Option[String] = None,
    arxiv: Option[String] = None,
    refetch: Boolean = false,
    legalOnly: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestRequest:
  given Schema[IngestRequest] = summon[KindlingsSchema[IngestRequest]].schema
