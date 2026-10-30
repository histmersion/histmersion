package com.histmersion.api

/** The period of ONE aggregation unit as it comes out of SQL (`lower(period)`, `upper(period) - 1`). For `unit=sources`
  * there is exactly one row per source, for `unit=findings` one per finding. Both bounds `None` = **undated** → ignored
  * by every function here (plan §1.7).
  */
final case class PeriodRow(periodStart: Option[Long], periodEnd: Option[Long])
