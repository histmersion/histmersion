package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Values)
type Hours = Hours.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Hours extends Newtype[Int, Pure]:
  given ConfigReader[Hours] = summon[ConfigReader[Int]].emap(Hours.either)
