package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Rewrite the item source `citekey` is linked to from its record. */
final case class CollectionUpdateOne(
    citekey: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionUpdateOne:
  given Schema[CollectionUpdateOne] = summon[KindlingsSchema[CollectionUpdateOne]].schema
