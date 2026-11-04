package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Link source `citekey` to item `id` of a collection (the collection itself is not changed). */
final case class CollectionLinkRequest(
    citekey: String,
    id: String
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
