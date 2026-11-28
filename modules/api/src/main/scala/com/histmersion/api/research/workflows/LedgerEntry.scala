package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class LedgerEntry(
    finding: ReviewFinding,
    run: LedgerEntry.Run,
    round: LedgerEntry.Round,
    status: LedgerStatus = LedgerStatus.Open,
    claimed: LedgerEntry.Claimed = LedgerEntry.Claimed(false),
    notFixed: Option[LedgerEntry.NotFixed] = None,
    reopened: LedgerEntry.Reopened = LedgerEntry.Reopened(0),
    closed: Option[Closure] = None,
    notFixedCount: LedgerEntry.NotFixedCount = LedgerEntry.NotFixedCount(0),
    deferred: Option[LedgerEntry.Deferred] = None
) derives JsonCodec

object LedgerEntry:
  export Merged.Round
  type Claimed = Claimed.T
  object Claimed extends Newtype[Boolean, Pure]
  type NotFixed = NotFixed.T
  object NotFixed extends Newtype[String, Pure]
  type Reopened = Reopened.T
  object Reopened extends Newtype[Int, Pure]
  type NotFixedCount = NotFixedCount.T
  object NotFixedCount extends Newtype[Int, Pure]
  export RunInfo.Id as Run
  export RunInfo.Id as Deferred
