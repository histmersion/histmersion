package com.histmersion
package util.database

import com.histmersion.domain.{CreatedAt, Creator}

import java.time.Clock

object TestCreators:
  given test: Creator.New = Creator.New.Manual(Creator.Surface.Internal)

  val clock: Clock = Clock.systemUTC()

  inline given at: CreatedAt = CreatedAt.now(clock)
