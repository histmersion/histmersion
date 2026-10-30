package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Percent = Percent.T
object Percent extends RefinedType[Int, Pure]:
  given ConfigReader[Percent] = summon[ConfigReader[Int]].emap(Percent.either)
