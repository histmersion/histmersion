package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Values)
type Days = Days.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Days extends Newtype[Int, Pure]:
  given ConfigReader[Days] = summon[ConfigReader[Int]].emap(Days.either)
