package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type CitationCount = CitationCount.T
object CitationCount extends RefinedType[Int, Pure]:
  given ConfigReader[CitationCount] = summon[ConfigReader[Int]].emap(CitationCount.either)
