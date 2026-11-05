package com.histmersion.util.data.exchange

import java.util.UUID

/** What became of one of the bundle's sources in a plan: the same record as one here (by its uid, or by the ledger),
  * deleted here, one with a source here by itself, one with a source here if a person says so, or new.
  */
enum SourceFate:
  case Same(local: UUID, byLedger: Boolean)
  case Deleted
  case Auto(pair: SourcePair)
  case Gate(pair: SourcePair, issue: String)
  case New
