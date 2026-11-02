package com.histmersion.domain.research.articles

import com.histmersion.util.database.given

import com.augustnagro.magnum.DbCodec

/** Flat projection of the whole article row, for the domain read (`load`). */
final private[articles] case class FullArticleRow(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: String,
    filter: Option[String],
    reasons: String
) derives DbCodec
