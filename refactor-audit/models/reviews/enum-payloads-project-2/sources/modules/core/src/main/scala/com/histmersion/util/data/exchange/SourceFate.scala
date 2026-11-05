package com.histmersion.util.data.exchange

import java.util.UUID

/** What became of one of the bundle's sources in a plan: the same record as one here (by its uid, or by the ledger),
  * deleted here, one with a source here by itself, one with a source here if a person says so, or new.
  */
enum SourceFate:
  case Same(local: UUID, byLedger: SourceFate.Same.ByLedger)
  case Deleted
  case Auto(pair: SourcePair)
  case Gate(pair: SourcePair, issue: SourceFate.Gate.Issue)
  case New

object SourceFate:
  object Same:
    type ByLedger = ByLedger.T
    object ByLedger extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Gate:
    type Issue = Issue.T
    object Issue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
