package com.histmersion.domain.library.acquisition

import com.histmersion.domain.AcquireRecheck

import java.time.Instant

/** An acquisition item's last check: when, under which fetcher generation, what it found, why, and how many times it
  * was checked.
  */
final case class AcquireCheckRow(
    item: AcquireCheckRow.Item,
    at: Instant,
    generation: AcquireRecheck.LastCheck.Generation,
    outcome: AcquireRecheck.Outcome.Wire,
    reason: AcquireCheckRow.Reason,
    checks: AcquireCheckRow.Checks
):
  def last: AcquireRecheck.LastCheck =
    AcquireRecheck.LastCheck(
      at,
      generation,
      failed = AcquireRecheck.LastCheck.Failed(AcquireRecheck.Outcome.parse(outcome).contains(AcquireRecheck.Outcome.Failed))
    )

object AcquireCheckRow:
  type Item = Item.T
  object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Checks = Checks.T
  object Checks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
