package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.Period
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure

/** A person's kind and period for a place as the in-memory catalogue holds it, with what the place's source said
  * before: a reset puts that back.
  */
final case class HeldOverride(
    placeId: Place.Id,
    kind: Option[HeldOverride.Kind],
    period: Option[(Option[HeldOverride.PeriodPart1], Option[HeldOverride.PeriodPart2])],
    reason: Option[HeldOverride.Reason],
    sourceKind: HeldOverride.SourceKind,
    sourcePeriod: Option[Period],
    updatedAt: HeldOverride.UpdatedAt
)

object HeldOverride:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends Newtype[Long, Pure]
  type PeriodPart2 = PeriodPart2.T
  object PeriodPart2 extends Newtype[Long, Pure]
  type Reason = Reason.T
  object Reason extends Newtype[String, Pure]
  type SourceKind = SourceKind.T
  object SourceKind extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
