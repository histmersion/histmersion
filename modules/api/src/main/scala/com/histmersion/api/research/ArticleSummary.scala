package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArticleSummary(
    slug: String,
    title: String,
    kind: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: Option[String] = None,
    review: Option[ReviewBadge] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleSummary:
  given Schema[ArticleSummary] = summon[KindlingsSchema[ArticleSummary]].schema
