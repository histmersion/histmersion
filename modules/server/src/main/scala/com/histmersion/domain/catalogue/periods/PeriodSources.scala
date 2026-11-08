package com.histmersion
package domain.catalogue.periods

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Where the live PeriodO dump is and which of its collections are imported when none is named. */
final case class PeriodSources(periodoUrl: PeriodSources.PeriodoUrl, collections: List[PeriodSources.Collections])

object PeriodSources:
  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends RefinedType[String, Pure]
  type Collections = Collections.T
  object Collections extends RefinedType[String, Pure]
