package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A finding's row with its id, as a write path reads it back. */
final private[sources] case class FindingIdRow(
    id: FindingIdRow.Id,
    claim: FindingIdRow.Claim,
    regionName: Option[FindingIdRow.RegionName],
    lon: Option[Longitude],
    lat: Option[Latitude],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    periodName: Option[FindingIdRow.PeriodName],
    tags: List[FindingIdRow.Tags],
    confidence: Option[Confidence],
    significance: Option[FindingIdRow.Significance]
) derives DbCodec

object FindingIdRow:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type RegionName = RegionName.T
  object RegionName extends RefinedType[String, Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Significance = Significance.T
  object Significance extends RefinedType[String, Pure]
