package com.histmersion.api

object YearFmt:

  def apply(v: Long): String =
    if v < 0 then s"${-v} BCE"
    else if v == 0 then "1 BCE" // no year zero in historical reckoning
    else s"$v CE"

  def apply(v: Option[Long]): String = v.fold("?")(apply)

  def period(start: Option[Long], end: Option[Long]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ""

  def openPeriod(start: Option[Long], end: Option[Long], none: String = "undated"): String =
    (start, end) match
      case (Some(_), Some(_)) => period(start, end)
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => none

  def span(ts: TimeSpan): String =
    ts.outer.fold("undated")((a, b) => period(Some(a), Some(b)))

  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then "undated" else parts.mkString(", ")
