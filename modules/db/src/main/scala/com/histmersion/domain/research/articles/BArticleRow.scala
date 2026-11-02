package com.histmersion.domain.research.articles

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An article as a bundle reads it, its period in its three columns (Magnum maps a SELECT's columns positionally). */
final private[articles] case class BArticleRow(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    updatedAt: String
) derives DbCodec
