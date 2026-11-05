package com.histmersion.domain.research.articles

import com.histmersion.util.database.given

import com.augustnagro.magnum.DbCodec

/** Flat projection of the whole article row, for the domain read (`load`). */
final private[articles] case class FullArticleRow(
    slug: FullArticleRow.Slug,
    title: FullArticleRow.Title,
    kind: FullArticleRow.Kind,
    body: FullArticleRow.Body,
    summary: Option[FullArticleRow.Summary],
    tags: List[FullArticleRow.Tags],
    regions: List[FullArticleRow.Regions],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    updatedAt: FullArticleRow.UpdatedAt,
    filter: Option[FullArticleRow.Filter],
    reasons: FullArticleRow.Reasons
) derives DbCodec

object FullArticleRow:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filter = Filter.T
  object Filter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reasons = Reasons.T
  object Reasons extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
