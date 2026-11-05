package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The deterministic verifiers (§4.4). `quoteMinRatio` is the OCR-tolerant quote match threshold (1.0 = verbatim after
  * normalisation); `maxAbsYear` bounds a signed year a model may propose.
  */
final case class AgentVerifyConfig(quoteMinRatio: Double, maxAbsYear: Long) derives ConfigReader
