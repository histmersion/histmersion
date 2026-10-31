package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An update of every linked item: one row per item, in id order, and whether it was written (`applied`). */
final case class CollectionUpdateAll(
    rows: List[CollectionUpdateRow],
    applied: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionUpdateAll:
  given Schema[CollectionUpdateAll] = summon[KindlingsSchema[CollectionUpdateAll]].schema
