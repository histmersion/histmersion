package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A collection's search: the entries shown, and how many matched in all (an `unlinked` listing shows a page of them).
  */
final case class CollectionSearch(
    entries: List[CollectionEntry],
    total: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
