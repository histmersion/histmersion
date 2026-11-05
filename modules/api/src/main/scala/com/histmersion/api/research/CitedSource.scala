package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given

final case class CitedSource(
    citekey: com.histmersion.api.library.Citekey,
    title: Option[com.histmersion.api.library.Title],
    year: Option[com.histmersion.api.catalogue.Year],
    status: Option[Source.Status],
    authors: List[com.histmersion.api.library.AuthorName] = (Nil).map(com.histmersion.api.library.AuthorName.apply),
    doi: Option[com.histmersion.api.library.Doi] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
