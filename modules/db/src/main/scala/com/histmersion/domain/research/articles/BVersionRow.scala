package com.histmersion
package domain.research.articles

import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}

/** An article's version as a bundle reads it: its body by digest and length, carried as a blob. */
final private[articles] case class BVersionRow(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha: BVersionRow.BodySha,
    bodyBytes: BVersionRow.BodyBytes,
    summary: Option[BVersionRow.Summary],
    versionOf: BVersionRow.VersionOf,
    createdAt: BVersionRow.CreatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BVersionRow:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type BodySha = Sha256
  val BodySha: Sha256.type = Sha256
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type BodyBytes = ByteCount
  val BodyBytes: ByteCount.type = ByteCount
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type VersionOf = VersionOf.T
  object VersionOf extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
