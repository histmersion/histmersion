package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What became of an imported book's cover: one line for a person, a `warning` when it could not be stored. */
final case class CollectionCover(
    line: String,
    warning: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionCover:
  given Schema[CollectionCover] = summon[KindlingsSchema[CollectionCover]].schema
