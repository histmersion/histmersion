package com.histmersion
package api.catalogue

import com.histmersion.api.TimeSpan
import com.histmersion.api.given
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
  object Label extends Newtype[String, Pure]
  export EventSummary.Kind
  export EventLead.Tier
  export EventCatalogueRef.Vei
  export EventCatalogueRef.Magnitude
  export EventLead.FindingsNear
  type Km = Km.T
  object Km extends Newtype[Double, Pure]
  type Years = Years.T
  object Years extends Newtype[Long, Pure]
  type Proposed = Proposed.T
  object Proposed extends Newtype[Int, Pure]
  type Attesting = Attesting.T
  object Attesting extends Newtype[Int, Pure]
