package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.library.Source

final case class CitedSource(
    citekey: String,
    title: Option[String],
    year: Option[Int],
    status: Option[Source.Status],
    authors: List[String] = Nil,
    doi: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CitedSource:
  given Schema[CitedSource] = summon[KindlingsSchema[CitedSource]].schema
