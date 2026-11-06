package com.histmersion.upstreams.dating
object CatalogDating:
  type AstronomicalYear = AstronomicalYear.T
  object AstronomicalYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HistoricalYear = HistoricalYear.T
  object HistoricalYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsoYear = IsoYear.T
  object IsoYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type XsdYearText = XsdYearText.T
  object XsdYearText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearMargin = YearMargin.T
  object YearMargin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearShift = YearShift.T
  object YearShift extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The years a start or an end may be, `lo ≤ hi`. */

  type WikidataPrecision = WikidataPrecision.T
  object WikidataPrecision extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShiftH = _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear
  val ShiftH: _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear.type = _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear

  final case class Bound(lo: Bound.Lo, hi: Bound.Hi)

  object Bound:
    type Lo = Lo.T
    object Lo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hi = Hi.T
    object Hi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A four-point span: the start lies in `[notBefore, beginBy]`, the end in `[endFrom, notAfter]`. */
  final case class Span(
      notBefore: Option[Span.NotBefore] = None,
      beginBy: Option[Span.BeginBy] = None,
      endFrom: Option[Span.EndFrom] = None,
      notAfter: Option[Span.NotAfter] = None
  )

  object Span:
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BeginBy = BeginBy.T
    object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndFrom = EndFrom.T
    object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotAfter = NotAfter.T
    object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: a year with a ± uncertainty in years (negative or absent = none). */
  def year(y: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear, plusMinus: Option[com.histmersion.upstreams.dating.CatalogDating.YearMargin] = None): Bound =
    val u = plusMinus.filter((_).value > 0).getOrElse(0L)
    Bound(shift(_root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((y).value), -u), shift(_root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((y).value), u))

  /** Pure: the span of an event that starts within `start` and ends within `end` (either may be unknown: then the one
    * known stands for both — a one-day battle, an eruption whose end is not recorded). The certain core (beginBy,
    * endFrom) is kept only when the latest start is not after the earliest end; otherwise it is left unknown rather
    * than inverted.
    */
  def span(start: Option[Bound], end: Option[Bound]): Span =
    (start.orElse(end), end.orElse(start)) match
      case (Some(s), Some(e)) =>
        val core = Option.when((s.hi).value <= e.lo)((s.hi, e.lo))
        Span(Some((s.lo).value.min(e.lo)), core.map(_._1), core.map(_._2), Some((e.hi).value.max(s.hi)))
      case _ => Span()

  /** The years a catalogue's dating may cover: none after `present` (an uncertainty is a window around a central
    * estimate, never a statement that the event may lie in the future) and, where the catalogue cannot precede it, none
    * before `floor` (the GVP's list is of HOLOCENE eruptions: `events.gvp.floor-year`, −9700).
    */
  final case class Limits(present: Limits.Present, floor: Option[Limits.Floor] = None)

  object Limits:
    type Present = Present.T
    object Present extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Floor = Floor.T
    object Floor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: each of the four points held inside `l` — monotone, so the order of the points is kept. */
  def clamp(ts: Span, l: Limits): Span =
    def c(y: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = l.floor.fold(y)((y).value.max).min(l.present)
    Span(ts.notBefore.map(c), ts.beginBy.map(c), ts.endFrom.map(c), ts.notAfter.map(c))

  /** Pure: a record stored before the central year and the ± were kept (V088), read back from its span: a start window
    * with no certain core is `central ± half its width` (the GVP's year ± its stated uncertainty, which is how the old
    * importer built it); a cored one is centred on its start window. `None` when the record is undated. Astronomical
    * arithmetic: no year 0.
    */
  def centralOf(ts: Span): Option[(com.histmersion.upstreams.dating.CatalogDating.Bound.Lo, com.histmersion.upstreams.dating.CatalogDating.Bound.Hi)] =
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
  def wikidata(isoYear: com.histmersion.upstreams.dating.CatalogDating.IsoYear, precision: com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision): Bound =
    if (precision).value >= 9 then { val h = fromAstronomical(_root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((isoYear).value)); Bound(h, h) }
    else
      val unit = math.pow(10, (9 - (precision).value.max(0)).toDouble).toLong
      val lo = Math.floorDiv((isoYear).value, unit) * unit
      Bound(fromAstronomical(_root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(lo - unit)), fromAstronomical(_root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(lo + 2 * unit - 1)))

  /** Pure: the astronomical year of an XSD dateTime (`-0051-07-01T00:00:00Z` → -51). */
  def isoYear(xsd: com.histmersion.upstreams.dating.CatalogDating.XsdYearText): Option[com.histmersion.upstreams.dating.CatalogDating.IsoYear] =
    val s = (xsd).value.trim
    val neg = s.startsWith("-")
    val digits = (if neg || s.startsWith("+") then s.drop(1) else s).takeWhile(_.isDigit)
    digits.toLongOption.map(v => if neg then _root_.com.histmersion.upstreams.dating.CatalogDating.IsoYear(-v) else _root_.com.histmersion.upstreams.dating.CatalogDating.IsoYear(v))

  /** Pure: a historical year as an astronomical one (−1, 1 BCE, is 0). */
  def toAstronomical(h: com.histmersion.upstreams.dating.CatalogDating.HistoricalYear): com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear = if (h).value < 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((h).value + 1) else if h == 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((0).toLong) else _root_.com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear((h).value)

  /** Pure: an astronomical year as a historical one (0 is −1, 1 BCE). */
  def fromAstronomical(a: com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = if (a).value <= 0 then _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((a).value - 1) else _root_.com.histmersion.upstreams.dating.CatalogDating.HistoricalYear((a).value)

  /** Pure: a historical year `d` years later (earlier when negative), across the missing year 0. */
  def shift(h: com.histmersion.upstreams.dating.CatalogDating.ShiftH, d: com.histmersion.upstreams.dating.CatalogDating.YearShift): com.histmersion.upstreams.dating.CatalogDating.HistoricalYear = fromAstronomical((toAstronomical(h)).value + d)
