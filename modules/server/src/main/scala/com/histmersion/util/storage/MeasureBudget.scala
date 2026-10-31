package com.histmersion.util.storage

import java.util.concurrent.atomic.AtomicLong

/** The caps of one measurement of stores: at most `maxEntries` visited entries over all its walks, and no walk past
  * `deadlineNanos` (on `clock`'s scale). Thread-safe; one per measurement.
  */
final class MeasureBudget(
    val maxEntries: Long,
    val deadlineNanos: Long,
    val clock: () => Long = () => System.nanoTime()
):
  private val used = AtomicLong(0L)

  /** Count one entry; false once either cap is reached. */
  def take(): Boolean = used.incrementAndGet() <= maxEntries && clock() < deadlineNanos
  def spent: Long = used.get
  def exhausted: Boolean = used.get >= maxEntries || clock() >= deadlineNanos

object MeasureBudget:
  def of(maxEntries: Long, maxMillis: Long): MeasureBudget =
    MeasureBudget(maxEntries, System.nanoTime() + maxMillis * 1_000_000L)

  def unlimited: MeasureBudget = MeasureBudget(Long.MaxValue, Long.MaxValue)
