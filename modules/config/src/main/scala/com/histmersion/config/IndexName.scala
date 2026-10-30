package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type IndexName = IndexName.T
object IndexName extends RefinedType[String, Pure]:
  given ConfigReader[IndexName] = summon[ConfigReader[String]].emap(IndexName.either)
