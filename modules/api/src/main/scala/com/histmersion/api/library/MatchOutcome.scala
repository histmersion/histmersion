package com.histmersion.api.library

import com.histmersion.api.WireEnumValues

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
  type WireOfResult = WireOfResult.T
  object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(o: MatchOutcome): com.histmersion.api.library.MatchOutcome.WireOfResult = o match
    case Same          => _root_.com.histmersion.api.library.MatchOutcome.WireOfResult("same")
    case AutoMerge     => _root_.com.histmersion.api.library.MatchOutcome.WireOfResult("auto-merge")
    case NeedsDecision => _root_.com.histmersion.api.library.MatchOutcome.WireOfResult("needs-decision")
    case New           => _root_.com.histmersion.api.library.MatchOutcome.WireOfResult("new")
    case DeletedHere   => _root_.com.histmersion.api.library.MatchOutcome.WireOfResult("deleted-here")
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome] = MatchOutcome.values
    def wire(e: MatchOutcome): com.histmersion.api.library.MatchOutcome.WireResult = wireOf(e)
    def fromWire(s: com.histmersion.api.library.MatchOutcome.FromWireS): Option[MatchOutcome] = MatchOutcome.values.find(o => wireOf(o) == _root_.com.histmersion.api.library.MatchOutcome.WireOfResult((s).value.trim))
