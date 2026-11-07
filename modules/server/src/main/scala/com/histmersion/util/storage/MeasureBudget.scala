package com.histmersion.util.storage

import java.util.concurrent.atomic.AtomicLong

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
  def take(): Boolean = used.incrementAndGet() <= maxEntries.value && clock() < deadlineNanos.value
  def spent: com.histmersion.util.storage.MeasureBudget.EntriesUsed = MeasureBudget.EntriesUsed(used.get)
  def exhausted: Boolean = used.get >= maxEntries.value || clock() >= deadlineNanos.value

object MeasureBudget:
  type EntriesUsed = EntriesUsed.T
  object EntriesUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxMillis = MaxMillis.T
  object MaxMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxEntries = MaxEntries.T
  object MaxEntries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeadlineNanos = DeadlineNanos.T
  object DeadlineNanos extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(maxEntries: com.histmersion.util.storage.MeasureBudget.MaxEntries, maxMillis: com.histmersion.util.storage.MeasureBudget.MaxMillis): MeasureBudget =
    MeasureBudget(maxEntries, DeadlineNanos(System.nanoTime() + maxMillis.value * 1_000_000L))

  def unlimited: MeasureBudget = MeasureBudget(MaxEntries(Long.MaxValue), DeadlineNanos(Long.MaxValue))
