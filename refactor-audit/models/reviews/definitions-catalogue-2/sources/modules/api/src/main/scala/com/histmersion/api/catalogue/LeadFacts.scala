package com.histmersion.api.catalogue

import com.histmersion.api.TimeSpan
import com.histmersion.api.given
final case class LeadFacts(
    eventId: com.histmersion.api.catalogue.Event.Id,
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
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Km = Km.T
  object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Years = Years.T
  object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Proposed = Proposed.T
  object Proposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attesting = Attesting.T
  object Attesting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
