package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class SearchHit(
    citekey: com.histmersion.api.library.Citekey,
    title: com.histmersion.api.library.Title,
    year: Option[com.histmersion.api.catalogue.Year],
    status: com.histmersion.api.library.Source.Status,
    topics: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    snippet: Option[String] = None,
    matchedHeading: Option[String] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
