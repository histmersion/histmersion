package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  CatalogRecord,
  EventCount,
  EventGeo,
  EventImportReport,
  EventImportRequest,
  EventPrecision,
  EventRederiveReport,
  EventSummary,
  EventTaxonomy
}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.config.{AppConfig, MediaConfig}
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.domain.catalogue.Event
import com.histmersion.pluginhost.{HttpTransport, PluginEvents, Polite}
import com.histmersion.plugin.{EventCatalogue, Plugin}
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import hearth.kindlings.optics.syntax.*

import scala.collection.immutable.ListMap
import com.histmersion.api.catalogue.Correction
import com.histmersion.upstreams.dating.CatalogDating
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year

/** The import loop of the event catalogues — Histmersion's own and the plugins' alike — and the re-derivation of the
  * stored records' dating (no network). `registered` are the catalogues of the calling thread's plugin generation;
  * `presentYear` the year the datings are held inside; `held` whether a maintenance run holds the lock (a run stops
  * between parts).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class EventImports(
    store: EventImportService,
    registered: () => ListMap[CatalogRecord.Provider, EventCatalogue],
    cfg: () => AppConfig,
    http: HttpTransport,
    presentYear: () => SignedYear,
    held: () => Boolean,
    owners: () => Map[EventCatalogue, Plugin.Info.Id]
):
  private def ec = cfg().events

  private def importer(provider: CatalogRecord.Provider): Creator.New =
    InTreeWorkflow.imported(
      registered().get(provider).flatMap(owners().get),
      InTreeWorkflow.Catalogue,
      InTreeWorkflow.EventImporting
    )

  /** The configured Wikidata classes' kinds, the most specific first. */
  private def classOrder: List[EventSummary.Kind] = ec.wikidata.classList.map(_._2)

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise (User-Agent, retries)
    * — and up to two minutes' Retry-After, which the query service asks for.
    */
  private def polite(intervalMs: MediaConfig.RequestIntervalMs): Polite =
    PluginEvents.polite(http, cfg().media, intervalMs)

  /** The limits of `provider`'s dating, the ONE rule of the import and of rederive: the present for every catalogue,
    * and the catalogue's own floor (the GVP's Holocene one) while it is registered.
    */
  private def limitsOf(provider: CatalogRecord.Provider, present: SignedYear): CatalogDating.Limits =
    PluginEvents.limits(registered().get(provider), present)

  /** A catalogue's terms: a registered one's, else a built-in's (its records outlive its plugin). */
  def infoOf(provider: CatalogRecord.Provider): Option[CatalogProviderInfo] =
    BuiltinCatalogues.info(provider, registered())

  def catalogues: List[CatalogProviderInfo] = BuiltinCatalogues.infos(registered())

  /** The catalogue an import names among the registered ones, or why none. */
  def resolve(asked: CatalogRecord.Provider): Either[Problem, CatalogRecord.Provider] =
    BuiltinCatalogues.resolve(asked, registered())

  /** The catalogue a re-derivation names: a built-in one even while it is switched off (its records are still there),
    * else a registered one.
    */
  def resolveStored(asked: CatalogRecord.Provider): Either[Problem, CatalogRecord.Provider] =
    val n = BuiltinCatalogues.canonical(asked)
    if CatalogProvider.all.contains(n) then Right(n) else resolve(asked)

  /** One catalogue part read: its key and the records (or why not). */
  private type Fetched = Either[Problem, List[CatalogRecord]]

  /** The import itself — blocking; the job runs it. Part by part: read, keep what passes the thresholds and the years,
    * place each record ([[CatalogMerge.place]]) against what is stored and what this run placed before, and (not a dry
    * run) write the part and record it as read.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def runImport(
      provider: CatalogRecord.Provider,
      req: EventImportRequest,
      area: Option[BBox],
      progress: JobInfo.Progress => Unit
  )(using WritePermit)(using Creator.New): EventImportReport =
    given Creator.New = importer(provider)
    val info = infoOf(provider)
    val catalogue = registered().get(provider)
    val dryRun = req.dryRun.dryRun
    val to = SignedYear(req.to.fold(ec.maxYear.value)(_.value))
    val from = req.from.map(f => SignedYear(f.value))
    val limit = req.limit.map(_.value).filter(_ > 0).getOrElse(ec.defaultLimit.value)
    val th = EventProviders.Thresholds(
      ec.regionBox,
      EventProviders.Thresholds.RegionVei(req.minVei.fold(ec.gvp.regionMinVei.value)(_.value)),
      EventProviders.Thresholds.OutsideVei(req.minVei.fold(ec.gvp.outsideMinVei.value)(_.value)),
      EventProviders.Thresholds.RegionMagnitude(req.minMagnitude.fold(ec.noaa.regionMinMagnitude.value)(_.value)),
      EventProviders.Thresholds.OutsideMagnitude(req.minMagnitude.fold(ec.noaa.outsideMinMagnitude.value)(_.value)),
      area
    )
    if req.refresh.refresh && !dryRun then store.clearParts(provider)
    val scope = EventProviders.scope(th)
    val done =
      if dryRun then Set.empty[String] else EventProviders.partsIn(store.partsDone(provider).map(_.value), scope)
    val stored0 = store.stored()
    val preRun = stored0.map(_.id).toSet
    // `var`s: the run's state across parts — what is placed so far, the report, the requests sent
    var known = stored0
    var seen = Set.empty[Event.Id]
    var report =
      EventImportReport(
        EventImportReport.Provider(provider.value),
        (if dryRun then EventImportReport.DryRun.DryRun else EventImportReport.DryRun.Apply),
        licence = info.fold(EventImportReport.Licence(""))(i => EventImportReport.Licence(i.licence.value)),
        citation = info.fold(EventImportReport.Citation(""))(i => EventImportReport.Citation(i.citation.value))
      )
    val clients = scala.collection.mutable.ListBuffer.empty[Polite]
    def client(ms: MediaConfig.RequestIntervalMs): Polite = { val p = polite(ms); clients += p; p }

    def handle(part: PluginEvents.PartName, fetched: Fetched)(using WritePermit)(using Creator.New): Unit =
      fetched match
        case Left(why) =>
          report = report.modify(_.failed).using(_ :+ EventImportReport.Failed(s"$part: ${why.sentence}"))
        case Right(read) =>
          // a catalogue's dating held inside the present (and its own floor, the GVP's Holocene one); a
          // record dated from its stated year when it gives no span — as rederive does
          val lim = limitsOf(provider, presentYear())
          val date: CatalogRecord => CatalogRecord = PluginEvents.derive(_, lim)
          // an item read again in a later (less specific) class keeps the kind it was stored with
          val kinds = if provider == CatalogProvider.Wikidata && !dryRun then store.storedKinds(provider)
          else Map.empty
          val recs = read
            .map(date)
            .map(r =>
              kinds
                .get(r.key)
                .fold(r)(k =>
                  r.modify(_.kind)
                    .setTo(
                      CatalogRecord.Kind(
                        EventProviders.WikidataEvents.moreSpecific(classOrder, k, r.kind.summary).value
                      )
                    )
                )
            )
          val judged = recs.map { r =>
            val span = r.timeSpan.outer
            val why =
              if span.exists(_._1.value > to.value) then Left(EventProviders.Rationale.After(to))
              else if from.exists(f => span.exists(_._2.value < f.value)) then
                Left(EventProviders.Rationale.Before(from.get))
              else EventProviders.keep(r, th)
            r -> why
          }
          val room = (limit - report.kept.value).max(0)
          val kept0 = judged.collect { case (r, Right(_)) => r }
          val kept = kept0.take(room)
          val skipped = judged.collect { case (_, Left(w)) => EventCount.Key(w.said) } ++
            List.fill(kept0.size - kept.size)(EventCount.Key("over --limit"))
          val named = if dryRun || provider != CatalogProvider.Wikidata then kept else withNames(kept, report)
          val placed = Placement.place(named, known, ec.tolerance)
          var created, merged, matched = 0
          val matchedIds = List.newBuilder[String]
          placed.foreach { (r, p) =>
            p match
              case Placement.Existing(id, _) if preRun(id) =>
                matched += 1; matchedIds += s"${r.label} → $id"
              case _ if seen(p.eventId) => merged += 1
              case _                    => created += 1
            seen += p.eventId
          }
          val newEvents = placed
            .collect { case (r, Placement.New(id, _)) => (id, r) }
            .groupBy(_._1)
            .toList
            .map((id, rs) =>
              StoredEvent(
                id,
                StoredEvent.Kind(rs.head._2.kind.value),
                rs.head._2.timeSpan,
                rs.head._2.lat.flatMap(l => Latitude.option(l.value)),
                rs.head._2.lon.flatMap(l => Longitude.option(l.value)),
                rs.head._2.wikidata,
                rs.map(_._2.key).toSet,
                StoredEvent.Catalogue(true),
                StoredEvent.Label(rs.head._2.label.value)
              )
            )
          known = known.filterNot(e => newEvents.exists(_.id == e.id)) ++ newEvents
          val saved =
            if dryRun then None
            else
              val s = store.save(placed)
              store.markPart(
                provider,
                EventImportRepo.PartName(EventProviders.scopedPart(part.value, scope)),
                RowCount(recs.size)
              )
              Some(s)
          report = EventImportReport.combine(
            report,
            EventImportReport(
              EventImportReport.Provider(provider.value),
              (if dryRun then EventImportReport.DryRun.DryRun else EventImportReport.DryRun.Apply),
              EventImportReport.Records(recs.size),
              EventImportReport.Kept(kept.size),
              EventImportReport.Created(created),
              EventImportReport.Merged(merged),
              EventImportReport.Matched(matched),
              EventImportReport.Updated(saved.fold(0)(_.updated.value)),
              EventCount.of(
                kept.map(r => EventCount.Key(EventTaxonomy.canonical(r.kind.summary).value))
              ),
              EventCount.of(skipped),
              matchedIds.result().take(20).map(EventImportReport.MatchedEvents(_)),
              parts = EventImportReport.Parts(1),
              corrections = saved.fold(Correction.Counts())(_.corrections)
            )
          )
      progress(
        JobInfo.Progress(
          s"${report.parts} part(s) · ${report.records} read · ${report.kept} kept · ${report.created} new · " +
            s"${report.matched} matched"
        )
      )

    def full = report.kept.value >= limit || held()

    /** A catalogue — Histmersion's own or a third party's, alike: its parts in its order, each read once (a part read
      * before is skipped, unless the catalogue's parts are pages of a list that changes), a paged part followed to its
      * last page; every record checked by the host ([[PluginEvents.record]]) before the thresholds, the merge and the
      * store.
      */
    def run(cat: EventCatalogue)(using WritePermit)(using Creator.New): Unit =
      val c = client(MediaConfig.RequestIntervalMs(PluginEvents.safe(cat.requestIntervalMs).fold(1000)(_.value)))
      val h = PluginEvents.http(c)
      val q = PluginEvents.query(from, to, area)
      val read: PluginEvents.PartName => Boolean =
        if PluginEvents.safe(cat.everyPartEachImport).contains(true) then _ => false else p => done(p.value)
      PluginEvents.guard(Right(cat.parts(q))) match
        case Left(why) =>
          report = report.modify(_.failed).using(_ :+ EventImportReport.Failed(s"parts: ${why.sentence}"))
        case Right(parts) =>
          parts.distinct.foreach { first =>
            PluginEvents.readPart(cat, PluginEvents.PartName(first.value), q, h, read, full) { (part, got) =>
              val recs = got.map(_.events.map(PluginEvents.record(provider, _)))
              recs.foreach(rs =>
                if rs.exists(_.isLeft) then
                  report = report
                    .modify(_.failed)
                    .using(
                      _ ++ rs.collect { case Left(w) => EventImportReport.Failed(s"$part: ${w.sentence}") }.take(20)
                    )
                rs.collect { case Right(c) => c.warnings }
                  .flatten
                  .foreach(w => scribe.warn(s"events import: $part: $w"))
              )
              handle(part, recs.map(_.collect { case Right(c) => c.record }))
            }
          }

    catalogue match
      case None =>
        report = report
          .modify(_.failed)
          .using(_ :+ EventImportReport.Failed(resolve(provider).fold(_.sentence, _ => provider.value)))
      case Some(cat) => run(cat)

    // the parts still to read: none for a catalogue whose parts are all read at every import
    val pending = catalogue match
      case Some(cat) if !dryRun && !PluginEvents.safe(cat.everyPartEachImport).contains(true) =>
        val now = EventProviders.partsIn(store.partsDone(provider).map(_.value), scope)
        PluginEvents
          .guard(Right(cat.parts(PluginEvents.query(from, to, area))))
          .fold(_ => 0, _.distinct.count(p => !now(p.value)))
      case _ => 0
    val out = report
      .modify(_.pending)
      .setTo(EventImportReport.Pending(pending))
      .modify(_.requests)
      .setTo(EventImportReport.Requests(clients.map(_.requests.value).sum + report.requests.value))
    scribe.info(s"events import: ${EventImportReport.summary(out)}")
    out

  /** Re-derive, blocking (the job runs it): every stored record of `provider` (all catalogues') read back, its dating
    * computed again (read again by its catalogue from the catalogue's own record where it keeps one, while the
    * catalogue is registered — [[com.histmersion.plugin.EventCatalogue.reread]]), and — not a dry run — written with
    * the events their catalogue created refreshed ([[EventImportService.save]], corrections by hand applied),
    * `rederive-batch` records a transaction.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def runRederive(
      provider: Option[CatalogRecord.Provider],
      dryRun: Boolean,
      progress: JobInfo.Progress => Unit
  )(using WritePermit)(using Creator.New): EventRederiveReport =
    val (rows, undecodable) = store.records(provider)
    val present = presentYear()
    def prec(r: CatalogRecord) = EventPrecision.of(r.timeSpan, r.plusMinus.map(p => EventSummary.PlusMinus(p.value)))
    val again = rows.map { (eventId, eventKind, r) =>
      // read again by its catalogue where it re-reads (from the catalogue's own record), else derived from the
      // stored one as at the import
      val reread = registered().get(r.provider).flatMap(PluginEvents.reread(_, r.provider, r))
      val d = PluginEvents.derive(reread.getOrElse(r), limitsOf(r.provider, present))
      // a Wikidata item read in two classes keeps the more specific kind (V085 stored the last class's)
      val k = if r.provider == CatalogProvider.Wikidata then
        CatalogRecord.Kind(
          EventProviders.WikidataEvents.moreSpecific(classOrder, d.kind.summary, eventKind).value
        )
      else d.kind
      (eventId, r, d.modify(_.kind).setTo(k), reread.isDefined)
    }
    val changed = again.filter((_, a, b, _) =>
      (a.timeSpan, a.central, a.plusMinus, a.datingMethod, a.kind) != (
        b.timeSpan,
        b.central,
        b.plusMinus,
        b.datingMethod,
        b.kind
      )
    )
    // `var`s: the counts of the write
    var updated = 0
    var counts = Correction.Counts()
    if !dryRun then
      again.grouped(ec.rederiveBatch.value.max(1)).zipWithIndex.foreach { (batch, i) =>
        val s =
          store.save(
            batch
              .map((id, _, b, _) => b -> Placement.Existing(id, Placement.Rationale("re-derived")))
          )
        updated += s.updated.value
        counts = counts + s.corrections
        progress(
          JobInfo.Progress(
            s"${(i + 1) * ec.rederiveBatch.value.max(1) min again.size} of ${again.size} record(s) re-derived"
          )
        )
      }
    val examples = changed.take(5).map { (id, a, b, _) =>
      def years(r: CatalogRecord) = r.timeSpan.outer.fold("undated")((x, y) => s"${Year.show(x)}…${Year.show(y)}")
      s"$id: ${years(a)} → ${years(b)}${EventPrecision
          .note(
            b.central.map(c => EventSummary.Central(c.value)),
            b.plusMinus.map(p => EventSummary.PlusMinus(p.value)),
            b.datingMethod.map(m => EventSummary.DatingMethod(m.value))
          )
          .fold("")(n => s" ($n)")}, ${prec(b)}"
    } ++ Option.when(undecodable.value > 0)(s"$undecodable stored record(s) no longer decode — left alone").toList
    val out = EventRederiveReport(
      (if dryRun then EventRederiveReport.DryRun.DryRun else EventRederiveReport.DryRun.Apply),
      provider.toList
        .ifEmpty((CatalogProvider.all ++ registered().keys).distinct)
        .map(p => EventRederiveReport.Providers(p.value)),
      EventRederiveReport.Records(again.size),
      EventRederiveReport.Changed(changed.size),
      EventRederiveReport.FromRaw(again.count((_, a, _, reread) => a.raw.isDefined && reread)),
      EventRederiveReport.Updated(updated),
      EventCount.of(again.map((_, a, _, _) => EventCount.Key(prec(a).value))),
      EventCount.of(again.map((_, _, b, _) => EventCount.Key(prec(b).value))),
      EventRederiveReport.OffMap(again.count((_, _, b, _) => !EventPrecision.mapped(prec(b)))),
      examples.map(EventRederiveReport.Examples(_)),
      counts
    )
    scribe.info(s"events rederive: ${EventRederiveReport.summary(out)}")
    out

  extension [A](l: List[A]) private def ifEmpty(d: => List[A]): List[A] = if l.isEmpty then d else l

  /** The labels and aliases of the Wikidata items among `recs`, in the configured languages. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def withNames(recs: List[CatalogRecord], report: EventImportReport): List[CatalogRecord] =
    val ids = recs.flatMap(_.wikidata).distinct
    if ids.isEmpty then recs
    else
      val c = polite(MediaConfig.RequestIntervalMs(ec.wikidata.apiIntervalMs.value))
      val names = ids
        .grouped(ec.wikidata.batch.value.max(1))
        .toList
        .flatMap { batch =>
          c.json(
            HttpTransport.Url(
              EventProviders.WikidataEvents
                .namesUrl(ec.wikidata.apiUrl.value, batch.map(_.value), ec.wikidata.languageList.map(_.value))
            )
          ).toOption
            .flatMap(EventProviders.WikidataEvents.names)
            .getOrElse(Map.empty)
            .toList
        }
        .toMap
      recs.map(r =>
        r.wikidata
          .flatMap(w => names.get(w.value))
          .fold(r)(ns =>
            r.modify(_.names)
              .setTo((r.names ++ ns.map(CatalogRecord.Names(_))).filterNot(_.value == r.label.value).distinct)
          )
      )
