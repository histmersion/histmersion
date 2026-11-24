package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Values)
type TokenCount = TokenCount.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object TokenCount extends Newtype[Int, Pure]:
  given ConfigReader[TokenCount] = summon[ConfigReader[Int]].emap(TokenCount.either)
