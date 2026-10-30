package com.histmersion.api

final case class PeriodOAuthority(id: String, title: String, year: Option[Int], periods: List[PeriodODefinition]):
  /** A PeriodO id resolves as `http://n2t.net/ark:/99152/<id>`. */
  def cite(periodId: String): String =
    s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}"
