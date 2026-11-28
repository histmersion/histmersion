package com.histmersion
package domain.catalogue.places.placereview

import com.histmersion.domain.catalogue.Place
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.catalogue.places.PlaceReview.periodText
import com.histmersion.domain.catalogue.places.PlaceReview.Rationale
import io.github.iltotore.iron.constraint.any.Pure

enum Remedy:
  case Reclassify(
      id: Evidence.Id,
      label: Evidence.Label,
      from: Place.Kind.Wire,
      to: Place.Kind.Wire,
      period: Option[Period],
      reason: Rationale,
      evidence: List[Remedy.EvidenceLine]
  )

  case Merge(
      id: Evidence.Id,
      label: Evidence.Label,
      into: Evidence.Id,
      intoLabel: Evidence.Label,
      reason: Rationale,
      evidence: List[Remedy.EvidenceLine]
  )

  case Review(id: Evidence.Id, label: Evidence.Label, reason: Rationale, evidence: List[Remedy.EvidenceLine])

  def id: Evidence.Id

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: Remedy.Line = Remedy.Line(s"$id $detail")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def detail: Remedy.Line =
    def ev(e: List[Remedy.EvidenceLine]) = if e.isEmpty then "" else e.mkString(" [", "; ", "]")
    this match
      case Reclassify(_, label, from, to, period, reason, evidence) =>
        val per =
          period.fold("")(p => s", period ${periodText(p)}")
        Remedy.Line(s"$label: $from → $to$per — $reason${ev(evidence)}")
      case Merge(_, label, into, intoLabel, reason, evidence) =>
        Remedy.Line(s"$label: merge into $into $intoLabel — $reason${ev(evidence)}")
      case Review(_, label, reason, evidence) =>
        Remedy.Line(s"$label: review — $reason${ev(evidence)}")

  def fixable: Boolean = this match
    case _: Review => false
    case _         => true

object Remedy:
  type EvidenceLine = EvidenceLine.T
  object EvidenceLine extends Newtype[String, Pure]
  type Line = Line.T
  object Line extends Newtype[String, Pure]
