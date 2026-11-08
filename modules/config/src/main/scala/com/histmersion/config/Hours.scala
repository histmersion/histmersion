package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type Hours = Hours.T
object Hours extends Newtype[Int, Pure]:
  given ConfigReader[Hours] = summon[ConfigReader[Int]].emap(Hours.either)
