package com.histmersion
package domain.research.articles

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.DbCodec
import com.histmersion.api.research.Article
import com.histmersion.domain.DeepTime
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A list row as the database has it: [[ArticleSummary]] without the review state, which the server derives from other
  * tables.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[articles] case class ArticleSummaryRow(
    slug: ArticleSummaryRow.Slug,
    title: ArticleSummaryRow.Title,
    kind: ArticleSummaryRow.Kind,
    summary: Option[ArticleSummaryRow.Summary],
    tags: List[ArticleSummaryRow.Tags],
    regions: List[ArticleSummaryRow.Regions],
    periodStart: Option[DeepTime.SignedYear],
    periodEnd: Option[DeepTime.SignedYear],
    updatedAt: Option[ArticleSummaryRow.UpdatedAt]
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def summaryRow: Article.Summary =
    Article.Summary(
      Article.Slug(slug.value),
      Article.Title(title.value),
      Article.Kind.Wire(kind.value),
      summary.map(s => Article.SummaryText(s.value)),
      tags.map(t => Article.Summary.Tags(t.value)),
      regions.map(r => Article.Summary.Regions(r.value)),
      periodStart.map(y => Article.Summary.PeriodStart(y.value)),
      periodEnd.map(y => Article.Summary.PeriodEnd(y.value)),
      updatedAt.map(u => Article.Summary.UpdatedAt(u.value))
    )

object ArticleSummaryRow:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = domain.research.Slug
  val Slug: domain.research.Slug.type = domain.research.Slug
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
  export domain.research.Article.Kind
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
