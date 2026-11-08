package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `infer-filter`: a proposed filter must return at least `minHits` and at most `maxHits` sources, and at least
  * `minCitedShare` (0..1) of the sources the article cites; `sample` sources it returns that the article does not cite
  * are shown with the proposal.
  */
final case class AgentInferFilterConfig(minHits: AgentInferFilterConfig.MinHits, maxHits: AgentInferFilterConfig.MaxHits, minCitedShare: AgentInferFilterConfig.MinCitedShare, sample: AgentInferFilterConfig.Sample)
    derives ConfigReader

object AgentInferFilterConfig:
  type MinHits = MinHits.T
  object MinHits extends RefinedType[Int, Pure]
  type MaxHits = MaxHits.T
  object MaxHits extends RefinedType[Int, Pure]
  type MinCitedShare = MinCitedShare.T
  object MinCitedShare extends RefinedType[Double, Pure]
  type Sample = Sample.T
  object Sample extends RefinedType[Int, Pure]
