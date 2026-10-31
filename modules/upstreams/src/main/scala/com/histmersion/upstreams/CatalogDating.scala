package com.histmersion.upstreams

/** How a catalogue's dating becomes a four-point span: an uncertainty widens the OUTER bounds and never invents a
  * certain core. Years are historical (no year 0: −1 is 1 BCE, 1 is 1 CE); an ISO/XSD date's year is astronomical (0 is
  * 1 BCE). The rules the first-party catalogues date their records by and the host dates a plugin's stated year and
  * holds every dating inside its limits by — one copy, so a record dated at the import dates to itself again at
  * `events rederive`. Pure.
  */
object CatalogDating:

  /** The years a start or an end may be, `lo ≤ hi`. */
  final case class Bound(lo: Long, hi: Long)

  /** A four-point span: the start lies in `[notBefore, beginBy]`, the end in `[endFrom, notAfter]`. */
  final case class Span(
      notBefore: Option[Long] = None,
      beginBy: Option[Long] = None,
      endFrom: Option[Long] = None,
      notAfter: Option[Long] = None
  )

  /** Pure: a year with a ± uncertainty in years (negative or absent = none). */
  def year(y: Long, plusMinus: Option[Long] = None): Bound =
    val u = plusMinus.filter(_ > 0).getOrElse(0L)
    Bound(shift(y, -u), shift(y, u))

  /** Pure: the span of an event that starts within `start` and ends within `end` (either may be unknown: then the one
    * known stands for both — a one-day battle, an eruption whose end is not recorded). The certain core (beginBy,
    * endFrom) is kept only when the latest start is not after the earliest end; otherwise it is left unknown rather
    * than inverted.
    */
  def span(start: Option[Bound], end: Option[Bound]): Span =
    (start.orElse(end), end.orElse(start)) match
      case (Some(s), Some(e)) =>
        val core = Option.when(s.hi <= e.lo)((s.hi, e.lo))
        Span(Some(s.lo.min(e.lo)), core.map(_._1), core.map(_._2), Some(e.hi.max(s.hi)))
      case _ => Span()

  /** The years a catalogue's dating may cover: none after `present` (an uncertainty is a window around a central
    * estimate, never a statement that the event may lie in the future) and, where the catalogue cannot precede it, none
    * before `floor` (the GVP's list is of HOLOCENE eruptions: `events.gvp.floor-year`, −9700).
    */
  final case class Limits(present: Long, floor: Option[Long] = None)

  /** Pure: each of the four points held inside `l` — monotone, so the order of the points is kept. */
  def clamp(ts: Span, l: Limits): Span =
    def c(y: Long): Long = l.floor.fold(y)(y.max).min(l.present)
    Span(ts.notBefore.map(c), ts.beginBy.map(c), ts.endFrom.map(c), ts.notAfter.map(c))

  /** Pure: a record stored before the central year and the ± were kept (V088), read back from its span: a start window
    * with no certain core is `central ± half its width` (the GVP's year ± its stated uncertainty, which is how the old
    * importer built it); a cored one is centred on its start window. `None` when the record is undated. Astronomical
    * arithmetic: no year 0.
    */
  def centralOf(ts: Span): Option[(Long, Long)] =
    val start = (ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter) match
      case (Some(a), None, None, Some(b)) => Some((a, b))
      case (Some(a), Some(b), _, _)       => Some((a, b))
      case (None, Some(b), _, _)          => Some((b, b))
      case _                              => None
    start.map { (a, b) =>
      val (x, y) = (toAstronomical(a.min(b)), toAstronomical(a.max(b)))
      (fromAstronomical(Math.floorDiv(x + y, 2L)), (y - x) / 2)
    }

  /** Pure: the years an ISO/XSD date at a Wikidata PRECISION covers: 9 a year, 8 a decade, 7 a century, 6 a millennium,
    * 5 … 0 ten thousand to a billion years; 10 and finer (a month, a day) the year. `isoYear` is astronomical (`-0051`
    * is 52 BCE). A decade/century/millennium is the one the year falls in (Wikidata stores "5th century BCE" as some
    * year inside it), widened by one unit each way, because editors do not agree on which year inside the unit they
    * store.
    */
  def wikidata(isoYear: Long, precision: Int): Bound =
    if precision >= 9 then { val h = fromAstronomical(isoYear); Bound(h, h) }
    else
      val unit = math.pow(10, (9 - precision.max(0)).toDouble).toLong
      val lo = Math.floorDiv(isoYear, unit) * unit
      Bound(fromAstronomical(lo - unit), fromAstronomical(lo + 2 * unit - 1))

  /** Pure: the astronomical year of an XSD dateTime (`-0051-07-01T00:00:00Z` → -51). */
  def isoYear(xsd: String): Option[Long] =
    val s = xsd.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then -v else v)

  /** Pure: a historical year as an astronomical one (−1, 1 BCE, is 0). */
  def toAstronomical(h: Long): Long = if h < 0 then h + 1 else if h == 0 then 0 else h

  /** Pure: an astronomical year as a historical one (0 is −1, 1 BCE). */
  def fromAstronomical(a: Long): Long = if a <= 0 then a - 1 else a

  /** Pure: a historical year `d` years later (earlier when negative), across the missing year 0. */
  def shift(h: Long, d: Long): Long = fromAstronomical(toAstronomical(h) + d)
