package com.histmersion
package util.data.exchange

import java.util.UUID
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

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
    enum ByLedger derives CanEqual:
      case ByLedger, ByMatch
    object ByLedger extends TwoCases[ByLedger]:
      extension (byLedger: ByLedger) def byLedger: Boolean = byLedger == ByLedger
  object Gate:
    type Issue = Issue.T
    object Issue extends Newtype[String, Pure]
