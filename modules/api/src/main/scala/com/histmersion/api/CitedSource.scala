package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class CitedSource(
    citekey: String,
    title: Option[String],
    year: Option[Int],
    status: Option[SourceStatus],
    authors: List[String] = Nil,
    doi: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CitedSource:
  given Schema[CitedSource] = summon[KindlingsSchema[CitedSource]].schema
