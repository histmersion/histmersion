package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a record or an item says of a work, as an audit compares them. */
final case class CollectionRecordView(
    title: String,
    authors: List[String],
    year: Option[Int]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionRecordView:
  given Schema[CollectionRecordView] = summon[KindlingsSchema[CollectionRecordView]].schema
