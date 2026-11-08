package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type Seconds = Seconds.T
object Seconds extends Newtype[Int, Pure]:
  given ConfigReader[Seconds] = summon[ConfigReader[Int]].emap(Seconds.either)
