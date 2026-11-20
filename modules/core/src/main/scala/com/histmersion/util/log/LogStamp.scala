package com.histmersion
package util.log

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** How the structured log writes its ids and times. Ids are UUIDv7 (RFC 9562: 48 bits of Unix milliseconds, the
  * version, 74 random bits), so they sort by time; times are ISO-8601 in UTC to the microsecond, as the database keeps
  * them, so the table and the file compare the same. Pure: the caller supplies the clock's reading and the randomness.
  */
object LogStamp:

  /** The UUIDv7 of `millis` and the random bits `high` (12 used) and `low` (62 used), in lower case. */
  def uuidV7(millis: Long, high: Long, low: Long): String =
    val msb = ((millis & 0xffffffffffffL) << 16) | 0x7000L | (high & 0x0fffL)
    val lsb = (low & 0x3fffffffffffffffL) | Long.MinValue // the variant bits 10
    java.util.UUID(msb, lsb).toString

  private val Iso = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  /** `at` to the microsecond, as `2026-11-20T09:15:02.123456Z`. */
  def iso(at: Instant): String = Iso.format(at.truncatedTo(ChronoUnit.MICROS))

  /** The UTC day of an ISO time this log wrote (`2026-11-20`): the file it goes in. */
  def day(iso: String): String = iso.take(10)
