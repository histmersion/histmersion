package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** `propose-articles`: at most `maxArticles` per plan; each one's filter returns at least `minSources` sources and
  * shares less than `maxOverlap` (0..1) of them with any article of the research area.
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class AgentSeedConfig(
    maxArticles: AgentSeedConfig.MaxArticles,
    minSources: AgentSeedConfig.MinSources,
    maxOverlap: AgentSeedConfig.MaxOverlap
) derives ConfigReader

object AgentSeedConfig:
  type MaxArticles = MaxArticles.T
  object MaxArticles extends Newtype[Int, Pure]
  type MinSources = MinSources.T
  object MinSources extends Newtype[Int, Pure]
  type MaxOverlap = MaxOverlap.T
  object MaxOverlap extends Newtype[Double, Pure]
