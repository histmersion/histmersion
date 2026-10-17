package com.histmersion.core

import io.circe.Json

// ── the year module (#1798 item 4) ──────────────────────────────────────────────────────────────
//
// ONE place for signed years: how they convert, how they are read from text and how they are shown.
// Every surface — the Markdown the MCP tools and the CLI print, the server's HTML pages, the web UI,
// the exports — goes through [[YearFmt]], so a year reads the same everywhere.
//
// The convention (V015): signed years in HISTORICAL reckoning, BCE negative, NO year 0 — 1 BCE is
// −1 and 1 CE is 1. A stored 0 is an artefact of ISO 8601 interop and reads as 1 BCE (as the
// astronomical year 0 is); it is never shown as "0 CE" and never accepted from a person.

/** Signed years in HISTORICAL reckoning (no year 0: −1 is 1 BCE, 1 is 1 CE), as the corpus stores
  * them, and the ASTRONOMICAL years of ISO/XSD dates (0 is 1 BCE, −1 is 2 BCE) that Wikidata, LPF and
  * PeriodO answer with. Pure. */
object Years:

  /** An astronomical year as a historical one. */
  def fromAstronomical(a: Long): Long = if a <= 0 then a - 1 else a

  /** A historical year as an astronomical one (0 is read as 1 BCE, as [[YearFmt]] shows it). */
  def toAstronomical(h: Long): Long = if h < 0 then h + 1 else if h == 0 then 0 else h

  /** `h` moved by `d` years, across the BCE/CE boundary without a year 0. */
  def shift(h: Long, d: Long): Long = fromAstronomical(toAstronomical(h) + d)

  /** Why year 0 is refused wherever a person or a model states a year. */
  val NoYearZero: String = "there is no year 0 (1 BCE is -1, 1 CE is 1)"

  private val Circa  = "^(?:c|ca|circa)\\.?\\s+".r
  private val EraBce = "(?:bce|bc|b\\.c\\.e\\.|b\\.c\\.)"
  private val EraCe  = "(?:ce|ad|c\\.e\\.|a\\.d\\.)"
  private val Typed  = s"^([+-]?\\d+)\\s*($EraBce|$EraCe)?$$".r
  private val EraFirst = s"^($EraBce|$EraCe)\\s*(\\d+)$$".r

  /** A year a PERSON typed, as a signed year: `1300 BCE`, `1300 BC`, `1300bce`, `c. 1200 BCE`,
    * `-1300`, `530`, `530 CE`, `AD 530`, `1,200 BCE`. An era word makes the number positive-is-BCE;
    * a minus sign with BCE is still BCE (`-1300 BCE` = 1300 BCE). Refused: anything else, a negative
    * CE year and year 0 ([[NoYearZero]]). The lenient READERS of data files ([[Lpf.parseYear]]) and
    * the strict reader of model answers (`AgentVerify`) keep their own acceptance rules and convert
    * through this object. */
  def parse(raw: String): Either[String, Long] =
    val t = Circa.replaceFirstIn(Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT), "")
      .replace(",", "").trim
    def isBce(era: String) = era.matches(EraBce)
    def signed(num: String, era: Option[String]): Either[String, Long] =
      num.toLongOption.toRight(s"'${raw.trim}' is not a year").flatMap { n =>
        era match
          case None                => Right(n)
          case Some(e) if isBce(e) => Right(-math.abs(n))
          case Some(_) if n < 0    => Left(s"'${raw.trim}': a CE year is positive; write the BCE year instead")
          case Some(_)             => Right(n)
      }
    val parsed = t match
      case Typed(num, era)    => signed(num, Option(era))
      case EraFirst(era, num) => signed(num, Some(era))
      case _                  => Left(s"'${raw.trim}' is not a year (e.g. 1300 BCE, 530 CE, -1300)")
    parsed.flatMap(y => if y == 0 then Left(s"'${raw.trim}': $NoYearZero") else Right(y))

  /** The ASTRONOMICAL year of an ISO 8601 / XSD date or year (`-0051-07-01T00:00:00Z` → −51,
    * `"0640"` → 640, `"+2000"` → 2000); `None` when it does not start with one. */
  def isoAstronomical(xsd: String): Option[Long] =
    val s      = xsd.trim
    val neg    = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then -v else v)

  /** An ISO 8601 year (astronomical) as a HISTORICAL signed year: `"0000"` → −1 (1 BCE). Only a
    * whole value that is a number — `"about 3000 BC"` is not one. */
  def parseIso(raw: String): Option[Long] =
    raw.trim.toLongOption.map(fromAstronomical)

  /** A year from a JSON scalar of a data file: a number (`-1400`, `-1400.0`) or a string the given
    * text reader understands. THE one `yearOf` of the gazetteer readers. */
  def fromJson(j: Json, text: String => Option[Long]): Option[Long] =
    j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(text)).orElse(j.asNumber.map(_.toDouble.round))

/** Unified year display — the ONE formatter every surface uses (server Markdown, HTML, web, exports).
  * Historical reckoning: a negative year is BCE; 0 is shown as 1 BCE (see [[Years]]). */
object YearFmt:

  /** A single year: `1300 BCE`, `530 CE`, `1 BCE` (for year 0). */
  def apply(v: Long): String =
    if v < 0 then s"${-v} BCE"
    else if v == 0 then "1 BCE"   // no year zero in historical reckoning
    else s"$v CE"

  /** An optional year: `?` when it is not known. */
  def apply(v: Option[Long]): String = v.fold("?")(apply)

  /** A period range: `1300 BCE–1100 BCE`, or a point year, or empty when either bound is missing. */
  def period(start: Option[Long], end: Option[Long]): String =
    (start, end) match
      case (Some(a), Some(b)) => if a == b then apply(a) else s"${apply(a)}–${apply(b)}"
      case _                  => ""

  /** A period whose bounds may each be missing, as a reader sees it: `27 BCE–410 CE`, `from 27 BCE`,
    * `until 410 CE`, or `none` (default `"undated"`) when neither is known. */
  def openPeriod(start: Option[Long], end: Option[Long], none: String = "undated"): String =
    (start, end) match
      case (Some(_), Some(_)) => period(start, end)
      case (Some(a), None)    => s"from ${apply(a)}"
      case (None, Some(b))    => s"until ${apply(b)}"
      case (None, None)       => none

  /** An event's dates on ONE short line — a list row, a card's byline, a map label: the outer span
    * ([[EventGeo.outer]]) as `1630 BCE–1590 BCE`, one year, or `undated`. */
  def span(ts: TimeSpan): String =
    EventGeo.outer(ts).fold("undated")((a, b) => period(Some(a), Some(b)))

  /** An event's dates in full, every point it states: `not before 1650 BCE, by 1630 BCE, until 1600 BCE,
    * not after 1590 BCE`, or `undated` — the detail views of the MCP tools and the CLI. */
  def spanLong(ts: TimeSpan): String =
    val parts = List(
      ts.notBefore.map(y => s"not before ${apply(y)}"),
      ts.beginBy.map(y => s"by ${apply(y)}"),
      ts.endFrom.map(y => s"until ${apply(y)}"),
      ts.notAfter.map(y => s"not after ${apply(y)}")
    ).flatten
    if parts.isEmpty then "undated" else parts.mkString(", ")
