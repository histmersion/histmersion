package com.histmersion.domain.research.articles

import com.histmersion.util.database.given

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
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    updatedAt: Option[ArticleSummaryRow.UpdatedAt]
) derives DbCodec:
  def summaryRow: Article.Summary =
    Article.Summary(slug, title, kind, summary, tags, regions, periodStart, periodEnd, updatedAt)

object ArticleSummaryRow:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regions = Regions.T
  object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
