package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type ItemCount = ItemCount.T
object ItemCount extends Newtype[Int, Pure]:
  given ConfigReader[ItemCount] = summon[ConfigReader[Int]].emap(ItemCount.either)
