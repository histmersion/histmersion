package com.histmersion.api

object YearFmt:

  type ApplyResult = ApplyResult.T
  object ApplyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyV = ApplyV.T
  object ApplyV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodResult = PeriodResult.T
  object PeriodResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenPeriodResult = OpenPeriodResult.T
  object OpenPeriodResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenPeriodStart = OpenPeriodStart.T
  object OpenPeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenPeriodEnd = OpenPeriodEnd.T
  object OpenPeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenPeriodNone = OpenPeriodNone.T
  object OpenPeriodNone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanResult = SpanResult.T
  object SpanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanLongResult = SpanLongResult.T
  object SpanLongResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(v: com.histmersion.api.YearFmt.ApplyV): com.histmersion.api.YearFmt.ApplyResult =
    if v < 0 then s"${-v} BCE"
    else if v == 0 then "1 BCE" // no year zero in historical reckoning
    else s"$v CE"

  def apply(v: Option[com.histmersion.api.YearFmt.ApplyV]): com.histmersion.api.YearFmt.ApplyResult = v.fold("?")(apply)

  def period(start: Option[com.histmersion.api.YearFmt.PeriodStart], end: Option[com.histmersion.api.YearFmt.PeriodEnd]): com.histmersion.api.YearFmt.PeriodResult =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ""

  def openPeriod(start: Option[com.histmersion.api.YearFmt.OpenPeriodStart], end: Option[com.histmersion.api.YearFmt.OpenPeriodEnd], none: com.histmersion.api.YearFmt.OpenPeriodNone = "undated"): com.histmersion.api.YearFmt.OpenPeriodResult =
    (start, end) match
      case (Some(_), Some(_)) => period(start, end)
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => none

  def span(ts: TimeSpan): com.histmersion.api.YearFmt.SpanResult =
    ts.outer.fold("undated")((a, b) => period(Some(a), Some(b)))

  def spanLong(ts: TimeSpan): com.histmersion.api.YearFmt.SpanLongResult =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then "undated" else parts.mkString(", ")
