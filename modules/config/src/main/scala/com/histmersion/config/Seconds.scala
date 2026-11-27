package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
@movesTo(FeaturePlace.Values)
type Seconds = Seconds.T
@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Seconds extends Newtype[Int, Pure]:
  given ConfigReader[Seconds] = summon[ConfigReader[Int]].emap(Seconds.either)
