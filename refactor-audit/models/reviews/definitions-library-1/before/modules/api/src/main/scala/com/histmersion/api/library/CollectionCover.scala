package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What became of an imported book's cover: one line for a person, a `warning` when it could not be stored. */
final case class CollectionCover(
    line: String,
    warning: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
