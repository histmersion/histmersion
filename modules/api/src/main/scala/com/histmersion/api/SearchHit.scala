package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** A row of the `search` result set: period split to bounds, arrays as proper lists. */
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
    // FTS extras (plan §1.4). Optional so callers that don't run a text query keep compiling.
    snippet: Option[String] = None, // ts_headline over the best-matching body chunk (**term**)
    matchedHeading: Option[String] = None, // that chunk's heading breadcrumb
    doi: Option[String] = None, // the link label when present (else the citekey)
    authors: List[String] = Nil // as stored: Library's table shows them under the title
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SearchHit:
  given Schema[SearchHit] = summon[KindlingsSchema[SearchHit]].schema
