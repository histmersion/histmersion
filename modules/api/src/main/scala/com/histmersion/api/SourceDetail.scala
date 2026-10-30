package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class SourceDetail(
    citekey: String,
    title: String,
    authors: List[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    doi: Option[String],
    ocrMarkdown: Option[String],
    notes: Option[String] = None,
    updatedAt: Option[String] = None,
    provenance: Option[String] = None,
    uid: Option[java.util.UUID] = None,
    origin: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceDetail:
  given Schema[SourceDetail] = summon[KindlingsSchema[SourceDetail]].schema
