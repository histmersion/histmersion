package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Minutes = Minutes.T
object Minutes extends RefinedType[Int, Pure]:
  given ConfigReader[Minutes] = summon[ConfigReader[Int]].emap(Minutes.either)
