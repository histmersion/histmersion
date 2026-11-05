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
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ref: Source.Ref = Source.Ref(com.histmersion.api.library.Citekey(citekey), (doi).map(com.histmersion.api.library.Doi.apply), (authors).map(com.histmersion.api.library.AuthorName.apply), (year).map(com.histmersion.api.catalogue.Year.apply), com.histmersion.api.library.Title(title), status)
