package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.UtilCommon)
type ServiceUrl = ServiceUrl.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ServiceUrl extends Newtype[String, Pure]:
  given ConfigReader[ServiceUrl] = summon[ConfigReader[String]].emap(ServiceUrl.either)
