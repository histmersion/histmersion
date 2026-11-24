package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Values)
type Percent = Percent.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Percent extends Newtype[Int, Pure]:
  given ConfigReader[Percent] = summon[ConfigReader[Int]].emap(Percent.either)
