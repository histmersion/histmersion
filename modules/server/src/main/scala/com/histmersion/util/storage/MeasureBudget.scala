package com.histmersion
package util.storage

import java.util.concurrent.atomic.AtomicLong
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The caps of one measurement of stores: at most `maxEntries` visited entries over all its walks, and no walk past
  * `deadlineNanos` (on `clock`'s scale). Thread-safe; one per measurement.
  */
final class MeasureBudget(
    val maxEntries: MeasureBudget.MaxEntries,
    val deadlineNanos: MeasureBudget.DeadlineNanos,
    val clock: () => Long = () => System.nanoTime()
):
  private val used = AtomicLong(0L)

  /** Count one entry; false once either cap is reached. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def take(): Boolean = used.incrementAndGet() <= maxEntries.value && clock() < deadlineNanos.value
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spent: MeasureBudget.EntriesUsed = MeasureBudget.EntriesUsed(used.get)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def exhausted: Boolean = used.get >= maxEntries.value || clock() >= deadlineNanos.value

object MeasureBudget:
  type EntriesUsed = EntriesUsed.T
  object EntriesUsed extends Newtype[Long, Pure]
  type MaxMillis = MaxMillis.T
  object MaxMillis extends Newtype[Long, Pure]
  type MaxEntries = MaxEntries.T
  object MaxEntries extends Newtype[Long, Pure]
  type DeadlineNanos = DeadlineNanos.T
  object DeadlineNanos extends Newtype[Long, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(maxEntries: MaxEntries, maxMillis: MaxMillis): MeasureBudget =
    MeasureBudget(maxEntries, DeadlineNanos(System.nanoTime() + maxMillis.value * 1_000_000L))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unlimited: MeasureBudget = MeasureBudget(MaxEntries(Long.MaxValue), DeadlineNanos(Long.MaxValue))
