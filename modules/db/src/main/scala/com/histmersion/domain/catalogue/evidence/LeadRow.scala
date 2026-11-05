package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec

/** The facts one event's lead is computed from: its dates, tier and size, the findings near it, the sources proposed
  * and confirmed.
  */
final case class LeadRow(
    id: LeadRow.Id,
    label: LeadRow.Label,
    kind: LeadRow.Kind,
    notBefore: Option[LeadRow.NotBefore],
    beginBy: Option[LeadRow.BeginBy],
    endFrom: Option[LeadRow.EndFrom],
    notAfter: Option[LeadRow.NotAfter],
    tier: LeadRow.Tier,
    vei: Option[LeadRow.Vei],
    magnitude: Option[LeadRow.Magnitude],
    findingsNear: LeadRow.FindingsNear,
    proposed: LeadRow.Proposed,
    attesting: LeadRow.Attesting
) derives DbCodec

object LeadRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Proposed = Proposed.T
  object Proposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attesting = Attesting.T
  object Attesting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
