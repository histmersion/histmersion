package com.histmersion
package domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.util.database.Jsonb

/** A finding as the bundle reads it, with its source's uid. */
final private[sources] case class BFindingRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    id: BFindingRow.Id,
    claim: BFindingRow.Claim,
    region: Option[BFindingRow.Region],
    lon: Option[Longitude],
    lat: Option[Latitude],
    pNull: BFindingRow.PNull,
    pLo: Option[BFindingRow.PLo],
    pHi: Option[BFindingRow.PHi],
    periodName: Option[BFindingRow.PeriodName],
    tags: List[BFindingRow.Tags],
    confidence: Option[Confidence],
    significance: Option[BFindingRow.Significance],
    updatedAt: BFindingRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BFindingRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  export DeepTime.{SignedYear as PHi, SignedYear as PLo}
  type PeriodName = PeriodName.T
  object PeriodName extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Significance = Significance.T
  object Significance extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
