package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An import: the record written (`source`), the item it is linked to, the entry it was linked to before
  * (`relinkedFrom`), the file taken (left in the collection, never modified), the cover, and the conversion queued
  * (`job`) or why it was not (`jobError`, with its `jobErrorCode`).
  */
final case class CollectionImported(
    citekey: String,
    id: String,
    title: String,
    relinkedFrom: Option[String],
    file: String,
    source: Source.WithFindings,
    cover: Option[CollectionCover],
    job: Option[Ingest.Accepted],
    jobError: Option[String] = None,
    jobErrorCode: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionImported:
  given Schema[CollectionImported] = summon[KindlingsSchema[CollectionImported]].schema
