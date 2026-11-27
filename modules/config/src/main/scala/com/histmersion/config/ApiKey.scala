package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Values)
type ApiKey = ApiKey.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ApiKey extends Newtype[String, Pure]:
  given ConfigReader[ApiKey] = summon[ConfigReader[String]].emap(ApiKey.either)
