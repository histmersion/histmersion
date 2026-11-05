package com.histmersion.domain.research.articles

import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An article's version as a bundle reads it: its body by digest and length, carried as a blob. */
final private[articles] case class BVersionRow(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha: String,
    bodyBytes: Long,
    summary: Option[String],
    versionOf: String,
    createdAt: String
) derives DbCodec
