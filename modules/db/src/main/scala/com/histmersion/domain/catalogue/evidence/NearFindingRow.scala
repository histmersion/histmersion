package com.histmersion
package domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A dated, placed finding near an event: how far, and the years between. */
final private[evidence] case class NearFindingRow(
    id: NearFindingRow.Id,
    citekey: Citekey,
    claim: NearFindingRow.Claim,
    km: NearFindingRow.Km,
    yearGap: NearFindingRow.YearGap,
    periodStart: Option[Year],
    periodEnd: Option[Year]
) derives DbCodec

object NearFindingRow:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type Km = Km.T
  object Km extends RefinedType[Double, Pure]
  type YearGap = YearGap.T
  object YearGap extends RefinedType[Long, Pure]
