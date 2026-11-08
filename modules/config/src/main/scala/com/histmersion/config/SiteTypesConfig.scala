package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Refining Wikidata sites' types from their items' classes (`events.site-types`). */
final case class SiteTypesConfig(batch: SiteTypesConfig.Batch, superclassDepth: SiteTypesConfig.SuperclassDepth) derives ConfigReader

object SiteTypesConfig:
  type Batch = Batch.T
  object Batch extends RefinedType[Int, Pure]
  type SuperclassDepth = SuperclassDepth.T
  object SuperclassDepth extends RefinedType[Int, Pure]
