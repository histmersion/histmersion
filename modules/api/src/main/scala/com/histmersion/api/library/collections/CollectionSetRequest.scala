package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Set named fields on item `id` of a collection. */
final case class CollectionSetRequest(
    id: String,
    fields: List[CollectionFieldValue]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionSetRequest:
  given Schema[CollectionSetRequest] = summon[KindlingsSchema[CollectionSetRequest]].schema
