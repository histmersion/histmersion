package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec

/** A dated, placed finding near an event: how far, and the years between. */
final private[evidence] case class NearFindingRow(
    id: NearFindingRow.Id,
    citekey: com.histmersion.domain.library.Citekey,
    claim: NearFindingRow.Claim,
    km: NearFindingRow.Km,
    yearGap: NearFindingRow.YearGap,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year]
) derives DbCodec

object NearFindingRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Km = Km.T
  object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearGap = YearGap.T
  object YearGap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
