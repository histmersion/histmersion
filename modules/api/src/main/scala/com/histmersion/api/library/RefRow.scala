package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class RefRow(
    title: Option[Title] = None,
    authors: List[AuthorName] = (Nil).map(AuthorName.apply),
    year: Option[Year] = None,
    doi: Option[Doi] = None,
    raw: Option[RefRow.Raw] = None,
    citekey: Option[Citekey] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object RefRow:
  export Refs.ReferenceText as Raw
