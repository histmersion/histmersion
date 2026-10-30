package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Seconds = Seconds.T
object Seconds extends RefinedType[Int, Pure]:
  given ConfigReader[Seconds] = summon[ConfigReader[Int]].emap(Seconds.either)
