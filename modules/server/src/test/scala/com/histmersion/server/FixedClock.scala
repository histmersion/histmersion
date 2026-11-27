package com.histmersion
package server

import java.time.{Clock, Instant, ZoneOffset}

object FixedClock:
  val clock: Clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)

  val system: SystemContext = SystemContext.isolated.copy(clock = clock)
