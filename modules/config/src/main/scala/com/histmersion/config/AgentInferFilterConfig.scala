package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `infer-filter`: a proposed filter must return at least `minHits` and at most `maxHits` sources, and at least
  * `minCitedShare` (0..1) of the sources the article cites; `sample` sources it returns that the article does not cite
  * are shown with the proposal.
  */
final case class AgentInferFilterConfig(minHits: AgentInferFilterConfig.MinHits, maxHits: AgentInferFilterConfig.MaxHits, minCitedShare: AgentInferFilterConfig.MinCitedShare, sample: AgentInferFilterConfig.Sample)
    derives ConfigReader

object AgentInferFilterConfig:
  type MinHits = MinHits.T
  object MinHits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxHits = MaxHits.T
  object MaxHits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinCitedShare = MinCitedShare.T
  object MinCitedShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sample = Sample.T
  object Sample extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
