package com.histmersion.api.library
import com.histmersion.api.given_Configuration
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

  /** Plan the rewrite of every linked item from its record; with `apply`, write it. */
  final case class Request(
      apply: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Request:
    given Schema[Request] = summon[KindlingsSchema[Request]].schema
