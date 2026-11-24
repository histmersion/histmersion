package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@movesTo(FeaturePlace.UtilCommon)
object YearFmt:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(v: Year): String =
    if (v).value < 0 then (s"${-(v).value} BCE")
    else if (v).value == 0 then ("1 BCE")
    else (s"$v CE")

  def apply(v: Option[Year]): String = v.fold("?")(apply)

  def period(start: Option[Year], end: Option[Year]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ("")

  def openPeriod(
      start: Option[Year],
      end: Option[Year],
      none: String = ("undated")
  ): String =
    (start, end) match
      case (Some(_), Some(_)) => ((period(start, end)))
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => ((none))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def span(ts: TimeSpan): String =
    ts.outer.fold("undated")((a, b) => period(Some(a), Some(b)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then ("undated") else (parts.mkString(", "))
