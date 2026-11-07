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
  type Slug = com.histmersion.domain.research.Slug
  val Slug: _root_.com.histmersion.domain.research.Slug.type = _root_.com.histmersion.domain.research.Slug
  type Title = com.histmersion.domain.Title
  val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
  type Kind = com.histmersion.domain.research.Article.Kind
  val Kind: _root_.com.histmersion.domain.research.Article.Kind.type = _root_.com.histmersion.domain.research.Article.Kind
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
