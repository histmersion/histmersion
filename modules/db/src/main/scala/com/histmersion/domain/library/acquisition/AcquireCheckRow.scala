package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.AcquireRecheck
import AcquireCheckRow.Rationale

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

/** An acquisition item's last check: when, under which fetcher generation, what it found, why, and how many times it
  * was checked.
  */
final case class AcquireCheckRow(
    item: AcquireCheckRow.Item,
    at: Instant,
    generation: AcquireRecheck.LastCheck.Generation,
    outcome: AcquireRecheck.Outcome.Wire,
    reason: Rationale,
    checks: AcquireCheckRow.Checks
):
  def last: AcquireRecheck.LastCheck =
    AcquireRecheck.LastCheck(
      at,
      generation,
      failed =
        AcquireRecheck.LastCheck.Failed(AcquireRecheck.Outcome.parse(outcome).contains(AcquireRecheck.Outcome.Failed))
    )

object AcquireCheckRow:
  type Item = Item.T
  object Item extends Newtype[Int, Pure]
  type Checks = Checks.T
  object Checks extends Newtype[Int, Pure]

  /** The short reason the item carries after the check, as the column keeps it ([[AcquireRecheck.Rationale.said]]). */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
