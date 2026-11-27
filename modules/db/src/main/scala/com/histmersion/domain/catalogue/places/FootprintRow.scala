package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[places] case class FootprintRow(
    id: FootprintRow.Id,
    placeId: domain.catalogue.Place.Id,
    role: FootprintRow.Role,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    certainty: Option[FootprintRow.Certainty],
    accuracyM: Option[FootprintRow.AccuracyM],
    citekey: Option[FootprintRow.Citekey]
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def toDto: Place.Footprint =
    Place.Footprint(
      Place.Footprint.Id(id.value),
      Place.Id(placeId.value),
      Place.Footprint.Role(role.value),
      for s <- periodStart; e <- periodEnd
      yield Period(
        com.histmersion.api.catalogue.Year(s.value.toLong),
        com.histmersion.api.catalogue.Year(e.value.toLong)
      ),
      certainty.map(c => Place.Footprint.Certainty(c.value)),
      accuracyM.map(a => Place.Footprint.AccuracyM(a.value)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object FootprintRow:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[Int, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Certainty = Certainty.T
  object Certainty extends Newtype[String, Pure]
  type AccuracyM = AccuracyM.T
  object AccuracyM extends Newtype[Float, Pure]
