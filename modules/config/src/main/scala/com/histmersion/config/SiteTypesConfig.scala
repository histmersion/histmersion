package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Refining Wikidata sites' types from their items' classes (`events.site-types`). */
@movesTo(FeaturePlace.CataloguePlaces)
final case class SiteTypesConfig(batch: SiteTypesConfig.Batch, superclassDepth: SiteTypesConfig.SuperclassDepth)
    derives ConfigReader

object SiteTypesConfig:
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
  type SuperclassDepth = SuperclassDepth.T
  object SuperclassDepth extends Newtype[Int, Pure]
