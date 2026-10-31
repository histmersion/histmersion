package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One push of a sync: the item it became, or why it failed. */
final case class CollectionSyncPush(
    citekey: String,
    id: Option[String],
    error: Option[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionSyncPush:
  given Schema[CollectionSyncPush] = summon[KindlingsSchema[CollectionSyncPush]].schema
