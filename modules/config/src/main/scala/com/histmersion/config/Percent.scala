package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Values)
type Percent = Percent.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Percent extends Newtype[Int, Pure]:
  given ConfigReader[Percent] = summon[ConfigReader[Int]].emap(Percent.either)
