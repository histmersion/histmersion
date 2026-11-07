package com.histmersion.domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** A finding's row with its id, as a write path reads it back. */
final private[sources] case class FindingIdRow(
    id: FindingIdRow.Id,
    claim: FindingIdRow.Claim,
    regionName: Option[FindingIdRow.RegionName],
    lon: Option[com.histmersion.Longitude],
    lat: Option[com.histmersion.Latitude],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    periodName: Option[FindingIdRow.PeriodName],
    tags: List[FindingIdRow.Tags],
    confidence: Option[Confidence],
    significance: Option[FindingIdRow.Significance]
) derives DbCodec

object FindingIdRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionName = RegionName.T
  object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Significance = Significance.T
  object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
