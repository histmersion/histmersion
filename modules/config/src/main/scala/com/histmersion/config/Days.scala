package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type Days = Days.T
object Days extends Newtype[Int, Pure]:
  given ConfigReader[Days] = summon[ConfigReader[Int]].emap(Days.either)
