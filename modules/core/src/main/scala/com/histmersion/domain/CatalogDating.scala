package com.histmersion.domain

import com.histmersion.api.{CatalogRecord, TimeSpan, Years}

/** How a catalogue's dating becomes the four-point span ([[TimeSpan]]): an uncertainty widens the OUTER bounds and
  * never invents a certain core. Pure.
  */
object CatalogDating:

  /** The years a start or an end may be, `lo ≤ hi`. */
  final case class Bound(lo: Long, hi: Long)

  /** Pure: a year with a ± uncertainty in years (negative or absent = none). */
  def year(y: Long, plusMinus: Option[Long] = None): Bound =
    val u = plusMinus.filter(_ > 0).getOrElse(0L)
    Bound(Years.shift(y, -u), Years.shift(y, u))

  /** Pure: the span of an event that starts within `start` and ends within `end` (either may be unknown: then the one
    * known stands for both — a one-day battle, an eruption whose end is not recorded). The certain core (beginBy,
    * endFrom) is kept only when the latest start is not after the earliest end; otherwise it is left unknown rather
    * than inverted.
    */
  def span(start: Option[Bound], end: Option[Bound]): TimeSpan =
    (start.orElse(end), end.orElse(start)) match
      case (Some(s), Some(e)) =>
        val core = Option.when(s.hi <= e.lo)((s.hi, e.lo))
        TimeSpan(Some(s.lo.min(e.lo)), core.map(_._1), core.map(_._2), Some(e.hi.max(s.hi)))
      case _ => TimeSpan()

  /** The years a catalogue's dating may cover: none after `present` (an uncertainty is a window around a central
    * estimate, never a statement that the event may lie in the future) and, where the catalogue cannot precede it, none
    * before `floor` (the GVP's list is of HOLOCENE eruptions: `events.gvp.floor-year`, −9700).
    */
  final case class Limits(present: Long, floor: Option[Long] = None)

  /** Pure: each of the four points held inside `l` — monotone, so the order of the points is kept. */
  def clamp(ts: TimeSpan, l: Limits): TimeSpan =
    def c(y: Long): Long = l.floor.fold(y)(y.max).min(l.present)
    TimeSpan(ts.notBefore.map(c), ts.beginBy.map(c), ts.endFrom.map(c), ts.notAfter.map(c))

  /** Pure: a record's span held inside `l`; its central year and ± are kept as the catalogue states them (the card says
    * "250 CE ± 2500", whatever the clamp did to the window).
    */
  def clamp(r: CatalogRecord, l: Limits): CatalogRecord = r.copy(timeSpan = clamp(r.timeSpan, l))

  /** Pure: a record stored before the central year and the ± were kept (V088), read back from its span: a start window
    * with no certain core is `central ± half its width` (the GVP's year ± its stated uncertainty, which is how the old
    * importer built it); a cored one is centred on its start window. `None` when the record is undated. Astronomical
    * arithmetic: no year 0.
    */
  def centralOf(ts: TimeSpan): Option[(Long, Long)] =
    val start = (ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter) match
      case (Some(a), None, None, Some(b)) => Some((a, b))
      case (Some(a), Some(b), _, _)       => Some((a, b))
      case (None, Some(b), _, _)          => Some((b, b))
      case _                              => None
    start.map { (a, b) =>
      val (x, y) = (Years.toAstronomical(a.min(b)), Years.toAstronomical(a.max(b)))
      (Years.fromAstronomical(Math.floorDiv(x + y, 2L)), (y - x) / 2)
    }

  /** Pure: the years an ISO/XSD date at a Wikidata PRECISION covers: 9 a year, 8 a decade, 7 a century, 6 a millennium,
    * 5 … 0 ten thousand to a billion years; 10 and finer (a month, a day) the year. `isoYear` is astronomical (`-0051`
    * is 52 BCE). A decade/century/millennium is the one the year falls in (Wikidata stores "5th century BCE" as some
    * year inside it), widened by one unit each way, because editors do not agree on which year inside the unit they
    * store.
    */
  def wikidata(isoYear: Long, precision: Int): Bound =
    if precision >= 9 then { val h = Years.fromAstronomical(isoYear); Bound(h, h) }
    else
      val unit = math.pow(10, (9 - precision.max(0)).toDouble).toLong
      val lo = Math.floorDiv(isoYear, unit) * unit
      Bound(Years.fromAstronomical(lo - unit), Years.fromAstronomical(lo + 2 * unit - 1))

  /** Pure: the astronomical year of an XSD dateTime (`-0051-07-01T00:00:00Z` → -51). */
  def isoYear(xsd: String): Option[Long] = Years.isoAstronomical(xsd)
