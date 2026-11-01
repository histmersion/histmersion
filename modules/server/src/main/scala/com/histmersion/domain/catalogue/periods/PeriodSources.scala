package com.histmersion.domain.catalogue.periods

/** Where the live PeriodO dump is and which of its collections are imported when none is named. */
final case class PeriodSources(periodoUrl: String, collections: List[String])
