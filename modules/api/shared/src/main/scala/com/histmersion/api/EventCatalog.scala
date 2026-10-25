package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Historicity

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
  * their span: `CatalogDating.centralOf`), the events refreshed, the precision of the records
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
