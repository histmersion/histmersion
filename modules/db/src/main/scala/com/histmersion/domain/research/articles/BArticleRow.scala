package com.histmersion
package domain.research.articles

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime

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
  val Slug: com.histmersion.domain.research.Slug.type = com.histmersion.domain.research.Slug
  type Title = com.histmersion.domain.Title
  val Title: com.histmersion.domain.Title.type = com.histmersion.domain.Title
  type Kind = Article.Kind
  val Kind: Article.Kind.type = Article.Kind
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  type PLo = DeepTime.SignedYear
  val PLo: DeepTime.SignedYear.type = DeepTime.SignedYear
  type PHi = DeepTime.SignedYear
  val PHi: DeepTime.SignedYear.type = DeepTime.SignedYear
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
