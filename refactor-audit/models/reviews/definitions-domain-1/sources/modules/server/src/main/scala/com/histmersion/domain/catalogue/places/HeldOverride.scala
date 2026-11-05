package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.Period

/** A person's kind and period for a place as the in-memory catalogue holds it, with what the place's source said
  * before: a reset puts that back.
  */
final case class HeldOverride(
    placeId: com.histmersion.domain.catalogue.Place.Id,
    kind: Option[HeldOverride.Kind],
    period: Option[(Option[HeldOverride.PeriodPart1], Option[HeldOverride.PeriodPart2])],
    reason: Option[HeldOverride.Reason],
    sourceKind: HeldOverride.SourceKind,
    sourcePeriod: Option[Period],
    updatedAt: HeldOverride.UpdatedAt
)

object HeldOverride:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart2 = PeriodPart2.T
  object PeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceKind = SourceKind.T
  object SourceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
