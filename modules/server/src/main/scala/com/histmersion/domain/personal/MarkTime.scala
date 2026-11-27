package com.histmersion
package domain.personal

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneOffset}
import com.histmersion.lint.{tolerated, LintRule}

/** When a bookmark, a note or a read mark was made or changed: to the microsecond, as Postgres keeps it. */
type MarkTime = MarkTime.T

object MarkTime extends Newtype[Instant, Pure]:
  type Iso = Iso.T
  object Iso extends Newtype[String, Pure]

  private val Format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  def at(instant: Instant): MarkTime = MarkTime(instant.truncatedTo(ChronoUnit.MICROS))

  /** A time as the database's `to_char` or the wire writes it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(iso: Iso): MarkTime = at(Instant.parse(iso.value))

  extension (t: MarkTime)
    /** As the wire writes it (`2026-10-30T12:40:00.123456Z`), the same text the database answers. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def iso: Iso = Iso(Format.format(t.value))
    def instant: Instant = t.value
