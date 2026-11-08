package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type Percent = Percent.T
object Percent extends Newtype[Int, Pure]:
  given ConfigReader[Percent] = summon[ConfigReader[Int]].emap(Percent.either)
