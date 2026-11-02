package com.histmersion.domain.library.acquisition

import com.histmersion.domain.AcquireRecheck

import java.time.Instant

/** An acquisition item's last check: when, under which fetcher generation, what it found, why, and how many times it
  * was checked.
  */
final case class AcquireCheckRow(
    item: Int,
    at: Instant,
    generation: String,
    outcome: String,
    reason: String,
    checks: Int
):
  def last: AcquireRecheck.LastCheck =
    AcquireRecheck.LastCheck(
      at,
      generation,
      failed = AcquireRecheck.Outcome.parse(outcome).contains(AcquireRecheck.Outcome.Failed)
    )
