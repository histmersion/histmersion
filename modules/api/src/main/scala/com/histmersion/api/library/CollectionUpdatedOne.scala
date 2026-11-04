package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An item rewritten from a record: the fields written (none when it already agreed) and the record's title. */
final case class CollectionUpdatedOne(
    citekey: String,
    id: String,
    changed: List[String],
    title: String
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
