package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `propose-articles`: at most `maxArticles` per plan; each one's filter returns at least `minSources` sources and
  * shares less than `maxOverlap` (0..1) of them with any article of the research area.
  */
final case class AgentSeedConfig(maxArticles: AgentSeedConfig.MaxArticles, minSources: AgentSeedConfig.MinSources, maxOverlap: AgentSeedConfig.MaxOverlap) derives ConfigReader

object AgentSeedConfig:
  type MaxArticles = MaxArticles.T
  object MaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinSources = MinSources.T
  object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxOverlap = MaxOverlap.T
  object MaxOverlap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
