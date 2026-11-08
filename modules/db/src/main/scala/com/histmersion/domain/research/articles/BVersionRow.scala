package com.histmersion
package domain.research.articles

import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An article's version as a bundle reads it: its body by digest and length, carried as a blob. */
final private[articles] case class BVersionRow(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha: BVersionRow.BodySha,
    bodyBytes: BVersionRow.BodyBytes,
    summary: Option[BVersionRow.Summary],
    versionOf: BVersionRow.VersionOf,
    createdAt: BVersionRow.CreatedAt
) derives DbCodec

object BVersionRow:
  type BodySha = Sha256
  val BodySha: Sha256.type = Sha256
  type BodyBytes = ByteCount
  val BodyBytes: ByteCount.type = ByteCount
  type Summary = Summary.T
  object Summary extends RefinedType[String, Pure]
  type VersionOf = VersionOf.T
  object VersionOf extends RefinedType[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends RefinedType[String, Pure]
