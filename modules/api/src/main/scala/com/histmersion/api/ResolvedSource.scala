package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class ResolvedSource(
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: String,
    status: SourceStatus
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ref: SourceRef = SourceRef(citekey, doi, authors, year, title, status)
object ResolvedSource:
  given Schema[ResolvedSource] = summon[KindlingsSchema[ResolvedSource]].schema
