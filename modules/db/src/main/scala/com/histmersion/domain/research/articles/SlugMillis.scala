package com.histmersion
package domain.research.articles

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.sources.CitekeyMillis

/** An article's slug and its last write in epoch milliseconds. */
final private[articles] case class SlugMillis(slug: SlugMillis.Slug, millis: SlugMillis.Millis) derives DbCodec

object SlugMillis:
  type Slug = com.histmersion.domain.research.Slug
  val Slug: com.histmersion.domain.research.Slug.type = com.histmersion.domain.research.Slug
  export CitekeyMillis.Millis
