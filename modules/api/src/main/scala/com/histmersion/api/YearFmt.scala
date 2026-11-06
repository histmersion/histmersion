package com.histmersion.api

object YearFmt:

  type OpenPeriodStart = _root_.com.histmersion.api.catalogue.Year
  val OpenPeriodStart: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type OpenPeriodEnd = _root_.com.histmersion.api.catalogue.Year
  val OpenPeriodEnd: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year

  def apply(v: com.histmersion.api.catalogue.Year): String =
    if (v).value < 0 then (s"${-(v).value} BCE")
    else if v == 0 then ("1 BCE") // no year zero in historical reckoning
    else (s"$v CE")

  def apply(v: Option[com.histmersion.api.catalogue.Year]): String = v.fold("?")(apply)

  def period(start: Option[com.histmersion.api.catalogue.Year], end: Option[com.histmersion.api.catalogue.Year]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ("")

  def openPeriod(start: Option[com.histmersion.api.YearFmt.OpenPeriodStart], end: Option[com.histmersion.api.YearFmt.OpenPeriodEnd], none: String = ("undated")): String =
    (start, end) match
      case (Some(_), Some(_)) => ((period(start, end)).value)
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => ((none).value)

  def span(ts: TimeSpan): String =
    ts.outer.fold("undated")((a, b) => period(Some(a), Some(b)))

  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then ("undated") else (parts.mkString(", "))
