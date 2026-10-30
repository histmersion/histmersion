package com.histmersion.api

/** Unified year display — the ONE formatter every surface uses (server Markdown, HTML, web, exports). Historical
  * reckoning: a negative year is BCE; 0 is shown as 1 BCE (see [[Years]]).
  */
object YearFmt:

  /** A single year: `1300 BCE`, `530 CE`, `1 BCE` (for year 0). */
  def apply(v: Long): String =
    if v < 0 then s"${-v} BCE"
    else if v == 0 then "1 BCE" // no year zero in historical reckoning
    else s"$v CE"

  /** An optional year: `?` when it is not known. */
  def apply(v: Option[Long]): String = v.fold("?")(apply)

  /** A period range: `1300 BCE–1100 BCE`, or a point year, or empty when either bound is missing. */
  def period(start: Option[Long], end: Option[Long]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ""

  /** A period whose bounds may each be missing, as a reader sees it: `27 BCE–410 CE`, `from 27 BCE`, `until 410 CE`, or
    * `none` (default `"undated"`) when neither is known.
    */
  def openPeriod(start: Option[Long], end: Option[Long], none: String = "undated"): String =
    (start, end) match
      case (Some(_), Some(_)) => period(start, end)
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => none

  /** An event's dates on ONE short line — a list row, a card's byline, a map label: the outer span ([[EventGeo.outer]])
    * as `1630 BCE–1590 BCE`, one year, or `undated`.
    */
  def span(ts: TimeSpan): String =
    EventGeo.outer(ts).fold("undated")((a, b) => period(Some(a), Some(b)))

  /** An event's dates in full, every point it states: `not before 1650 BCE, by 1630 BCE, until 1600 BCE, not after 1590
    * BCE`, or `undated` — the detail views of the MCP tools and the CLI.
    */
  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then "undated" else parts.mkString(", ")
