package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** A keyless upstream service. */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class ServiceConfig(url: ServiceConfig.Url) derives ConfigReader

object ServiceConfig:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Url = ServiceUrl
  val Url: ServiceUrl.type = ServiceUrl
