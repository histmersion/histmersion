package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type ItemCount = ItemCount.T
object ItemCount extends RefinedType[Int, Pure]:
  given ConfigReader[ItemCount] = summon[ConfigReader[Int]].emap(ItemCount.either)
