package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source linked to item `id`, whose title is `title`. */
final case class CollectionLinked(
    citekey: String,
    id: String,
    title: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionLinked:
  given Schema[CollectionLinked] = summon[KindlingsSchema[CollectionLinked]].schema
