package com.histmersion.api

/** One PeriodO authority — a publication or dataset — and the definitions it makes. */
final case class PeriodOAuthority(id: String, title: String, year: Option[Int], periods: List[PeriodODefinition]):
  /** What every period row says it comes from: the PeriodO id (resolvable as `http://n2t.net/ark:/99152/<id>`) and the
    * authority that states the range.
    */
  def cite(periodId: String): String =
    s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}"
