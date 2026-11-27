package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.UtilCommon)
type ServiceUrl = ServiceUrl.T
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object ServiceUrl extends Newtype[String, Pure]:
  given ConfigReader[ServiceUrl] = summon[ConfigReader[String]].emap(ServiceUrl.either)
