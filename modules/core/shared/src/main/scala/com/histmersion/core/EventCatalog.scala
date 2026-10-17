package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Events from CATALOGUES (#1449): the Smithsonian's Holocene eruptions, NOAA's earthquakes, eruptions
  * and tsunamis, Wikidata's battles, sieges, wars, treaties… — every event a catalogue knows, not only
  * the ones a compiled source names. A catalogue event is a RESEARCH LEAD, not evidence: it is drawn
  * lighter than an event a source in the corpus attests (docs/visualization-guide.md §1), and its card
  * names the catalogue, its licence and its citation as its provenance.
  *
  * This file is the pure half: the providers' identities and terms, the mapping of their datings onto
  * the four-point time-span, the records an import reads, how records from several catalogues and the
  * events already stored are merged into ONE event, the report of an import, and the shapes of the
  * proposed links to the corpus and of the questions an event raises. */

// ── providers ───────────────────────────────────────────────────────────────────────────────────

/** A catalogue: its key, what to call it, the licence its data comes under and the citation it asks
  * for. The terms were read on 2026-10-14 (see each). */
final case class CatalogProviderInfo(id: String, title: String, licence: String, citation: String, home: String)
    derives KindlingsCodecAsObject
object CatalogProviderInfo:
  given Schema[CatalogProviderInfo] = KindlingsSchema.derived[CatalogProviderInfo].schema

object CatalogProvider:
  val Gvp      = "gvp"
  val Noaa     = "noaa"
  val Wikidata = "wikidata"
  val all: List[String] = List(Gvp, Noaa, Wikidata)

  val infos: List[CatalogProviderInfo] = List(
    // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
    // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
    CatalogProviderInfo(Gvp, "Smithsonian Global Volcanism Program",
      "Smithsonian Terms of Use (non-commercial use with attribution)",
      "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
        "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2",
      "https://volcano.si.edu"),
    // a U.S. federal work: public domain; NCEI asks to be cited
    CatalogProviderInfo(Noaa, "NOAA NCEI Natural Hazards",
      "public-domain",
      "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
        "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K",
      "https://www.ngdc.noaa.gov/hazard/"),
    CatalogProviderInfo(Wikidata, "Wikidata", "CC0-1.0",
      "Wikidata (CC0), https://www.wikidata.org", "https://www.wikidata.org"))

  def info(id: String): Option[CatalogProviderInfo] = infos.find(_.id == id)

  /** Pure: the provider a request names (`smithsonian` and `volcano` for gvp, `ncei` for noaa). */
  def parse(s: String): Either[String, String] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case Gvp | "smithsonian" | "volcano"   => Right(Gvp)
      case Noaa | "ncei"                     => Right(Noaa)
      case Wikidata | "wd"                   => Right(Wikidata)
      case other => Left(s"unknown event catalogue '$other' (one of: ${all.mkString(", ")})")

/** How a catalogue's dating becomes the four-point span ([[TimeSpan]]): an uncertainty widens the
  * OUTER bounds and never invents a certain core. Pure. */
object CatalogDating:

  /** The years a start or an end may be, `lo ≤ hi`. */
  final case class Bound(lo: Long, hi: Long)

  /** Pure: a year with a ± uncertainty in years (negative or absent = none). */
  def year(y: Long, plusMinus: Option[Long] = None): Bound =
    val u = plusMinus.filter(_ > 0).getOrElse(0L)
    Bound(Years.shift(y, -u), Years.shift(y, u))

  /** Pure: the span of an event that starts within `start` and ends within `end` (either may be
    * unknown: then the one known stands for both — a one-day battle, an eruption whose end is not
    * recorded). The certain core (beginBy, endFrom) is kept only when the latest start is not after
    * the earliest end; otherwise it is left unknown rather than inverted. */
  def span(start: Option[Bound], end: Option[Bound]): TimeSpan =
    (start.orElse(end), end.orElse(start)) match
      case (Some(s), Some(e)) =>
        val core = Option.when(s.hi <= e.lo)((s.hi, e.lo))
        TimeSpan(Some(s.lo.min(e.lo)), core.map(_._1), core.map(_._2), Some(e.hi.max(s.hi)))
      case _ => TimeSpan()

  /** The years a catalogue's dating may cover: none after `present` (an uncertainty is a window
    * around a central estimate, never a statement that the event may lie in the future) and, where
    * the catalogue cannot precede it, none before `floor` (the GVP's list is of HOLOCENE eruptions:
    * `events.gvp.floor-year`, −9700). */
  final case class Limits(present: Long, floor: Option[Long] = None)

  /** Pure: each of the four points held inside `l` — monotone, so the order of the points is kept. */
  def clamp(ts: TimeSpan, l: Limits): TimeSpan =
    def c(y: Long): Long = l.floor.fold(y)(y.max).min(l.present)
    TimeSpan(ts.notBefore.map(c), ts.beginBy.map(c), ts.endFrom.map(c), ts.notAfter.map(c))

  /** Pure: a record's span held inside `l`; its central year and ± are kept as the catalogue states
    * them (the card says "250 CE ± 2500", whatever the clamp did to the window). */
  def clamp(r: CatalogRecord, l: Limits): CatalogRecord = r.copy(timeSpan = clamp(r.timeSpan, l))

  /** Pure: a record stored before the central year and the ± were kept (V088), read back from its
    * span: a start window with no certain core is `central ± half its width` (the GVP's year ± its
    * stated uncertainty, which is how the old importer built it); a cored one is centred on its start
    * window. `None` when the record is undated. Astronomical arithmetic: no year 0. */
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

  /** Pure: the years an ISO/XSD date at a Wikidata PRECISION covers: 9 a year, 8 a decade, 7 a century,
    * 6 a millennium, 5 … 0 ten thousand to a billion years; 10 and finer (a month, a day) the year.
    * `isoYear` is astronomical (`-0051` is 52 BCE). A decade/century/millennium is the one the year
    * falls in (Wikidata stores "5th century BCE" as some year inside it), widened by one unit each
    * way, because editors do not agree on which year inside the unit they store. */
  def wikidata(isoYear: Long, precision: Int): Bound =
    if precision >= 9 then { val h = Years.fromAstronomical(isoYear); Bound(h, h) }
    else
      val unit = math.pow(10, (9 - precision.max(0)).toDouble).toLong
      val lo   = Math.floorDiv(isoYear, unit) * unit
      Bound(Years.fromAstronomical(lo - unit), Years.fromAstronomical(lo + 2 * unit - 1))

  /** Pure: the astronomical year of an XSD dateTime (`-0051-07-01T00:00:00Z` → -51). */
  def isoYear(xsd: String): Option[Long] = Years.isoAstronomical(xsd)

// ── one record of a catalogue ───────────────────────────────────────────────────────────────────

/** What one catalogue says about one event. `providerId` is the catalogue's own key (a GVP eruption
  * number, `volcano:1351` at NOAA, a Wikidata item); `group` is a key shared by records of the same
  * thing across catalogues where the catalogues say so (the GVP volcano number NOAA quotes for an
  * eruption). `magnitude` (an earthquake's), `vei` (an eruption's Volcanic Explosivity Index) and
  * `deaths` are as the catalogue gives them. */
final case class CatalogRecord(
    provider: String,
    providerId: String,
    url: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    lat: Option[Double],
    lon: Option[Double],
    historicity: String = Historicity.Attested,
    vei: Option[Int] = None,
    magnitude: Option[Double] = None,
    deaths: Option[Long] = None,
    wikidata: Option[String] = None,
    names: List[String] = Nil,
    description: Option[String] = None,
    group: Option[String] = None,
    /** The year the catalogue STATES (GVP's `StartDateYear`, NOAA's `year`), before any ± widened it. */
    central: Option[Long] = None,
    /** The catalogue's stated ± in years around [[central]] (GVP's `StartDateYearUncertainty`). */
    plusMinus: Option[Long] = None,
    /** How the catalogue dated it (GVP's `StartEvidenceMethod`: `Tephrochronology`, `Isotopic: K/Ar`…). */
    datingMethod: Option[String] = None,
    /** The catalogue's own record as read (JSON text: a GVP feature, a NOAA item, the Wikidata rows
      * of the item) — what `events rederive` parses again with no network. */
    raw: Option[String] = None
) derives KindlingsCodecAsObject:
  /** The key it is stored under in `event_catalogue`. */
  def key: (String, String) = (provider, providerId)
object CatalogRecord:
  given Schema[CatalogRecord] = KindlingsSchema.derived[CatalogRecord].schema

  /** Pure: the id an event created from this record gets (`gvp_13879`, `noaa_volcano_1351`,
    * `wikidata_q131386`). Stable, so a re-import finds it. */
  def eventId(r: CatalogRecord): String = eventId(r.provider, r.providerId)

  def eventId(provider: String, providerId: String): String =
    (provider + "_" + providerId).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").stripSuffix("_")

  /** Pure: which of an event's catalogue records it is refreshed from (its OWNER): among the records
    * of the catalogue that created it, the one whose id it carries, else the lowest id. */
  def owner(eventId: String, catalogue: String, keys: Iterable[(String, String)]): Option[(String, String)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2)
    own.find(k => CatalogRecord.eventId(k._1, k._2) == eventId).orElse(own.headOption)

// ── merging: one event per thing, whichever catalogues know it ──────────────────────────────────

/** An event already stored, as the merge sees it: its kind, span, point, Wikidata item, the
  * catalogue keys recorded for it, and whether an import made it (`catalogue`) — a compiled event is
  * matched, never overwritten. */
final case class StoredEvent(id: String, kind: String, timeSpan: TimeSpan, lat: Option[Double], lon: Option[Double],
    wikidata: Option[String], keys: Set[(String, String)], catalogue: Boolean, label: String = "")

object CatalogMerge:

  /** How close two records of the same kind must be to be one event: their outer spans overlap once
    * each is widened by `years`, and their points are within `km`. */
  final case class Tolerance(km: Double, years: Long)

  /** Where one record goes: into a stored event (`existing`), or into the NEW event `id` (several
    * records of one thing share it). `why` says what matched. */
  enum Placement:
    case Existing(eventId: String, why: String)
    case New(eventId: String, why: String)
    def eventId: String

  private def outer(ts: TimeSpan): Option[(Long, Long)] = EventGeo.outer(ts)

  /** Pure: do two spans overlap once widened by `years`? An undated one overlaps nothing. */
  def overlaps(a: TimeSpan, b: TimeSpan, years: Long): Boolean =
    (outer(a), outer(b)) match
      case (Some((a0, a1)), Some((b0, b1))) => Years.shift(a0, -years) <= Years.shift(b1, years) && Years.shift(b0, -years) <= Years.shift(a1, years)
      case _ => false

  /** Pure: are two points within `km`? An unplaced one is near nothing. */
  def near(a: (Option[Double], Option[Double]), b: (Option[Double], Option[Double]), km: Double): Boolean =
    (a, b) match
      case ((Some(la), Some(lo)), (Some(lb), Some(ob))) => Geocode.distanceKm(lo, la, ob, lb) <= km
      case _ => false

  /** Two kinds are the same thing when their canonical kinds are equal (a siege is no battle here:
    * Wikidata lists both for one fight only when it says so through the item). */
  private def sameKind(a: String, b: String): Boolean = EventTaxonomy.canonical(a) == EventTaxonomy.canonical(b)

  /** Pure: how well a candidate's span and point fit a record's — the TIE-BREAK when several
    * qualify, compared in this order: the larger `score` (the overlap of the two outer spans over
    * their union, 0…1: the one whose dating agrees best), then the smaller `years` (between the
    * spans' midpoints), then the smaller `km`, then the smaller id. Undated or unplaced sides rank
    * last on that criterion (∞), never first. */
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
    *  1. the stored event that already holds its catalogue key;
    *  2. the stored event that is the same Wikidata item;
    *  3. a stored event of the same kind, overlapping in time and near in space, that no record of the
    *     SAME catalogue made (one catalogue's two records are two events: two eruptions of one volcano);
    *  4. a record placed before it in this run by the same key or item, or — from ANOTHER catalogue —
    *     the same `group` (+ overlapping time) or kind+time+space;
    * else it makes a new event, under [[CatalogRecord.eventId]].
    *
    * DETERMINISTIC: the records are placed in key order (provider, then id), whatever order they came
    * in, and where a rule finds several candidates the best [[Fit]] wins (overlap, then |Δt|, then
    * distance, then id) — never the first in the list. The answer keeps the order of `records`. */
  def place(records: List[CatalogRecord], stored: List[StoredEvent], tol: Tolerance): List[(CatalogRecord, Placement)] =
    val byKey = stored.flatMap(e => e.keys.map(_ -> e.id)).groupMap(_._1)(_._2).view.mapValues(_.min).toMap
    val byWd  = stored.flatMap(e => e.wikidata.map(_ -> e)).groupMap(_._1)(_._2)
    def best[A](cands: List[A])(f: A => Fit): Option[A] = cands.minByOption(f)
    def ofStored(r: CatalogRecord)(e: StoredEvent): Fit = fit(r, e.timeSpan, e.lat, e.lon, e.id)
    val order = records.zipWithIndex.sortBy((r, i) => (r.provider, r.providerId, i))
    // `var`-free fold: the records placed so far, with where they went
    val placed = order.foldLeft(Vector.empty[(Int, CatalogRecord, Placement)]) { case (acc, (r, i)) =>
      def storedMatch: Option[Placement] =
        byKey.get(r.key).map(Placement.Existing(_, s"${r.provider} ${r.providerId} already recorded"))
          .orElse(r.wikidata.flatMap(q => byWd.get(q)).flatMap(es => best(es)(ofStored(r)))
            .map(e => Placement.Existing(e.id, s"same Wikidata item ${r.wikidata.get}")))
          .orElse(best(stored.filter(e => !e.keys.exists(_._1 == r.provider) && sameKind(e.kind, r.kind) &&
              overlaps(e.timeSpan, r.timeSpan, tol.years) && near((e.lat, e.lon), (r.lat, r.lon), tol.km)))(ofStored(r))
            .map(e => Placement.Existing(e.id, s"same ${EventTaxonomy.canonical(r.kind)} at the same time and place as '${e.id}'")))
      def runMatch: Option[Placement] =
        // the rules in order; within one rule, the best fit
        val prior = acc.toList.map((_, o, p) => (o, p))
        def pick(rule: (CatalogRecord, Placement) => Boolean): Option[Placement] =
          best(prior.filter(rule.tupled))((o, p) => fit(r, o.timeSpan, o.lat, o.lon, p.eventId)).map(_._2)
        pick((o, _) => o.key == r.key)
          .orElse(pick((o, _) => o.wikidata.isDefined && o.wikidata == r.wikidata))
          .orElse(pick((o, _) => o.provider != r.provider && o.group.isDefined && o.group == r.group &&
              sameKind(o.kind, r.kind) && overlaps(o.timeSpan, r.timeSpan, tol.years)))
          .orElse(pick((o, _) => o.provider != r.provider && sameKind(o.kind, r.kind) &&
              overlaps(o.timeSpan, r.timeSpan, tol.years) && near((o.lat, o.lon), (r.lat, r.lon), tol.km)))
          .map {
            case Placement.Existing(id, _) => Placement.Existing(id, "merged with another record of this run")
            case Placement.New(id, _)      => Placement.New(id, "merged with another catalogue's record of it")
          }
      val p = storedMatch.orElse(runMatch).getOrElse(Placement.New(CatalogRecord.eventId(r), "new"))
      acc :+ ((i, r, p))
    }
    placed.sortBy(_._1).map((_, r, p) => r -> p).toList

// ── an import ───────────────────────────────────────────────────────────────────────────────────

/** `POST /api/events/import` (a job): read `provider`'s catalogue — `bbox` (`minLon,minLat,maxLon,maxLat`),
  * the years `from`/`to` (signed), `minVei` (eruptions) and `minMagnitude` (earthquakes) narrow it
  * further than the configured defaults — and, unless `dryRun`, store what it finds, merged with what
  * is there. `limit` caps the records taken; `refresh` asks again for the parts already read. */
final case class EventImportRequest(
    provider: String,
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    bbox: Option[String] = None,
    from: Option[Long] = None,
    to: Option[Long] = None,
    minVei: Option[Int] = None,
    minMagnitude: Option[Double] = None,
    refresh: Boolean = false
) derives KindlingsCodecAsObject
object EventImportRequest:
  given Schema[EventImportRequest] = KindlingsSchema.derived[EventImportRequest].schema

/** A count by key (a kind, a reason). */
final case class EventCount(key: String, n: Int) derives KindlingsCodecAsObject
object EventCount:
  given Schema[EventCount] = KindlingsSchema.derived[EventCount].schema
  /** Pure: count `keys`, most frequent first, then by key. */
  def of(keys: List[String]): List[EventCount] =
    keys.groupBy(identity).toList.map((k, v) => EventCount(k, v.size)).sortBy(c => (-c.n, c.key))
  def add(a: List[EventCount], b: List[EventCount]): List[EventCount] =
    (a ++ b).groupBy(_.key).toList.map((k, cs) => EventCount(k, cs.map(_.n).sum)).sortBy(c => (-c.n, c.key))

/** What an import read and did (a dry run: would do). `records` read from the catalogue; `skipped`
  * per reason (no position, below the threshold, outside the years…); `kept` records that pass;
  * `created` new events, `merged` records joined to another record of this run, `matched` records
  * joined to an event already stored (`matchedEvents` names a few); `byKind` the kept records'
  * kinds; `parts` the catalogue requests (a class, a page) done, `pending` those left for the next run. */
final case class EventImportReport(
    provider: String,
    dryRun: Boolean,
    records: Int = 0,
    kept: Int = 0,
    created: Int = 0,
    merged: Int = 0,
    matched: Int = 0,
    updated: Int = 0,
    byKind: List[EventCount] = Nil,
    skipped: List[EventCount] = Nil,
    matchedEvents: List[String] = Nil,
    parts: Int = 0,
    pending: Int = 0,
    requests: Int = 0,
    failed: List[String] = Nil,
    licence: String = "",
    citation: String = "",
    /** The refreshed events' corrections by hand ([[Corrections]]): kept, retired (the catalogue now
      * says what the person said), and in conflict (the catalogue changed to something else). */
    corrections: CorrectionCounts = CorrectionCounts()
) derives KindlingsCodecAsObject
object EventImportReport:
  given Schema[EventImportReport] = KindlingsSchema.derived[EventImportReport].schema

  def combine(a: EventImportReport, b: EventImportReport): EventImportReport =
    EventImportReport(b.provider, b.dryRun, a.records + b.records, a.kept + b.kept, a.created + b.created,
      a.merged + b.merged, a.matched + b.matched, a.updated + b.updated, EventCount.add(a.byKind, b.byKind),
      EventCount.add(a.skipped, b.skipped), (a.matchedEvents ++ b.matchedEvents).distinct.take(20),
      a.parts + b.parts, b.pending, a.requests + b.requests, a.failed ++ b.failed,
      if b.licence.nonEmpty then b.licence else a.licence, if b.citation.nonEmpty then b.citation else a.citation,
      a.corrections + b.corrections)

  /** Pure: the one line `histmersion jobs` shows for a finished import. */
  def summary(r: EventImportReport): String =
    val verb = if r.dryRun then "would create" else "created"
    List(
      Some(s"${r.provider}: ${r.records} record(s) read, ${r.kept} kept"),
      Some(s"$verb ${r.created} event(s), ${r.merged} merged with another record of the same thing, ${r.matched} matched to stored events"),
      Option.when(r.byKind.nonEmpty)("kinds: " + r.byKind.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.skipped.nonEmpty)("skipped: " + r.skipped.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.pending > 0)(s"${r.pending} part(s) not read yet"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
      r.corrections.summary
    ).flatten.mkString(" · ")

  /** Pure: the report as Markdown (the CLI after the job, the MCP answer). */
  def markdown(r: EventImportReport): String =
    def counts(cs: List[EventCount]) = if cs.isEmpty then "none" else cs.map(c => s"${c.key} ${c.n}").mkString(" · ")
    val head = s"## Event import — ${r.provider}${if r.dryRun then " (dry run: nothing stored)" else ""}"
    val lines = List(
      s"- records read: ${r.records} (${r.parts} request part(s), ${r.requests} HTTP request(s))",
      s"- kept: ${r.kept} — ${if r.dryRun then "would create" else "created"} ${r.created} event(s), " +
        s"${r.merged} merged with another catalogue record, ${r.matched} matched to stored events" +
        (if r.updated > 0 then s", ${r.updated} refreshed" else ""),
      s"- by kind: ${counts(r.byKind)}",
      s"- skipped: ${counts(r.skipped)}") ++
      Option.when(r.matchedEvents.nonEmpty)(s"- matched: ${r.matchedEvents.mkString(", ")}").toList ++
      Option.when(r.pending > 0)(s"- not read yet: ${r.pending} part(s) (the next run takes them)").toList ++
      Option.when(r.failed.nonEmpty)(s"- failed: ${r.failed.mkString("; ")}").toList ++
      r.corrections.summary.map(c => s"- $c (`histmersion events corrections`)").toList ++
      Option.when(r.licence.nonEmpty)(s"- licence: ${r.licence}").toList ++
      Option.when(r.citation.nonEmpty)(s"- cite as: ${r.citation}").toList
    (head :: "" :: lines).mkString("\n")

/** `POST /api/events/rederive` (a job): compute the dating of the stored catalogue records again from
  * what each record keeps (`event_catalogue.record`: the catalogue's own record as read) — NO network
  * — and refresh the events those catalogues created, their corrections by hand applied. `provider`
  * narrows it to one catalogue; `dryRun` counts what would change. */
final case class EventRederiveRequest(provider: Option[String] = None, dryRun: Boolean = false)
    derives KindlingsCodecAsObject
object EventRederiveRequest:
  given Schema[EventRederiveRequest] = KindlingsSchema.derived[EventRederiveRequest].schema

/** What a re-derivation read and did: `records` read back, `changed` whose dating changed, `fromRaw`
  * parsed again from the catalogue's own record (the rest, stored before V088 kept it, read back from
  * their span: [[CatalogDating.centralOf]]), the events refreshed, the precision of the records
  * before and after ([[EventPrecision]]) and how many leave the map (`offMap`: imprecise now), and a
  * few examples. */
final case class EventRederiveReport(
    dryRun: Boolean,
    providers: List[String],
    records: Int = 0,
    changed: Int = 0,
    fromRaw: Int = 0,
    updated: Int = 0,
    before: List[EventCount] = Nil,
    after: List[EventCount] = Nil,
    offMap: Int = 0,
    examples: List[String] = Nil,
    corrections: CorrectionCounts = CorrectionCounts()
) derives KindlingsCodecAsObject
object EventRederiveReport:
  given Schema[EventRederiveReport] = KindlingsSchema.derived[EventRederiveReport].schema

  /** Pure: the one line `histmersion jobs` shows. */
  def summary(r: EventRederiveReport): String =
    def counts(cs: List[EventCount]) = cs.map(c => s"${c.key} ${c.n}").mkString(", ")
    List(
      Some(s"${r.providers.mkString(", ")}: ${r.records} record(s) re-derived (${r.fromRaw} from the catalogue's own record)"),
      Some(s"${r.changed} dating(s) ${if r.dryRun then "would change" else "changed"}, ${r.updated} event(s) refreshed"),
      Option.when(r.after.nonEmpty)(s"precision ${counts(r.before)} → ${counts(r.after)}"),
      Option.when(r.offMap > 0)(s"${r.offMap} imprecise (off the map, on the list)"),
      r.corrections.summary
    ).flatten.mkString(" · ")

  def markdown(r: EventRederiveReport): String =
    val head = s"## Event re-derivation — ${r.providers.mkString(", ")}${if r.dryRun then " (dry run: nothing stored)" else ""}"
    (head :: "" :: summary(r).split(" · ").toList.map("- " + _) ++ r.examples.map("  - " + _)).mkString("\n")

// ── an event's catalogue records, as its card shows them ────────────────────────────────────────

/** One catalogue's record of an event, with what its licence asks to be shown. */
final case class EventCatalogueRef(
    provider: String,
    providerId: String,
    url: String,
    title: String,
    licence: String,
    citation: String,
    vei: Option[Int] = None,
    magnitude: Option[Double] = None,
    deaths: Option[Long] = None,
    retrievedAt: Option[String] = None
) derives KindlingsCodecAsObject
object EventCatalogueRef:
  given Schema[EventCatalogueRef] = KindlingsSchema.derived[EventCatalogueRef].schema

/** An event's TIER (docs/visualization-guide.md §1): `attested` when a source in the corpus attests it
  * (its own `citekey`, or an attestation row), `catalogue` when only a catalogue knows it. */
object EventTier:
  val Attested  = "attested"
  val Catalogue = "catalogue"
  val all: List[String] = List(Attested, Catalogue)
