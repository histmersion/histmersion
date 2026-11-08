package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type CitationCount = CitationCount.T
object CitationCount extends Newtype[Int, Pure]:
  given ConfigReader[CitationCount] = summon[ConfigReader[Int]].emap(CitationCount.either)
