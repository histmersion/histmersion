package com.histmersion.domain.research.articles

import com.histmersion.util.database.given

import com.augustnagro.magnum.DbCodec
import com.histmersion.api.research.Article

/** A list row as the database has it: [[ArticleSummary]] without the review state, which the server derives from other
  * tables.
  */
final private[articles] case class ArticleSummaryRow(
    slug: String,
    title: String,
    kind: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: Option[String]
) derives DbCodec:
  def summaryRow: Article.Summary =
    Article.Summary(slug, title, kind, summary, tags, regions, periodStart, periodEnd, updatedAt)
