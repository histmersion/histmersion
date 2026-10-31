package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One source the library lists as not in a collection: its push metadata, where its original is (`local`,
  * `stored`, `none`, `unknown`) and whether it counts as a gap (a record with no fetched file never does).
  */
final case class CollectionGapRow(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    summary: Option[String],
    topics: List[String],
    provenance: Option[String],
    calibreId: Option[String],
    isbn: Option[String],
    storedOriginal: Option[Boolean],
    original: String,
    counted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionGapRow:
  given Schema[CollectionGapRow] = summon[KindlingsSchema[CollectionGapRow]].schema
