package com.histmersion.domain.catalogue.periods

/** Where the live PeriodO dump is and which of its collections are imported when none is named. */
final case class PeriodSources(periodoUrl: PeriodSources.PeriodoUrl, collections: List[PeriodSources.Collections])

object PeriodSources:
  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Collections = Collections.T
  object Collections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
