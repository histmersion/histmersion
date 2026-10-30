package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Hours = Hours.T
object Hours extends RefinedType[Int, Pure]:
  given ConfigReader[Hours] = summon[ConfigReader[Int]].emap(Hours.either)
