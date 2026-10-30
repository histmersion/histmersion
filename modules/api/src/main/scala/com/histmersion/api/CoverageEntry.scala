package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source the article does not cite, with its stored reason (`None` = not given yet). */
final case class CoverageEntry(source: CoverageSource, reason: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageEntry:
  given Schema[CoverageEntry] = summon[KindlingsSchema[CoverageEntry]].schema
