package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

import java.time.Clock
import java.time.temporal.ChronoUnit

type CreatedAt = CreatedAt.T

object CreatedAt extends Newtype[java.time.Instant, Pure]:
  def now(clock: Clock): CreatedAt = CreatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS))
