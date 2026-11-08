package com.histmersion
package api.catalogue

import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class LeadFacts(
    eventId: Event.Id,
    label: LeadFacts.Label,
    kind: LeadFacts.Kind,
    timeSpan: TimeSpan,
    tier: LeadFacts.Tier,
    vei: Option[LeadFacts.Vei],
    magnitude: Option[LeadFacts.Magnitude],
    findingsNear: LeadFacts.FindingsNear,
    km: LeadFacts.Km,
    years: LeadFacts.Years,
    proposed: LeadFacts.Proposed,
    attesting: LeadFacts.Attesting
)

object LeadFacts:
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Tier = Tier.T
  object Tier extends RefinedType[String, Pure]
  type Vei = Vei.T
  object Vei extends RefinedType[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends RefinedType[Double, Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends RefinedType[Int, Pure]
  type Km = Km.T
  object Km extends RefinedType[Double, Pure]
  type Years = Years.T
  object Years extends RefinedType[Long, Pure]
  type Proposed = Proposed.T
  object Proposed extends RefinedType[Int, Pure]
  type Attesting = Attesting.T
  object Attesting extends RefinedType[Int, Pure]
