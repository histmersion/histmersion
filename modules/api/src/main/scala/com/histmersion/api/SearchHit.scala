package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class SearchHit(
    citekey: String,
    title: String,
    year: Option[Int],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    snippet: Option[String] = None,
    matchedHeading: Option[String] = None,
    doi: Option[String] = None,
    authors: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SearchHit:
  given Schema[SearchHit] = summon[KindlingsSchema[SearchHit]].schema
