package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type TokenCount = TokenCount.T
object TokenCount extends Newtype[Int, Pure]:
  given ConfigReader[TokenCount] = summon[ConfigReader[Int]].emap(TokenCount.either)
