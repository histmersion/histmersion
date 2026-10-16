package com.histmersion.core

/** How PRECISELY an event is dated, decided once, here, for every view (docs/visualization-guide.md
  * §1b). An event's UNCERTAINTY is its stated ± (a catalogue's: GVP's `StartDateYearUncertainty`)
  * or, where it states none or a smaller one, half the width of its uncertain windows — the start
  * window (`notBefore…beginBy`) or the end window (`endFrom…notAfter`), whichever is wider; an event
  * with no certain core is one window, `notBefore…notAfter`. Then:
  *
  *  - **precise** — ± ≤ 50 years: drawn on the map at its years, like any dated thing;
  *  - **approximate** — 50 < ± ≤ 500: drawn, faded (an uncertain date must not look crisp);
  *  - **imprecise** — ± > 500 (a tephra layer dated ± 2500 years, a Wikidata "millennium"): NOT drawn
  *    on the map at a year — it would stand in every view of three thousand years — but listed (the
  *    events list, the card), marked "imprecise date";
  *  - **undated** — no year at all (no time view shows it; the map's feed leaves it out).
  *
  * The database computes the same uncertainty (V088's generated `events.uncertainty`, checked against
  * [[uncertainty]] by `EventCorrectionsRepoSuite`), so the map's feed (`/api/events/geo`) leaves the
  * imprecise ones out in SQL with [[ApproximateUpTo]] and every feed carries the class. */
object EventPrecision:
  val Precise     = "precise"
  val Approximate = "approximate"
  val Imprecise   = "imprecise"
  val Undated     = "undated"
  val all: List[String] = List(Precise, Approximate, Imprecise, Undated)

  /** The largest ± that is still precise, and the largest still drawn (the rule above). */
  val PreciseUpTo: Long     = 50
  val ApproximateUpTo: Long = 500

  /** Pure: the event's uncertainty in years (`None`: undated). Astronomical arithmetic: no year 0. */
  def uncertainty(ts: TimeSpan, plusMinus: Option[Long]): Option[Long] =
    EventGeo.outer(ts).map { (a, b) =>
      def a2(y: Long) = Years.toAstronomical(y)
      def width(x: Option[Long], y: Option[Long]): Long = (for p <- x; q <- y yield a2(q) - a2(p)).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then (a2(b) - a2(a)).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      derived.max(plusMinus.getOrElse(0L).max(0))
    }

  /** Pure: the class of an uncertainty. */
  def of(uncertainty: Option[Long]): String =
    uncertainty match
      case None                         => Undated
      case Some(u) if u <= PreciseUpTo  => Precise
      case Some(u) if u <= ApproximateUpTo => Approximate
      case _                            => Imprecise

  def of(ts: TimeSpan, plusMinus: Option[Long]): String = of(uncertainty(ts, plusMinus))

  /** Pure: is an event of this class drawn on the map at a year (the markers, the playback)? Dated,
    * and not imprecisely: the time views show dated things only. */
  def mapped(precision: String): Boolean = precision != Imprecise && precision != Undated

  /** Pure: the words a list row or a card marks a class with (`None`: nothing to say). */
  def label(precision: String): Option[String] =
    precision match
      case Imprecise   => Some("imprecise date")
      case Approximate => Some("approximate date")
      case _           => None

  /** Pure: the stated dating in words — `250 CE ± 2500 (tephrochronology)`; a method alone is
    * "dated by …"; nothing stated, `None`. */
  def note(central: Option[Long], plusMinus: Option[Long], method: Option[String]): Option[String] =
    val m = method.map(_.trim).filter(_.nonEmpty).map(lowerFirst)
    central match
      case Some(c) => Some(YearFmt(c) + plusMinus.filter(_ > 0).fold("")(u => s" ± $u") + m.fold("")(x => s" ($x)"))
      case None    => m.map(x => s"dated by $x")

  /** `Historical Observations` → `historical observations`: each Title-case word in lower case;
    * `K/Ar`, `14C` stay as written. */
  private def lowerFirst(s: String): String =
    s.split(" ", -1).map(w =>
      if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}" else w
    ).mkString(" ")
