package com.histmersion
package domain.catalogue.places.placereview

import com.histmersion.domain.catalogue.Place
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.catalogue.places.PlaceReview.periodText
import com.histmersion.domain.catalogue.places.PlaceReview.Rationale
import io.github.iltotore.iron.constraint.any.Pure

enum Proposal:
  /** Change the kind (and, for a polity, the period). */
  case Reclassify(
      id: Evidence.Id,
      label: Evidence.Label,
      from: Place.Kind.Wire,
      to: Place.Kind.Wire,
      period: Option[Period],
      reason: Rationale,
      evidence: List[Proposal.EvidenceLine]
  )

  /** Move the place's findings, links and children to `into`, delete it, keep its id as an alias. */
  case Merge(
      id: Evidence.Id,
      label: Evidence.Label,
      into: Evidence.Id,
      intoLabel: Evidence.Label,
      reason: Rationale,
      evidence: List[Proposal.EvidenceLine]
  )

  /** Nothing automatic: a person decides. */
  case Review(id: Evidence.Id, label: Evidence.Label, reason: Rationale, evidence: List[Proposal.EvidenceLine])

  def id: Evidence.Id

  /** One line: id, label, current → proposed kind, why, and the evidence. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: Proposal.Line = Proposal.Line(s"$id $detail")

  /** The line without the id — a lint item, which carries the id beside it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def detail: Proposal.Line =
    def ev(e: List[Proposal.EvidenceLine]) = if e.isEmpty then "" else e.mkString(" [", "; ", "]")
    this match
      case Reclassify(_, label, from, to, period, reason, evidence) =>
        val per =
          period.fold("")(p => s", period ${periodText(p)}")
        Proposal.Line(s"$label: $from → $to$per — $reason${ev(evidence)}")
      case Merge(_, label, into, intoLabel, reason, evidence) =>
        Proposal.Line(s"$label: merge into $into $intoLabel — $reason${ev(evidence)}")
      case Review(_, label, reason, evidence) =>
        Proposal.Line(s"$label: review — $reason${ev(evidence)}")

  /** Does `--fix` act on it? */
  def fixable: Boolean = this match
    case _: Review => false
    case _         => true

object Proposal:
  type EvidenceLine = EvidenceLine.T
  object EvidenceLine extends Newtype[String, Pure]
  type Line = Line.T
  object Line extends Newtype[String, Pure]
