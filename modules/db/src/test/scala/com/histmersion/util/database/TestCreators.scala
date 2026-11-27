package com.histmersion
package util.database

import com.histmersion.domain.{CreatedAt, Creator}

import java.time.{Clock, Instant, ZoneOffset}

object TestCreators:
  given test: Creator.New = Creator.New.Manual(Creator.Surface.Internal)

  val clock: Clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)

  given at: CreatedAt = CreatedAt.now(clock)
