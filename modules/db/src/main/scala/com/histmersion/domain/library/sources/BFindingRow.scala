package com.histmersion.domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** A finding as the bundle reads it, with its source's uid. */
final private[sources] case class BFindingRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    id: BFindingRow.Id,
    claim: BFindingRow.Claim,
    region: Option[BFindingRow.Region],
    lon: Option[com.histmersion.Longitude],
    lat: Option[com.histmersion.Latitude],
    pNull: BFindingRow.PNull,
    pLo: Option[BFindingRow.PLo],
    pHi: Option[BFindingRow.PHi],
    periodName: Option[BFindingRow.PeriodName],
    tags: List[BFindingRow.Tags],
    confidence: Option[Confidence],
    significance: Option[BFindingRow.Significance],
    updatedAt: BFindingRow.UpdatedAt
) derives DbCodec

object BFindingRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = com.histmersion.domain.DeepTime.SignedYear
  val PLo: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type PHi = com.histmersion.domain.DeepTime.SignedYear
  val PHi: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type PeriodName = PeriodName.T
  object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Significance = Significance.T
  object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
