package com.histmersion.upstreams.dating
object CatalogDating:
  type AstronomicalYear = AstronomicalYear.T
  object AstronomicalYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    extension (a: T)
      def +(d: YearShift): T = apply(a.value + d.value)
      def midpoint(o: T): T = apply(Math.floorDiv(a.value + o.value, 2L))
      def halfWidth(o: T): YearMargin = YearMargin((o.value - a.value) / 2)
  type HistoricalYear = HistoricalYear.T
  object HistoricalYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    extension (y: T)
      def <=(o: T): Boolean = y.value <= o.value
      def min(o: T): T = apply(y.value.min(o.value))
      def max(o: T): T = apply(y.value.max(o.value))
  type XsdYearText = XsdYearText.T
  object XsdYearText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearMargin = YearMargin.T
  object YearMargin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    extension (m: T) def forward: YearShift = YearShift(m.value)
  type YearShift = YearShift.T
  object YearShift extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    extension (d: T) def unary_- : T = apply(-d.value)

  /** The years a start or an end may be, `lo ≤ hi`. */

  type WikidataPrecision = WikidataPrecision.T
  object WikidataPrecision extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Bound(lo: HistoricalYear, hi: HistoricalYear)

  /** A four-point span: the start lies in `[notBefore, beginBy]`, the end in `[endFrom, notAfter]`. */
  final case class Span(
      notBefore: Option[HistoricalYear] = None,
      beginBy: Option[HistoricalYear] = None,
      endFrom: Option[HistoricalYear] = None,
      notAfter: Option[HistoricalYear] = None
  )

  /** Pure: a year with a ± uncertainty in years (negative or absent = none). */
  def year(y: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear, plusMinus: Option[com.histmersion.upstreams.dating.CatalogDating.YearMargin] = None): Bound =
    val u = plusMinus.filter((_).value > 0).fold(YearShift(0L))(_.forward)
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
  final case class Limits(present: HistoricalYear, floor: Option[HistoricalYear] = None)

  /** Pure: each of the four points held inside `l` — monotone, so the order of the points is kept. */
  def clamp(ts: Span, l: Limits): Span =
    def c(y: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = l.floor.fold(y)(y.max(_)).min(l.present)
    Span(ts.notBefore.map(c), ts.beginBy.map(c), ts.endFrom.map(c), ts.notAfter.map(c))

  /** Pure: a record stored before the central year and the ± were kept (V088), read back from its span: a start window
    * with no certain core is `central ± half its width` (the GVP's year ± its stated uncertainty, which is how the old
    * importer built it); a cored one is centred on its start window. `None` when the record is undated. Astronomical
    * arithmetic: no year 0.
    */
  def centralOf(ts: Span): Option[(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear, com.histmersion.upstreams.dating.CatalogDating.YearMargin)] =
    val start = (ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter) match
      case (Some(a), None, None, Some(b)) => Some((a, b))
      case (Some(a), Some(b), _, _)       => Some((a, b))
      case (None, Some(b), _, _)          => Some((b, b))
      case _                              => None
    start.map { (a, b) =>
      val (x, y) = (toAstronomical(a.min(b)), toAstronomical(a.max(b)))
      (fromAstronomical(x.midpoint(y)), x.halfWidth(y))
    }

  /** Pure: the years an ISO/XSD date at a Wikidata PRECISION covers: 9 a year, 8 a decade, 7 a century, 6 a millennium,
    * 5 … 0 ten thousand to a billion years; 10 and finer (a month, a day) the year. `isoYear` is astronomical (`-0051`
    * is 52 BCE). A decade/century/millennium is the one the year falls in (Wikidata stores "5th century BCE" as some
    * year inside it), widened by one unit each way, because editors do not agree on which year inside the unit they
    * store.
    */
  def wikidata(isoYear: com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear, precision: com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision): Bound =
    if (precision).value >= 9 then { val h = fromAstronomical(isoYear); Bound(h, h) }
    else
      val unit = math.pow(10, (9 - (precision).value.max(0)).toDouble).toLong
      val lo = Math.floorDiv((isoYear).value, unit) * unit
      Bound(fromAstronomical(_root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(lo - unit)), fromAstronomical(_root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(lo + 2 * unit - 1)))

  /** Pure: the astronomical year of an XSD dateTime (`-0051-07-01T00:00:00Z` → -51). */
  def isoYear(xsd: com.histmersion.upstreams.dating.CatalogDating.XsdYearText): Option[com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear] =
    val s = (xsd).value.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-v) else _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(v))

  /** Pure: a historical year as an astronomical one (−1, 1 BCE, is 0). */
  def toAstronomical(h: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear): com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear = if (h).value < 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((h).value + 1) else if (h).value == 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((0).toLong) else _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((h).value)

  /** Pure: an astronomical year as a historical one (0 is −1, 1 BCE). */
  def fromAstronomical(a: com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = if (a).value <= 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((a).value - 1) else _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((a).value)

  /** Pure: a historical year `d` years later (earlier when negative), across the missing year 0. */
  def shift(h: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear, d: com.histmersion.upstreams.dating.CatalogDating.YearShift): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = fromAstronomical(toAstronomical(h) + d)
