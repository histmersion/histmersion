package com.histmersion
package domain.research.articles

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

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
    updatedAt: BArticleRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BArticleRow:
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
  enum PNull derives CanEqual:
    case Undated, Dated
  object PNull extends TwoCases[PNull]:
    extension (pNull: PNull) def pNull: Boolean = pNull == Undated
  export DeepTime.{SignedYear as PHi, SignedYear as PLo}
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
