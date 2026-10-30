package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class CoverageSource(
    citekey: String,
    title: String,
    year: Option[Int],
    status: SourceStatus,
    authors: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageSource:
  given Schema[CoverageSource] = summon[KindlingsSchema[CoverageSource]].schema
