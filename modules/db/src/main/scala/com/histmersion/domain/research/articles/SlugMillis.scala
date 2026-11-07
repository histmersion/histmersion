package com.histmersion.domain.research.articles

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

/** An article's slug and its last write in epoch milliseconds. */
final private[articles] case class SlugMillis(slug: SlugMillis.Slug, millis: SlugMillis.Millis) derives DbCodec

object SlugMillis:
  type Slug = com.histmersion.domain.research.Slug
  val Slug: _root_.com.histmersion.domain.research.Slug.type = _root_.com.histmersion.domain.research.Slug
  type Millis = com.histmersion.domain.library.sources.CitekeyMillis.Millis
  val Millis: _root_.com.histmersion.domain.library.sources.CitekeyMillis.Millis.type = _root_.com.histmersion.domain.library.sources.CitekeyMillis.Millis
