package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Values)
type ApiKey = ApiKey.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ApiKey extends Newtype[String, Pure]:
  given ConfigReader[ApiKey] = summon[ConfigReader[String]].emap(ApiKey.either)
