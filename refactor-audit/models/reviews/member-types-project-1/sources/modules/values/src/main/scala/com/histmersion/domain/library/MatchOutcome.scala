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
  type WireOfResult = WireOfResult.T
  object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(o: MatchOutcome): com.histmersion.domain.library.MatchOutcome.WireOfResult = o match
    case Same          => "same"
    case AutoMerge     => "auto-merge"
    case NeedsDecision => "needs-decision"
    case New           => "new"
    case DeletedHere   => "deleted-here"
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome] = MatchOutcome.values
    def wire(e: MatchOutcome): com.histmersion.domain.library.MatchOutcome.WireResult = wireOf(e)
    def fromWire(s: com.histmersion.domain.library.MatchOutcome.FromWireS): Option[MatchOutcome] = MatchOutcome.values.find(o => wireOf(o) == s.trim)
