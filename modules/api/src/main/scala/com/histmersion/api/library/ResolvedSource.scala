package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ResolvedSource(
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: String,
    status: com.histmersion.api.library.Source.Status
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ref: Source.Ref = Source.Ref(citekey, doi, authors, year, title, status)
object ResolvedSource:
  given Schema[ResolvedSource] = summon[KindlingsSchema[ResolvedSource]].schema
