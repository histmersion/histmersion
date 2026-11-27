package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** A keyless upstream service. */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class ServiceConfig(url: ServiceConfig.Url) derives ConfigReader

object ServiceConfig:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Url = ServiceUrl
  val Url: ServiceUrl.type = ServiceUrl
