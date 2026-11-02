package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class RetractionNotice(nature: Option[String], reason: Option[String], date: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object RetractionNotice:
  given Schema[RetractionNotice] = summon[KindlingsSchema[RetractionNotice]].schema
