package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.domain.library.Source

final case class CoverageSource(
    citekey: String,
    title: String,
    year: Option[Int],
    status: Source.Status,
    authors: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CoverageSource:
  given Schema[CoverageSource] = summon[KindlingsSchema[CoverageSource]].schema
