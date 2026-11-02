package com.histmersion.domain.catalogue.events

import com.histmersion.api.{BBox, YearFmt}
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  CatalogRecord,
  CorrectionCounts,
  EventCount,
  EventGeo,
  EventImportReport,
  EventImportRequest,
  EventPrecision,
  EventRederiveReport,
  EventTaxonomy
}
import com.histmersion.config.AppConfig
import com.histmersion.pluginhost.{HttpTransport, PluginEvents, Polite}
import com.histmersion.plugin.EventCatalogue
import hearth.kindlings.optics.syntax.*

import scala.collection.immutable.ListMap

/** The import loop of the event catalogues — Histmersion's own and the plugins' alike — and the re-derivation of the
  * stored records' dating (no network). `registered` are the catalogues of the calling thread's plugin generation;
  * `presentYear` the year the datings are held inside; `held` whether a maintenance run holds the lock (a run stops
  * between parts).
  */
final class EventImports(
    store: EventImportService,
    registered: () => ListMap[String, EventCatalogue],
    cfg: () => AppConfig,
    http: HttpTransport,
    presentYear: () => Long,
    held: () => Boolean
):
  private def ec = cfg().events

  /** The configured Wikidata classes' kinds, the most specific first. */
  private def classOrder: List[String] = ec.wikidata.classList.map(_._2)

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise (User-Agent, retries)
    * — and up to two minutes' Retry-After, which the query service asks for.
    */
  private def polite(intervalMs: Int): Polite =
    PluginEvents.polite(http, cfg().media, intervalMs)

  /** The limits of `provider`'s dating, the ONE rule of the import and of rederive: the present for every catalogue,
    * and the catalogue's own floor (the GVP's Holocene one) while it is registered.
    */
  private def limitsOf(provider: String, present: Long): com.histmersion.upstreams.dating.CatalogDating.Limits =
    PluginEvents.limits(registered().get(provider), present)

  /** A catalogue's terms: a registered one's, else a built-in's (its records outlive its plugin). */
  def infoOf(provider: String): Option[CatalogProviderInfo] = BuiltinCatalogues.info(provider, registered())

  def catalogues: List[CatalogProviderInfo] = BuiltinCatalogues.infos(registered())

  /** The catalogue an import names among the registered ones, or why none. */
  def resolve(asked: String): Either[String, String] = BuiltinCatalogues.resolve(asked, registered())

  /** The catalogue a re-derivation names: a built-in one even while it is switched off (its records are still there),
    * else a registered one.
    */
  def resolveStored(asked: String): Either[String, String] =
    val n = BuiltinCatalogues.canonical(asked)
    if CatalogProvider.all.contains(n) then Right(n) else resolve(asked)

  /** One catalogue part read: its key and the records (or why not). */
  private type Fetched = Either[String, List[CatalogRecord]]

  /** The import itself — blocking; the job runs it. Part by part: read, keep what passes the thresholds and the years,
    * place each record ([[CatalogMerge.place]]) against what is stored and what this run placed before, and (not a dry
    * run) write the part and record it as read.
    */
  def runImport(
      provider: String,
      req: EventImportRequest,
      area: Option[BBox],
      progress: String => Unit
  ): EventImportReport =
    val info = infoOf(provider)
    val catalogue = registered().get(provider)
    val to = req.to.getOrElse(ec.maxYear)
    val from = req.from
    val limit = req.limit.filter(_ > 0).getOrElse(ec.defaultLimit)
    val th = EventProviders.Thresholds(
      ec.regionBox,
      req.minVei.getOrElse(ec.gvp.regionMinVei),
      req.minVei.getOrElse(ec.gvp.outsideMinVei),
      req.minMagnitude.getOrElse(ec.noaa.regionMinMagnitude),
      req.minMagnitude.getOrElse(ec.noaa.outsideMinMagnitude),
      area
    )
    if req.refresh && !req.dryRun then store.clearParts(provider)
    val scope = EventProviders.scope(th)
    val done =
      if req.dryRun then Set.empty[String] else EventProviders.partsIn(store.partsDone(provider), scope)
    val stored0 = store.stored()
    val preRun = stored0.map(_.id).toSet
    // `var`s: the run's state across parts — what is placed so far, the report, the requests sent
    var known = stored0
    var seen = Set.empty[String]
    var report =
      EventImportReport(provider, req.dryRun, licence = info.fold("")(_.licence), citation = info.fold("")(_.citation))
    val clients = scala.collection.mutable.ListBuffer.empty[Polite]
    def client(ms: Int): Polite = { val p = polite(ms); clients += p; p }

    def handle(part: String, fetched: Fetched): Unit =
      fetched match
        case Left(why)   => report = report.modify(_.failed).using(_ :+ s"$part: $why")
        case Right(read) =>
          // a catalogue's dating held inside the present (and its own floor, the GVP's Holocene one); a
          // record dated from its stated year when it gives no span — as rederive does
          val lim = limitsOf(provider, presentYear())
          val date: CatalogRecord => CatalogRecord = PluginEvents.derive(_, lim)
          // an item read again in a later (less specific) class keeps the kind it was stored with
          val kinds = if provider == CatalogProvider.Wikidata && !req.dryRun then store.storedKinds(provider)
          else Map.empty
          val recs = read
            .map(date)
            .map(r =>
              kinds
                .get(r.key)
                .fold(r)(k => r.modify(_.kind).setTo(EventProviders.WikidataEvents.moreSpecific(classOrder, k, r.kind)))
            )
          val judged = recs.map { r =>
            val span = r.timeSpan.outer
            val why =
              if span.exists(_._1 > to) then Left(s"after $to")
              else if from.exists(f => span.exists(_._2 < f)) then Left(s"before ${from.get}")
              else EventProviders.keep(r, th)
            r -> why
          }
          val room = (limit - report.kept).max(0)
          val kept0 = judged.collect { case (r, Right(_)) => r }
          val kept = kept0.take(room)
          val skipped = judged.collect { case (_, Left(w)) => w } ++ List.fill(kept0.size - kept.size)("over --limit")
          val named = if req.dryRun || provider != CatalogProvider.Wikidata then kept else withNames(kept, report)
          val placed = CatalogMerge.place(named, known, ec.tolerance)
          var created, merged, matched = 0
          val matchedIds = List.newBuilder[String]
          placed.foreach { (r, p) =>
            p match
              case CatalogMerge.Placement.Existing(id, _) if preRun(id) =>
                matched += 1; matchedIds += s"${r.label} → $id"
              case _ if seen(p.eventId) => merged += 1
              case _                    => created += 1
            seen += p.eventId
          }
          val newEvents = placed
            .collect { case (r, CatalogMerge.Placement.New(id, _)) => (id, r) }
            .groupBy(_._1)
            .toList
            .map((id, rs) =>
              StoredEvent(
                id,
                rs.head._2.kind,
                rs.head._2.timeSpan,
                rs.head._2.lat,
                rs.head._2.lon,
                rs.head._2.wikidata,
                rs.map(_._2.key).toSet,
                catalogue = true,
                rs.head._2.label
              )
            )
          known = known.filterNot(e => newEvents.exists(_.id == e.id)) ++ newEvents
          val saved =
            if req.dryRun then None
            else
              val s = store.save(placed)
              store.markPart(provider, EventProviders.scopedPart(part, scope), recs.size)
              Some(s)
          report = EventImportReport.combine(
            report,
            EventImportReport(
              provider,
              req.dryRun,
              recs.size,
              kept.size,
              created,
              merged,
              matched,
              saved.fold(0)(_.updated),
              EventCount.of(kept.map(r => EventTaxonomy.canonical(r.kind))),
              EventCount.of(skipped),
              matchedIds.result().take(20),
              parts = 1,
              corrections = saved.fold(CorrectionCounts())(_.corrections)
            )
          )
      progress(
        s"${report.parts} part(s) · ${report.records} read · ${report.kept} kept · ${report.created} new · " +
          s"${report.matched} matched"
      )

    def full = report.kept >= limit || held()

    /** A catalogue — Histmersion's own or a third party's, alike: its parts in its order, each read once (a part read
      * before is skipped, unless the catalogue's parts are pages of a list that changes), a paged part followed to its
      * last page; every record checked by the host ([[PluginEvents.record]]) before the thresholds, the merge and the
      * store.
      */
    def run(cat: EventCatalogue): Unit =
      val c = client(PluginEvents.safe(cat.requestIntervalMs).getOrElse(1000))
      val h = PluginEvents.http(c)
      val q = PluginEvents.query(from, to, area)
      val read: String => Boolean =
        if PluginEvents.safe(cat.everyPartEachImport).contains(true) then _ => false else done
      PluginEvents.guard(Right(cat.parts(q))) match
        case Left(why)    => report = report.modify(_.failed).using(_ :+ s"parts: $why")
        case Right(parts) =>
          parts.distinct.foreach { first =>
            PluginEvents.readPart(cat, first, q, h, read, full) { (part, got) =>
              val recs = got.map(_.events.map(PluginEvents.record(provider, _)))
              recs.foreach(rs =>
                if rs.exists(_.isLeft) then
                  report = report.modify(_.failed).using(_ ++ rs.collect { case Left(w) => s"$part: $w" }.take(20))
              )
              handle(part, recs.map(_.collect { case Right(r) => r }))
            }
          }

    catalogue match
      case None =>
        report = report.copy(failed =
          report.failed :+ BuiltinCatalogues.resolve(provider, registered()).fold(identity, _ => provider)
        )
      case Some(c) => run(c)

    // the parts still to read: none for a catalogue whose parts are all read at every import
    val pending = catalogue match
      case Some(cat) if !req.dryRun && !PluginEvents.safe(cat.everyPartEachImport).contains(true) =>
        val now = EventProviders.partsIn(store.partsDone(provider), scope)
        PluginEvents
          .guard(Right(cat.parts(PluginEvents.query(from, to, area))))
          .fold(_ => 0, _.distinct.count(p => !now(p)))
      case _ => 0
    val out = report.copy(pending = pending, requests = clients.map(_.requests).sum + report.requests)
    scribe.info(s"events import: ${EventImportReport.summary(out)}")
    out

  /** Re-derive, blocking (the job runs it): every stored record of `provider` (all catalogues') read back, its dating
    * computed again (read again by its catalogue from the catalogue's own record where it keeps one, while the
    * catalogue is registered — [[com.histmersion.plugin.EventCatalogue.reread]]), and — not a dry run — written with
    * the events their catalogue created refreshed ([[EventImportService.save]], corrections by hand applied),
    * `rederive-batch` records a transaction.
    */
  def runRederive(
      provider: Option[String],
      dryRun: Boolean,
      progress: String => Unit
  ): EventRederiveReport =
    val (rows, undecodable) = store.records(provider)
    val present = presentYear()
    def prec(r: CatalogRecord) = EventPrecision.of(r.timeSpan, r.plusMinus)
    val again = rows.map { (eventId, eventKind, r) =>
      // read again by its catalogue where it re-reads (from the catalogue's own record), else derived from the
      // stored one as at the import
      val reread = registered().get(r.provider).flatMap(PluginEvents.reread(_, r.provider, r))
      val d = PluginEvents.derive(reread.getOrElse(r), limitsOf(r.provider, present))
      // a Wikidata item read in two classes keeps the more specific kind (V085 stored the last class's)
      val k = if r.provider == CatalogProvider.Wikidata then
        EventProviders.WikidataEvents.moreSpecific(classOrder, d.kind, eventKind)
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
    var counts = CorrectionCounts()
    if !dryRun then
      again.grouped(ec.rederiveBatch.max(1)).zipWithIndex.foreach { (batch, i) =>
        val s =
          store.save(batch.map((id, _, b, _) => b -> CatalogMerge.Placement.Existing(id, "re-derived")))
        updated += s.updated
        counts = counts + s.corrections
        progress(s"${(i + 1) * ec.rederiveBatch.max(1) min again.size} of ${again.size} record(s) re-derived")
      }
    val examples = changed.take(5).map { (id, a, b, _) =>
      def years(r: CatalogRecord) = r.timeSpan.outer.fold("undated")((x, y) => s"${YearFmt(x)}…${YearFmt(y)}")
      s"$id: ${years(a)} → ${years(b)}${EventPrecision.note(b.central, b.plusMinus, b.datingMethod).fold("")(n => s" ($n)")}, ${prec(b)}"
    } ++ Option.when(undecodable > 0)(s"$undecodable stored record(s) no longer decode — left alone").toList
    val out = EventRederiveReport(
      dryRun,
      provider.toList.ifEmpty((CatalogProvider.all ++ registered().keys).distinct),
      again.size,
      changed.size,
      again.count((_, a, _, reread) => a.raw.isDefined && reread),
      updated,
      EventCount.of(again.map((_, a, _, _) => prec(a))),
      EventCount.of(again.map((_, _, b, _) => prec(b))),
      again.count((_, _, b, _) => !EventPrecision.mapped(prec(b))),
      examples,
      counts
    )
    scribe.info(s"events rederive: ${EventRederiveReport.summary(out)}")
    out

  extension (l: List[String]) private def ifEmpty(d: => List[String]): List[String] = if l.isEmpty then d else l

  /** The labels and aliases of the Wikidata items among `recs`, in the configured languages. */
  private def withNames(recs: List[CatalogRecord], report: EventImportReport): List[CatalogRecord] =
    val ids = recs.flatMap(_.wikidata).distinct
    if ids.isEmpty then recs
    else
      val c = polite(ec.wikidata.apiIntervalMs)
      val names = ids
        .grouped(ec.wikidata.batch.max(1))
        .toList
        .flatMap { batch =>
          c.json(EventProviders.WikidataEvents.namesUrl(ec.wikidata.apiUrl, batch, ec.wikidata.languageList))
            .flatMap(EventProviders.WikidataEvents.names)
            .getOrElse(Map.empty)
            .toList
        }
        .toMap
      recs.map(r =>
        r.wikidata
          .flatMap(names.get)
          .fold(r)(ns => r.modify(_.names).setTo((r.names ++ ns).filterNot(_ == r.label).distinct))
      )
