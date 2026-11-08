package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

/** What a record or an item says of a work, as an audit compares them. */
final case class CollectionRecordView(
    title: Title,
    authors: List[AuthorName],
    year: Option[Year]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
