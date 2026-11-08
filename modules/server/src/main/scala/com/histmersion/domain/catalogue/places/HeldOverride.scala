package com.histmersion
package domain.catalogue.places

import com.histmersion.api.Problem
import com.histmersion.api.catalogue.Period
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure

/** A person's kind and period for a place as the in-memory catalogue holds it, with what the place's source said
  * before: a reset puts that back.
  */
final case class HeldOverride(
    placeId: Place.Id,
    kind: Option[HeldOverride.Kind],
    period: Option[(Option[HeldOverride.Bound.Start], Option[HeldOverride.Bound.End])],
    reason: Option[Problem],
    sourceKind: HeldOverride.SourceKind,
    sourcePeriod: Option[Period],
    updatedAt: HeldOverride.UpdatedAt
)

object HeldOverride:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  object Bound:
    type Start = Start.T
    object Start extends Newtype[Long, Pure]
    type End = End.T
    object End extends Newtype[Long, Pure]
  type SourceKind = SourceKind.T
  object SourceKind extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
