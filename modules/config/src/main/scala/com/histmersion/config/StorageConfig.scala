package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The Storage part of About (`histmersion about`, `/api/about`, the web About window): how often the server measures
  * its directories (`ttl-seconds`; About answers the last measurement in between), how long a request waits for a
  * measurement under way before it answers "measuring…" (`wait-millis`), the caps of one measurement (`max-millis`,
  * `max-entries` — a walk they stop reports "at least"), how many of the largest tables it lists (`top-tables`) and the
  * free space below which it says what to delete (`low-free-gb`).
  */
final case class StorageConfig(
    ttlSeconds: Int,
    waitMillis: Int,
    maxMillis: Int,
    maxEntries: Int,
    topTables: Int,
    lowFreeGb: Double
) derives ConfigReader:
  def lowFreeBytes: Long = (lowFreeGb * 1e9).toLong
