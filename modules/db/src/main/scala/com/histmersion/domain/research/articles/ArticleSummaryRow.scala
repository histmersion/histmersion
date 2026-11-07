package com.histmersion.domain.research.articles

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.DbCodec
import com.histmersion.api.research.Article

/** A list row as the database has it: [[ArticleSummary]] without the review state, which the server derives from other
  * tables.
  */
final private[articles] case class ArticleSummaryRow(
    slug: ArticleSummaryRow.Slug,
    title: ArticleSummaryRow.Title,
    kind: ArticleSummaryRow.Kind,
    summary: Option[ArticleSummaryRow.Summary],
    tags: List[ArticleSummaryRow.Tags],
    regions: List[ArticleSummaryRow.Regions],
    periodStart: Option[com.histmersion.domain.DeepTime.SignedYear],
    periodEnd: Option[com.histmersion.domain.DeepTime.SignedYear],
    updatedAt: Option[ArticleSummaryRow.UpdatedAt]
) derives DbCodec:
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
  type Slug = com.histmersion.domain.research.Slug
  val Slug: _root_.com.histmersion.domain.research.Slug.type = _root_.com.histmersion.domain.research.Slug
  type Title = com.histmersion.domain.Title
  val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
  type Kind = com.histmersion.domain.research.Article.Kind
  val Kind: _root_.com.histmersion.domain.research.Article.Kind.type = _root_.com.histmersion.domain.research.Article.Kind
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
