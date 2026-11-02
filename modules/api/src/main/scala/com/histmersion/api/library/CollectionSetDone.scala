package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The fields set on item `id`. */
final case class CollectionSetDone(
    id: String,
    fields: List[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionSetDone:
  given Schema[CollectionSetDone] = summon[KindlingsSchema[CollectionSetDone]].schema
