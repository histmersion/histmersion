package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class RefRow(
    title: Option[com.histmersion.api.library.Title] = None,
    authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
    year: Option[com.histmersion.api.catalogue.Year] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    raw: Option[RefRow.Raw] = None,
    citekey: Option[com.histmersion.api.library.Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

