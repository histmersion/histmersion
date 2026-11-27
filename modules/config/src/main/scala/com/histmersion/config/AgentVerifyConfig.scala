package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The deterministic verifiers (§4.4). `quoteMinRatio` is the OCR-tolerant quote match threshold (1.0 = verbatim after
  * normalisation); `maxAbsYear` bounds a signed year a model may propose.
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class AgentVerifyConfig(
    quoteMinRatio: AgentVerifyConfig.QuoteMinRatio,
    maxAbsYear: AgentVerifyConfig.MaxAbsYear
) derives ConfigReader

object AgentVerifyConfig:
  type QuoteMinRatio = QuoteMinRatio.T
  object QuoteMinRatio extends Newtype[Double, Pure]
  type MaxAbsYear = MaxAbsYear.T
  object MaxAbsYear extends Newtype[Long, Pure]
