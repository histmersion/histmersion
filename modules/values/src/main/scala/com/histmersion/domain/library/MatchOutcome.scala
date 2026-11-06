package com.histmersion.domain.library

import com.histmersion.WireEnumValues
enum MatchOutcome:
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
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(o: MatchOutcome): com.histmersion.domain.library.MatchOutcome.Wire = o match
    case Same          => _root_.com.histmersion.domain.library.MatchOutcome.Wire("same")
    case AutoMerge     => _root_.com.histmersion.domain.library.MatchOutcome.Wire("auto-merge")
    case NeedsDecision => _root_.com.histmersion.domain.library.MatchOutcome.Wire("needs-decision")
    case New           => _root_.com.histmersion.domain.library.MatchOutcome.Wire("new")
    case DeletedHere   => _root_.com.histmersion.domain.library.MatchOutcome.Wire("deleted-here")
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome] = MatchOutcome.values
    def wire(e: MatchOutcome): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[MatchOutcome] = MatchOutcome.values.find(o => wireOf(o) == _root_.com.histmersion.domain.library.MatchOutcome.Wire((s).value.trim))
