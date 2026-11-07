package com.histmersion.domain.research.articles

import com.augustnagro.magnum.DbCodec
import java.util.UUID

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
  type BodySha = com.histmersion.Sha256
  val BodySha: _root_.com.histmersion.Sha256.type = _root_.com.histmersion.Sha256
  type BodyBytes = com.histmersion.ByteCount
  val BodyBytes: _root_.com.histmersion.ByteCount.type = _root_.com.histmersion.ByteCount
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionOf = com.histmersion.domain.research.Slug
  val VersionOf: _root_.com.histmersion.domain.research.Slug.type = _root_.com.histmersion.domain.research.Slug
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
