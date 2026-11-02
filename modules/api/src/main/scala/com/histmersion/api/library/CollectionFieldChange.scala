package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One field of an item: what the collection holds and what it is (or would be) written to. */
final case class CollectionFieldChange(
    field: String,
    from: String,
    to: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionFieldChange:
  given Schema[CollectionFieldChange] = summon[KindlingsSchema[CollectionFieldChange]].schema
