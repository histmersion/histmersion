package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One item of a collection as a listing shows it (a Calibre book): its id, title, authors, year, publisher, tags,
  * identifiers by scheme and its files.
  */
final case class CollectionEntry(
    id: String,
    title: String,
    authors: List[String],
    year: Option[Int],
    publisher: Option[String],
    tags: List[String],
    identifiers: Map[String, String],
    formats: List[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionEntry:
  given Schema[CollectionEntry] = summon[KindlingsSchema[CollectionEntry]].schema
