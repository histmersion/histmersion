package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Plan the rewrite of every linked item from its record; with `apply`, write it. */
final case class CollectionUpdateAllRequest(
    apply: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionUpdateAllRequest:
  given Schema[CollectionUpdateAllRequest] = summon[KindlingsSchema[CollectionUpdateAllRequest]].schema
