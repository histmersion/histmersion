package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class KeyedServiceConfig(url: KeyedServiceConfig.Url, apiKey: ApiKey) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key: Option[ApiKey] = Option(apiKey).map(_.value.trim).filter(_.nonEmpty).map(ApiKey(_))

object KeyedServiceConfig:

  type Url = Url.T
  object Url extends Newtype[String, Pure]
