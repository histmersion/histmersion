package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The deterministic verifiers (§4.4). `quoteMinRatio` is the OCR-tolerant quote match threshold (1.0 = verbatim after
  * normalisation); `maxAbsYear` bounds a signed year a model may propose.
  */
final case class AgentVerifyConfig(quoteMinRatio: AgentVerifyConfig.QuoteMinRatio, maxAbsYear: AgentVerifyConfig.MaxAbsYear) derives ConfigReader

object AgentVerifyConfig:
  type QuoteMinRatio = QuoteMinRatio.T
  object QuoteMinRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxAbsYear = MaxAbsYear.T
  object MaxAbsYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
