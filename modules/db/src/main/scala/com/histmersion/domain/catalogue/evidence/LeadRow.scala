package com.histmersion
package domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

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
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends Newtype[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends Newtype[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends Newtype[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends Newtype[Long, Pure]
  type Tier = Tier.T
  object Tier extends Newtype[String, Pure]
  type Vei = Vei.T
  object Vei extends Newtype[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends Newtype[Double, Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends Newtype[Int, Pure]
  type Proposed = Proposed.T
  object Proposed extends Newtype[Int, Pure]
  type Attesting = Attesting.T
  object Attesting extends Newtype[Int, Pure]
