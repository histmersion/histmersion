package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CalibreGap(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    summary: Option[String],
    topics: List[String],
    provenance: Option[String],
    calibreId: Option[String] = None,
    isbn: Option[String] = None,
    /** None where the server does not say (an older server, the push metadata): the push then tries. */
    storedOriginal: Option[Boolean] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CalibreGap:
  given Schema[CalibreGap] = summon[KindlingsSchema[CalibreGap]].schema
