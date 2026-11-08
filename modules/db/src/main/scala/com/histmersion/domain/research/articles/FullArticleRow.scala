package com.histmersion
package domain.research.articles

import com.histmersion.util.database.given

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.research.Article
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Flat projection of the whole article row, for the domain read (`load`). */
final private[articles] case class FullArticleRow(
    slug: FullArticleRow.Slug,
    title: FullArticleRow.Title,
    kind: FullArticleRow.Kind,
    body: FullArticleRow.Body,
    summary: Option[FullArticleRow.Summary],
    tags: List[FullArticleRow.Tags],
    regions: List[FullArticleRow.Regions],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    updatedAt: FullArticleRow.UpdatedAt,
    filter: Option[FullArticleRow.Filter],
    reasons: FullArticleRow.Reasons
) derives DbCodec

object FullArticleRow:
  type Slug = com.histmersion.domain.research.Slug
  val Slug: com.histmersion.domain.research.Slug.type = com.histmersion.domain.research.Slug
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Kind = Article.Kind
  val Kind: Article.Kind.type = Article.Kind
  type Body = Body.T
  object Body extends RefinedType[String, Pure]
  type Summary = Summary.T
  object Summary extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Regions = Regions.T
  object Regions extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
  type Filter = Filter.T
  object Filter extends RefinedType[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends RefinedType[String, Pure]
