package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `propose-articles`: at most `maxArticles` per plan; each one's filter returns at least `minSources` sources and
  * shares less than `maxOverlap` (0..1) of them with any article of the research area.
  */
final case class AgentSeedConfig(maxArticles: AgentSeedConfig.MaxArticles, minSources: AgentSeedConfig.MinSources, maxOverlap: AgentSeedConfig.MaxOverlap) derives ConfigReader

object AgentSeedConfig:
  type MaxArticles = MaxArticles.T
  object MaxArticles extends RefinedType[Int, Pure]
  type MinSources = MinSources.T
  object MinSources extends RefinedType[Int, Pure]
  type MaxOverlap = MaxOverlap.T
  object MaxOverlap extends RefinedType[Double, Pure]
