package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi

final case class CitedSource(
    citekey: Citekey,
    title: Option[Title],
    year: Option[Year],
    status: Option[Source.Status],
    authors: List[AuthorName] = (Nil).map(AuthorName.apply),
    doi: Option[Doi] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
