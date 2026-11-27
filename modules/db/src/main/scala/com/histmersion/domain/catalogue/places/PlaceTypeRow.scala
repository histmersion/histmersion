package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[places] case class PlaceTypeRow(
    placeId: domain.catalogue.Place.Id,
    typeLabel: PlaceTypeRow.TypeLabel,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    citekey: Option[Citekey]
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def toDto: Place.Type =
    Place.Type(
      Place.Id(placeId.value),
      Place.Type.TypeLabel(typeLabel.value),
      for s <- periodStart; e <- periodEnd
      yield Period(
        com.histmersion.api.catalogue.Year(s.value.toLong),
        com.histmersion.api.catalogue.Year(e.value.toLong)
      ),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object PlaceTypeRow:
  type TypeLabel = TypeLabel.T
  object TypeLabel extends Newtype[String, Pure]
