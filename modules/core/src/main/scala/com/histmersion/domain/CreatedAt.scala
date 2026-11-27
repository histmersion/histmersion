package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Clock, ZoneOffset}

type CreatedAt = CreatedAt.T

object CreatedAt extends Newtype[java.time.Instant, Pure]:
  type Iso = Iso.T
  object Iso extends Newtype[String, Pure]

  private val Format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  def now(clock: Clock): CreatedAt = CreatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS))

  extension (at: CreatedAt) def iso: Iso = Iso(Format.format(at.value))
