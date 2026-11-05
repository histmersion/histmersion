package com.histmersion.domain.research.articles

import com.augustnagro.magnum.DbCodec

/** An article's slug and its last write in epoch milliseconds. */
final private[articles] case class SlugMillis(slug: SlugMillis.Slug, millis: SlugMillis.Millis) derives DbCodec

object SlugMillis:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Millis = Millis.T
  object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
