package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IngestReport(
    citekey: String,
    doi: Option[String],
    title: Option[String],
    created: Boolean,
    pdf: Option[PdfInfo],
    edgesAdded: Int,
    assessment: Option[Assessment],
    steps: List[IngestStep],
    dropIn: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestReport:
  given Schema[IngestReport] = summon[KindlingsSchema[IngestReport]].schema
