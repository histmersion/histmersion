package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PdfInfo(source: String, url: Option[String], bytes: Long, spoolPath: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PdfInfo:
  given Schema[PdfInfo] = summon[KindlingsSchema[PdfInfo]].schema
