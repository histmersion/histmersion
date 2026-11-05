package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One fetcher's answer when it had no copy: `refused` (a copy it may not take) or a miss. */
final case class FetcherAnswer(fetcher: FetcherAnswer.Fetcher, refused: FetcherAnswer.Refused, why: FetcherAnswer.Why)

object FetcherAnswer:
  type Fetcher = Fetcher.T
  object Fetcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refused = Refused.T
  object Refused extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Why = Why.T
  object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
