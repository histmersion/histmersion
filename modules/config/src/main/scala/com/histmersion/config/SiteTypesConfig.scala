package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** Refining Wikidata sites' types from their items' classes (`events.site-types`). */
final case class SiteTypesConfig(batch: SiteTypesConfig.Batch, superclassDepth: SiteTypesConfig.SuperclassDepth) derives ConfigReader

object SiteTypesConfig:
  type Batch = Batch.T
  object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SuperclassDepth = SuperclassDepth.T
  object SuperclassDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
