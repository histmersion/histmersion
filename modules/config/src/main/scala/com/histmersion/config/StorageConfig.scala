package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The Storage part of About (`histmersion about`, `/api/about`, the web About window): how often the server measures
  * its directories (`ttl-seconds`; About answers the last measurement in between), how long a request waits for a
  * measurement under way before it answers "measuring…" (`wait-millis`), the caps of one measurement (`max-millis`,
  * `max-entries` — a walk they stop reports "at least"), how many of the largest tables it lists (`top-tables`) and the
  * free space below which it says what to delete (`low-free-gb`).
  */
final case class StorageConfig(
    ttlSeconds: StorageConfig.TtlSeconds,
    waitMillis: StorageConfig.WaitMillis,
    maxMillis: StorageConfig.MaxMillis,
    maxEntries: StorageConfig.MaxEntries,
    topTables: StorageConfig.TopTables,
    lowFreeGb: StorageConfig.LowFreeGb
) derives ConfigReader:
  def lowFreeBytes: com.histmersion.ByteCount = com.histmersion.ByteCount.option((lowFreeGb.value * 1e9).toLong).getOrElse(com.histmersion.ByteCount.zero)

object StorageConfig:

  type TtlSeconds = TtlSeconds.T
  object TtlSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitMillis = WaitMillis.T
  object WaitMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxMillis = MaxMillis.T
  object MaxMillis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxEntries = MaxEntries.T
  object MaxEntries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TopTables = TopTables.T
  object TopTables extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowFreeGb = LowFreeGb.T
  object LowFreeGb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
