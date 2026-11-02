package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageEntry(source: CoverageSource, reason: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageEntry:
  given Schema[CoverageEntry] = summon[KindlingsSchema[CoverageEntry]].schema
