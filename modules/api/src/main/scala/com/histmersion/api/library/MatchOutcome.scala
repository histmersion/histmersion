package com.histmersion
package api.library

import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

enum MatchOutcome derives CanEqual:
  /** The same record here (by uid or ledger): imported as an update, if anything. */
  case Same

  /** Another record here is the same work, sure enough to merge without asking. */
  case AutoMerge

  /** Another record here may be the same work: a [[IssueKind.SameWork]] issue decides it. */
  case NeedsDecision

  /** A new source. */
  case New

  /** Deleted here: not brought back. */
  case DeletedHere

object MatchOutcome:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def wireOf(o: MatchOutcome): Wire = o match
    case Same          => Wire("same")
    case AutoMerge     => Wire("auto-merge")
    case NeedsDecision => Wire("needs-decision")
    case New           => Wire("new")
    case DeletedHere   => Wire("deleted-here")
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: MatchOutcome): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[MatchOutcome] = values.find(o => wireOf(o) == Wire((s).value.trim))
