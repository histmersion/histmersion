package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class ResolvedSource(
    citekey: com.histmersion.api.library.Citekey,
    doi: Option[com.histmersion.api.library.Doi],
    authors: List[com.histmersion.api.library.AuthorName],
    year: Option[com.histmersion.api.catalogue.Year],
    title: ResolvedSource.Title,
    status: com.histmersion.api.library.Source.Status
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ref: Source.Ref = Source.Ref(citekey, doi, authors, year, title, status)

object ResolvedSource:
  type Title = com.histmersion.api.library.Title
  val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
