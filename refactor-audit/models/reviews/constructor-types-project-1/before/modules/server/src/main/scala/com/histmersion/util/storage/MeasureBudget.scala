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
  def take(): com.histmersion.util.storage.MeasureBudget.TakeResult = used.incrementAndGet() <= maxEntries && clock() < deadlineNanos
  def spent: com.histmersion.util.storage.MeasureBudget.SpentResult = used.get
  def exhausted: com.histmersion.util.storage.MeasureBudget.ExhaustedResult = used.get >= maxEntries || clock() >= deadlineNanos

object MeasureBudget:
  type TakeResult = TakeResult.T
  object TakeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpentResult = SpentResult.T
  object SpentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExhaustedResult = ExhaustedResult.T
  object ExhaustedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxEntries = OfMaxEntries.T
  object OfMaxEntries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxMillis = OfMaxMillis.T
  object OfMaxMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(maxEntries: com.histmersion.util.storage.MeasureBudget.OfMaxEntries, maxMillis: com.histmersion.util.storage.MeasureBudget.OfMaxMillis): MeasureBudget =
    MeasureBudget(maxEntries, System.nanoTime() + maxMillis * 1_000_000L)

  def unlimited: MeasureBudget = MeasureBudget(Long.MaxValue, Long.MaxValue)
