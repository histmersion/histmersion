package com.histmersion.domain

import com.histmersion.api.{CatalogProviderInfo, CatalogRecord, EventGeo, EventTaxonomy, Geocode, TimeSpan, Years}

/** The pure half of events from catalogues: the providers' identities and terms, the mapping of their datings onto the
  * four-point time-span, and how records from several catalogues and the events already stored are merged into ONE
  * event.
  */

object CatalogProvider:
  val Gvp = "gvp"
  val Noaa = "noaa"
  val Wikidata = "wikidata"
  val all: List[String] = List(Gvp, Noaa, Wikidata)

  val infos: List[CatalogProviderInfo] = List(
    // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
    // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
    CatalogProviderInfo(
      Gvp,
      "Smithsonian Global Volcanism Program",
      "Smithsonian Terms of Use (non-commercial use with attribution)",
      "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
        "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2",
      "https://volcano.si.edu"
    ),
    // a U.S. federal work: public domain; NCEI asks to be cited
    CatalogProviderInfo(
      Noaa,
      "NOAA NCEI Natural Hazards",
      "public-domain",
      "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
        "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K",
      "https://www.ngdc.noaa.gov/hazard/"
    ),
    CatalogProviderInfo(
      Wikidata,
      "Wikidata",
      "CC0-1.0",
      "Wikidata (CC0), https://www.wikidata.org",
      "https://www.wikidata.org"
    )
  )

  def info(id: String): Option[CatalogProviderInfo] = infos.find(_.id == id)

  /** Pure: the provider a request names (`smithsonian` and `volcano` for gvp, `ncei` for noaa). */
  def parse(s: String): Either[String, String] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case Gvp | "smithsonian" | "volcano" => Right(Gvp)
      case Noaa | "ncei"                   => Right(Noaa)
      case Wikidata | "wd"                 => Right(Wikidata)
      case other                           => Left(s"unknown event catalogue '$other' (one of: ${all.mkString(", ")})")

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

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the catalogue keys recorded for
  * it, and whether an import made it (`catalogue`) — a compiled event is matched, never overwritten.
  */
final case class StoredEvent(
    id: String,
    kind: String,
    timeSpan: TimeSpan,
    lat: Option[Double],
    lon: Option[Double],
    wikidata: Option[String],
    keys: Set[(String, String)],
    catalogue: Boolean,
    label: String = ""
)

