package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A list row: everything but the body. */
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
    /** Where it stands with the review panel (`GET /api/articles/{slug}/review` has the history). */
    review: Option[ReviewBadge] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleSummary:
  given Schema[ArticleSummary] = summon[KindlingsSchema[ArticleSummary]].schema
