package com.histmersion.domain.research.articles

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An article as a bundle reads it, its period in its three columns (Magnum maps a SELECT's columns positionally). */
final private[articles] case class BArticleRow(
    uid: UUID,
    origin: UUID,
    slug: BArticleRow.Slug,
    title: BArticleRow.Title,
    kind: BArticleRow.Kind,
    body: BArticleRow.Body,
    summary: Option[BArticleRow.Summary],
    tags: List[BArticleRow.Tags],
    regions: List[BArticleRow.Regions],
    pNull: BArticleRow.PNull,
    pLo: Option[BArticleRow.PLo],
    pHi: Option[BArticleRow.PHi],
    updatedAt: BArticleRow.UpdatedAt
) derives DbCodec

object BArticleRow:
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
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = com.histmersion.domain.DeepTime.SignedYear
  val PLo: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type PHi = com.histmersion.domain.DeepTime.SignedYear
  val PHi: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
