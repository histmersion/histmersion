package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Days = Days.T
object Days extends RefinedType[Int, Pure]:
  given ConfigReader[Days] = summon[ConfigReader[Int]].emap(Days.either)
