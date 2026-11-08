package com.histmersion
package api

import com.histmersion.api.catalogue.Year

object YearFmt:

  export com.histmersion.api.catalogue.{Year as OpenPeriodEnd, Year as OpenPeriodStart}

  def apply(v: Year): String =
    if (v).value < 0 then (s"${-(v).value} BCE")
    else if (v).value == 0 then ("1 BCE") // no year zero in historical reckoning
    else (s"$v CE")

  def apply(v: Option[Year]): String = v.fold("?")(apply)

  def period(start: Option[Year], end: Option[Year]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ("")

  def openPeriod(
      start: Option[YearFmt.OpenPeriodStart],
      end: Option[YearFmt.OpenPeriodEnd],
      none: String = ("undated")
  ): String =
    (start, end) match
      case (Some(_), Some(_)) => ((period(start, end)))
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => ((none))

  def span(ts: TimeSpan): String =
    ts.outer.fold("undated")((a, b) => period(Some(Year(a.value)), Some(Year(b.value))))

  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then ("undated") else (parts.mkString(", "))
