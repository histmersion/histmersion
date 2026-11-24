package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Values)
type ItemCount = ItemCount.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ItemCount extends Newtype[Int, Pure]:
  given ConfigReader[ItemCount] = summon[ConfigReader[Int]].emap(ItemCount.either)
