package com.histmersion.domain.research.articles

import com.augustnagro.magnum.DbCodec

/** An article's slug and its last write in epoch milliseconds. */
final private[articles] case class SlugMillis(slug: String, millis: Long) derives DbCodec
