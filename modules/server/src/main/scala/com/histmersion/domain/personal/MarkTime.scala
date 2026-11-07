package com.histmersion.domain.personal

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneOffset}

/** When a bookmark, a note or a read mark was made or changed: to the microsecond, as Postgres keeps it. */
type MarkTime = MarkTime.T

object MarkTime extends RefinedType[Instant, Pure]:
  type Iso = Iso.T
  object Iso extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  def at(instant: Instant): MarkTime = MarkTime(instant.truncatedTo(ChronoUnit.MICROS))

  /** A time as the database's `to_char` or the wire writes it. */
  def parse(iso: com.histmersion.domain.personal.MarkTime.Iso): MarkTime = at(Instant.parse(iso))

  extension (t: MarkTime)
    /** As the wire writes it (`2026-10-30T12:40:00.123456Z`), the same text the database answers. */
    def iso: com.histmersion.domain.personal.MarkTime.Iso = Iso.format(t.value)
    def instant: Instant = t.value
