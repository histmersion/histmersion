package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class ResolvedSource(
    citekey: Citekey,
    doi: Option[Doi],
    authors: List[AuthorName],
    year: Option[Year],
    title: ResolvedSource.Title,
    status: Source.Status
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def ref: Source.Ref = Source.Ref(citekey, doi, authors, year, title, status)

object ResolvedSource:
  export com.histmersion.api.library.Title