object CatalogMerge:

  /** How close two records of the same kind must be to be one event: their outer spans overlap once each is widened by
    * `years`, and their points are within `km`.
    */
  final case class Tolerance(km: Double, years: Long)

  /** Where one record goes: into a stored event (`existing`), or into the NEW event `id` (several records of one thing
    * share it). `why` says what matched.
    */
  enum Placement:
    case Existing(eventId: String, why: String)
    case New(eventId: String, why: String)
    def eventId: String

  private def outer(ts: TimeSpan): Option[(Long, Long)] = EventGeo.outer(ts)

  /** Pure: do two spans overlap once widened by `years`? An undated one overlaps nothing. */
  def overlaps(a: TimeSpan, b: TimeSpan, years: Long): Boolean =
    (outer(a), outer(b)) match
      case (Some((a0, a1)), Some((b0, b1))) =>
        Years.shift(a0, -years) <= Years.shift(b1, years) && Years.shift(b0, -years) <= Years.shift(a1, years)
      case _ => false

  /** Pure: are two points within `km`? An unplaced one is near nothing. */
  def near(a: (Option[Double], Option[Double]), b: (Option[Double], Option[Double]), km: Double): Boolean =
    (a, b) match
      case ((Some(la), Some(lo)), (Some(lb), Some(ob))) => Geocode.distanceKm(lo, la, ob, lb) <= km
      case _                                            => false

  /** Two kinds are the same thing when their canonical kinds are equal (a siege is no battle here: Wikidata lists both
    * for one fight only when it says so through the item).
    */
  private def sameKind(a: String, b: String): Boolean = EventTaxonomy.canonical(a) == EventTaxonomy.canonical(b)

  /** Pure: how well a candidate's span and point fit a record's — the TIE-BREAK when several qualify, compared in this
    * order: the larger `score` (the overlap of the two outer spans over their union, 0…1: the one whose dating agrees
    * best), then the smaller `years` (between the spans' midpoints), then the smaller `km`, then the smaller id.
    * Undated or unplaced sides rank last on that criterion (∞), never first.
    */
  final case class Fit(score: Double, years: Double, km: Double, id: String)
  object Fit:
    given Ordering[Fit] = Ordering.by((f: Fit) => (-f.score, f.years, f.km, f.id))

  /** Pure: the [[Fit]] of a candidate (`ts`, `lat`/`lon`, `id`) to a record. */
  def fit(r: CatalogRecord, ts: TimeSpan, lat: Option[Double], lon: Option[Double], id: String): Fit =
    val (score, years) = (outer(r.timeSpan), outer(ts)) match
      case (Some((a0, a1)), Some((b0, b1))) =>
        val inter = (a1.min(b1) - a0.max(b0) + 1).max(0).toDouble
        val union = (a1.max(b1) - a0.min(b0) + 1).toDouble
        (inter / union, math.abs((a0 + a1) / 2.0 - (b0 + b1) / 2.0))
      case _ => (0.0, Double.PositiveInfinity)
    val km = (r.lat, r.lon, lat, lon) match
      case (Some(la), Some(lo), Some(lb), Some(ob)) => Geocode.distanceKm(lo, la, ob, lb)
      case _                                        => Double.PositiveInfinity
    Fit(score, years, km, id)

  /** Pure: where each record goes. A record joins, in this order of rules:
    *   1. the stored event that already holds its catalogue key;
    *   2. the stored event that is the same Wikidata item;
    *   3. a stored event of the same kind, overlapping in time and near in space, that no record of the SAME catalogue
    *      made (one catalogue's two records are two events: two eruptions of one volcano);
    *   4. a record placed before it in this run by the same key or item, or — from ANOTHER catalogue — the same `group`
    *      (+ overlapping time) or kind+time+space;
    * else it makes a new event, under [[CatalogRecord.eventId]].
    *
    * DETERMINISTIC: the records are placed in key order (provider, then id), whatever order they came in, and where a
    * rule finds several candidates the best [[Fit]] wins (overlap, then |Δt|, then distance, then id) — never the first
    * in the list. The answer keeps the order of `records`.
    */
  def place(records: List[CatalogRecord], stored: List[StoredEvent], tol: Tolerance): List[(CatalogRecord, Placement)] =
    val byKey = stored.flatMap(e => e.keys.map(_ -> e.id)).groupMap(_._1)(_._2).view.mapValues(_.min).toMap
    val byWd = stored.flatMap(e => e.wikidata.map(_ -> e)).groupMap(_._1)(_._2)
    def best[A](cands: List[A])(f: A => Fit): Option[A] = cands.minByOption(f)
    def ofStored(r: CatalogRecord)(e: StoredEvent): Fit = fit(r, e.timeSpan, e.lat, e.lon, e.id)
    val order = records.zipWithIndex.sortBy((r, i) => (r.provider, r.providerId, i))
    // `var`-free fold: the records placed so far, with where they went
    val placed = order.foldLeft(Vector.empty[(Int, CatalogRecord, Placement)]) { case (acc, (r, i)) =>
      def storedMatch: Option[Placement] =
        byKey
          .get(r.key)
          .map(Placement.Existing(_, s"${r.provider} ${r.providerId} already recorded"))
          .orElse(
            r.wikidata
              .flatMap(q => byWd.get(q))
              .flatMap(es => best(es)(ofStored(r)))
              .map(e => Placement.Existing(e.id, s"same Wikidata item ${r.wikidata.get}"))
          )
          .orElse(
            best(
              stored.filter(e =>
                !e.keys.exists(_._1 == r.provider) && sameKind(e.kind, r.kind) &&
                  overlaps(e.timeSpan, r.timeSpan, tol.years) && near((e.lat, e.lon), (r.lat, r.lon), tol.km)
              )
            )(ofStored(r))
              .map(e =>
                Placement
                  .Existing(e.id, s"same ${EventTaxonomy.canonical(r.kind)} at the same time and place as '${e.id}'")
              )
          )
      def runMatch: Option[Placement] =
        // the rules in order; within one rule, the best fit
        val prior = acc.toList.map((_, o, p) => (o, p))
        def pick(rule: (CatalogRecord, Placement) => Boolean): Option[Placement] =
          best(prior.filter(rule.tupled))((o, p) => fit(r, o.timeSpan, o.lat, o.lon, p.eventId)).map(_._2)
        pick((o, _) => o.key == r.key)
          .orElse(pick((o, _) => o.wikidata.isDefined && o.wikidata == r.wikidata))
          .orElse(
            pick((o, _) =>
              o.provider != r.provider && o.group.isDefined && o.group == r.group &&
                sameKind(o.kind, r.kind) && overlaps(o.timeSpan, r.timeSpan, tol.years)
            )
          )
          .orElse(
            pick((o, _) =>
              o.provider != r.provider && sameKind(o.kind, r.kind) &&
                overlaps(o.timeSpan, r.timeSpan, tol.years) && near((o.lat, o.lon), (r.lat, r.lon), tol.km)
            )
          )
          .map {
            case Placement.Existing(id, _) => Placement.Existing(id, "merged with another record of this run")
            case Placement.New(id, _)      => Placement.New(id, "merged with another catalogue's record of it")
          }
      val p = storedMatch.orElse(runMatch).getOrElse(Placement.New(CatalogRecord.eventId(r), "new"))
      acc :+ ((i, r, p))
    }
    placed.sortBy(_._1).map((_, r, p) => r -> p).toList
