package com.histmersion
package domain.research.articles

import com.histmersion.util.database.given

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

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
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = domain.research.Slug
  val Slug: domain.research.Slug.type = domain.research.Slug
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
  export Article.Kind
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
  type Reasons = Reasons.T
  object Reasons extends Newtype[String, Pure]
