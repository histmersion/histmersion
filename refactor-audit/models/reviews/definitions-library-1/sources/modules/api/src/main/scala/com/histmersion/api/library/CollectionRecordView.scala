package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a record or an item says of a work, as an audit compares them. */
final case class CollectionRecordView(
    title: com.histmersion.api.library.Title,
    authors: List[com.histmersion.api.library.AuthorName],
    year: Option[com.histmersion.api.catalogue.Year]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
