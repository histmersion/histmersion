package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A field to set on an item, by the collection's own field name. */
final case class CollectionFieldValue(
    name: String,
    value: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionFieldValue:
  given Schema[CollectionFieldValue] = summon[KindlingsSchema[CollectionFieldValue]].schema
