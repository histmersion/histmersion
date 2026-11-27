package com.histmersion
package domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A dated, placed finding near an event: how far, and the years between. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
  object Id extends Newtype[String, Pure]
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Km = Km.T
  object Km extends Newtype[Double, Pure]
  type YearGap = YearGap.T
  object YearGap extends Newtype[Long, Pure]
