package com.histmersion
package domain.research.articles

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.sources.CitekeyMillis
import com.histmersion.lint.{LintRule, tolerated}

/** An article's slug and its last write in epoch milliseconds. */
final private[articles] case class SlugMillis(slug: SlugMillis.Slug, millis: SlugMillis.Millis) derives DbCodec

object SlugMillis:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = domain.research.Slug
  val Slug: domain.research.Slug.type = domain.research.Slug
  export CitekeyMillis.Millis
