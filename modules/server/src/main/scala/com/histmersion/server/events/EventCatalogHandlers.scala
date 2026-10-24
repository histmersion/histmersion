package com.histmersion.server.events

import com.histmersion.pluginhost.{HttpTransport, JavaHttpTransport, Polite, PluginEvents}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{AttestationRepo, CorrectionRepo, EventCatalogRepo, EventRepo, JobRepo, PlaceScope, SourceRepo}
import com.histmersion.server.{BackgroundJob, Deps}



/** Events from catalogues (#1449): the import (a job, dry run or not), the proposed links of an event
  * to the corpus and their confirmation, the leads, an event's context for its card, an article's
  * catalogue leads, and the refinement of Wikidata sites' types. Reached through `Handlers.catalog`,
  * so REST, MCP and the CLI share this one implementation. */
final class EventCatalogHandlers(deps: Deps, http: HttpTransport, presentYear: () => Long):

  def this(deps: Deps, http: HttpTransport) = this(deps, http, () => java.time.Year.now(java.time.ZoneOffset.UTC).getValue.toLong)
  def this(deps: Deps) = this(deps, JavaHttpTransport(deps.cfg.events.timeoutSeconds))

  private val ds  = deps.ds
  private def cfg = deps.cfg
  private def ec  = cfg.events

  /** The configured Wikidata classes' kinds, the most specific first. */
  private def classOrder: List[String] = ec.wikidata.classList.map(_._2)

  /** One catalogue run at a time (a run's pace is per run; two would ask twice as fast). */
  private val slot = BackgroundJob.Slot()

  private def invalid(m: String)  = ApiError(m, Some(Write.CodeInvalid))
  private def notFound(m: String) = ApiError(m, Some(Write.CodeNotFound))

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise
    * (User-Agent, retries) — and up to two minutes' Retry-After, which the query service asks for. */
  private def polite(intervalMs: Int): Polite =
    PluginEvents.polite(http, cfg.media, intervalMs)

  /** Start `body` as a [[BackgroundJob]] of `kind`. `write` jobs take the maintenance guard; a dry run
    * does not. `body` answers the summary, or why the run failed. */
  private def startJob(kind: String, describe: String, write: Boolean)(body: (JobInfo, String => Unit) => Either[String, String])
      : Either[ApiError, JobInfo] =
    def go: Either[ApiError, JobInfo] =
      BackgroundJob.start(ds, kind, Some(JobDetail(events = Some(describe))), slot, "an events run",
        pins = deps.pins)((job, progress) =>
        BackgroundJob.Outcome.of(body(job, progress)))
    if write then deps.maintenance.guard(kind)(go) else go

  // ── the import ──────────────────────────────────────────────────────────────────────────────

  /** The catalogues the plugins of the calling thread's generation registered (built-in and third-party). */
  private def registered = deps.plugins.registries.eventCatalogues

  /** The limits of `provider`'s dating, the ONE rule of the import and of rederive: the present for
    * every catalogue, the Holocene floor for the GVP's, a plugin catalogue's own floor for its. */
  private def limitsOf(provider: String, present: Long): CatalogDating.Limits =
    if CatalogProvider.all.contains(provider) then EventProviders.limits(provider, present, ec.gvp.floorYear)
    else PluginEvents.limits(registered.get(provider), present)

  /** A catalogue's terms: a registered one's, else a built-in's (its records outlive its plugin). */
  private def infoOf(provider: String): Option[CatalogProviderInfo] = BuiltinCatalogues.info(provider, registered)

  def catalogues: List[CatalogProviderInfo] = BuiltinCatalogues.infos(registered)

  def importJob(req: EventImportRequest): Either[ApiError, JobInfo] =
    for
      provider <- BuiltinCatalogues.resolve(req.provider, registered).left.map(invalid)
      area     <- req.bbox.map(_.trim).filter(_.nonEmpty) match
                    case Some(b) => BBox.parse(b).toRight(invalid(s"--bbox '$b' is not minLon,minLat,maxLon,maxLat")).map(Some(_))
                    case None    => Right(None)
      job      <- startJob(EventCatalogHandlers.ImportJobKind,
                    s"${infoOf(provider).fold(provider)(_.title)} events${if req.dryRun then " (dry run)" else ""}",
                    write = !req.dryRun) { (job, progress) =>
                    val r = runImport(provider, req, area, progress)
                    JobRepo.get(ds, job.id).foreach(j =>
                      JobRepo.setDetail(ds, job.id, j.detail.getOrElse(JobDetail()).copy(eventImport = Some(r))))
                    Right(EventImportReport.summary(r))
                  }
    yield job

  /** One catalogue part read: its key and the records (or why not). */
  private type Fetched = Either[String, List[CatalogRecord]]

  /** The import itself — blocking; the job runs it. Part by part: read, keep what passes the
    * thresholds and the years, place each record ([[CatalogMerge.place]]) against what is stored and
    * what this run placed before, and (not a dry run) write the part and record it as read. */
  private[events] def runImport(provider: String, req: EventImportRequest, area: Option[BBox],
      progress: String => Unit): EventImportReport =
    val info  = infoOf(provider)
    val catalogue = registered.get(provider)
    val to    = req.to.getOrElse(ec.maxYear)
    val from  = req.from
    val limit = req.limit.filter(_ > 0).getOrElse(ec.defaultLimit)
    val th = EventProviders.Thresholds(ec.regionBox,
      req.minVei.getOrElse(ec.gvp.regionMinVei), req.minVei.getOrElse(ec.gvp.outsideMinVei),
      req.minMagnitude.getOrElse(ec.noaa.regionMinMagnitude), req.minMagnitude.getOrElse(ec.noaa.outsideMinMagnitude),
      area)
    if req.refresh && !req.dryRun then EventCatalogRepo.clearParts(ds, provider)
    val done = if req.dryRun then Set.empty[String] else EventCatalogRepo.partsDone(ds, provider)
    val stored0 = EventCatalogRepo.stored(ds)
    val preRun  = stored0.map(_.id).toSet
    // `var`s: the run's state across parts — what is placed so far, the report, the requests sent
    var known  = stored0
    var seen   = Set.empty[String]
    var report = EventImportReport(provider, req.dryRun, licence = info.fold("")(_.licence), citation = info.fold("")(_.citation))
    val clients = scala.collection.mutable.ListBuffer.empty[Polite]
    def client(ms: Int): Polite = { val p = polite(ms); clients += p; p }

    def handle(part: String, fetched: Fetched): Unit =
      fetched match
        case Left(why) => report = report.copy(failed = report.failed :+ s"$part: $why")
        case Right(read) =>
          // a catalogue's dating held inside the present (and the GVP's Holocene floor, a plugin's own);
          // a plugin's record dated from its stated year when it gives no span — as rederive does
          val lim  = limitsOf(provider, presentYear())
          val date: CatalogRecord => CatalogRecord =
            if CatalogProvider.all.contains(provider) then CatalogDating.clamp(_, lim) else PluginEvents.derive(_, lim)
          // an item read again in a later (less specific) class keeps the kind it was stored with
          val kinds = if provider == CatalogProvider.Wikidata && !req.dryRun then EventCatalogRepo.storedKinds(ds, provider) else Map.empty
          val recs = read.map(date).map(r =>
            kinds.get(r.key).fold(r)(k => r.copy(kind = EventProviders.WikidataEvents.moreSpecific(classOrder, k, r.kind))))
          val judged = recs.map { r =>
            val span = EventGeo.outer(r.timeSpan)
            val why =
              if span.exists(_._1 > to) then Left(s"after $to")
              else if from.exists(f => span.exists(_._2 < f)) then Left(s"before ${from.get}")
              else EventProviders.keep(r, th)
            r -> why
          }
          val room = (limit - report.kept).max(0)
          val kept0 = judged.collect { case (r, Right(_)) => r }
          val kept  = kept0.take(room)
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
              case _ => created += 1
            seen += p.eventId
          }
          val newEvents = placed.collect { case (r, CatalogMerge.Placement.New(id, _)) => (id, r) }.groupBy(_._1).toList
            .map((id, rs) => StoredEvent(id, rs.head._2.kind, rs.head._2.timeSpan, rs.head._2.lat, rs.head._2.lon,
              rs.head._2.wikidata, rs.map(_._2.key).toSet, catalogue = true, rs.head._2.label))
          known = known.filterNot(e => newEvents.exists(_.id == e.id)) ++ newEvents
          val saved =
            if req.dryRun then None
            else
              val s = EventCatalogRepo.save(ds, placed)
              EventCatalogRepo.markPart(ds, provider, part, recs.size)
              Some(s)
          report = EventImportReport.combine(report, EventImportReport(provider, req.dryRun, recs.size, kept.size,
            created, merged, matched, saved.fold(0)(_.updated), EventCount.of(kept.map(r => EventTaxonomy.canonical(r.kind))),
            EventCount.of(skipped), matchedIds.result().take(20), parts = 1,
            corrections = saved.fold(CorrectionCounts())(_.corrections)))
      progress(s"${report.parts} part(s) · ${report.records} read · ${report.kept} kept · ${report.created} new · " +
        s"${report.matched} matched")

    def full = report.kept >= limit || deps.maintenance.active

    /** One of the host's own catalogues, read as it always was. */
    def runHost(name: String): Unit = name match
      case CatalogProvider.Gvp =>
        val c = client(ec.gvp.requestIntervalMs)
        EventProviders.Gvp.windows(from.getOrElse(ec.minYear), to, ec.gvp.windowYears).foreach { (a, b) =>
          val part = s"years:$a..$b"
          if !full && !done(part) then
            handle(part, c.json(EventProviders.Gvp.url(ec.gvp.url, a, b, ec.gvp.pageSize)).flatMap(EventProviders.Gvp.parse)
              .flatMap((rs, total) => Either.cond(total.forall(_ <= rs.size), rs,
                s"${total.getOrElse(0)} eruptions in $a..$b, more than one request takes (events.gvp.page-size)")))
        }
      case CatalogProvider.Noaa =>
        val c = client(ec.noaa.requestIntervalMs)
        ec.noaa.datasetList.foreach { dataset =>
          var page  = 1
          var pages = 1
          while page <= pages && !full do
            val got = c.json(EventProviders.Noaa.url(ec.noaa.url, dataset, from, to, page, ec.noaa.pageSize))
              .flatMap(EventProviders.Noaa.parse(dataset, _))
            got.foreach((_, n) => pages = n)
            handle(s"$dataset:$page", got.map(_._1))
            if got.isLeft then pages = 0
            page += 1
        }
      case _ =>
        val c    = client(ec.wikidata.requestIntervalMs)
        val lang = (ec.wikidata.languageList :+ "en").distinct.mkString(",")
        ec.wikidata.classList.foreach { (qid, kind) =>
          val part = s"class:$qid"
          if !full && !done(part) then
            val q = EventProviders.WikidataEvents.sparql(qid, from, to, lang)
            handle(part, c.json(EventProviders.WikidataEvents.sparqlUrl(ec.wikidata.sparqlUrl, q))
              .flatMap(EventProviders.WikidataEvents.parse(_, kind)))
        }

    /** A plugin's catalogue: its parts in its order, each read once (a part read before is skipped, as
      * the built-ins' are), a paged part followed to its last page; every record checked by the host
      * ([[PluginEvents.record]]) before the same thresholds, merge and store as the built-ins'. */
    def runPlugin(cat: com.histmersion.plugin.EventCatalogue): Unit =
      val c = client(PluginEvents.safe(cat.requestIntervalMs).getOrElse(1000))
      val h = PluginEvents.http(c)
      val q = PluginEvents.query(from, to, area)
      PluginEvents.guard(Right(cat.parts(q))) match
        case Left(why) => report = report.copy(failed = report.failed :+ s"parts: $why")
        case Right(parts) =>
          parts.distinct.foreach { first =>
            PluginEvents.readPart(cat, first, q, h, done, full) { (part, got) =>
              val recs = got.map(_.events.map(PluginEvents.record(provider, _)))
              recs.foreach(rs => if rs.exists(_.isLeft) then
                report = report.copy(failed = report.failed ++ rs.collect { case Left(w) => s"$part: $w" }.take(20)))
              handle(part, recs.map(_.collect { case Right(r) => r }))
            }
          }

    catalogue match
      case None =>
        report = report.copy(failed = report.failed :+ BuiltinCatalogues.resolve(provider, registered).fold(identity, _ => provider))
      case Some(c: HostEventCatalogue) => runHost(c.name)
      case Some(c) => runPlugin(c)

    val pending = catalogue match
      case Some(cat) if !req.dryRun && !cat.isInstanceOf[HostEventCatalogue] =>
        val now = EventCatalogRepo.partsDone(ds, provider)
        PluginEvents.guard(Right(cat.parts(PluginEvents.query(from, to, area)))).fold(_ => 0, _.distinct.count(p => !now(p)))
      case _ => provider match
        case CatalogProvider.Gvp if !req.dryRun =>
          val now = EventCatalogRepo.partsDone(ds, provider)
          EventProviders.Gvp.windows(from.getOrElse(ec.minYear), to, ec.gvp.windowYears).count((a, b) => !now(s"years:$a..$b"))
        case CatalogProvider.Wikidata if !req.dryRun =>
          val now = EventCatalogRepo.partsDone(ds, provider)
          ec.wikidata.classList.count((q, _) => !now(s"class:$q"))
        case _ => 0
    val out = report.copy(pending = pending, requests = clients.map(_.requests).sum + report.requests)
    scribe.info(s"events import: ${EventImportReport.summary(out)}")
    out

  // ── re-derivation (no network) ──────────────────────────────────────────────────────────────

  def rederiveJob(req: EventRederiveRequest): Either[ApiError, JobInfo] =
    for
      provider <- req.provider.map(_.trim).filter(_.nonEmpty) match
                    case Some(p) =>
                      // no network: a built-in catalogue switched off since still has its records to re-derive
                      val n = BuiltinCatalogues.canonical(p)
                      if CatalogProvider.all.contains(n) then Right(Some(n))
                      else BuiltinCatalogues.resolve(p, registered).left.map(invalid).map(Some(_))
                    case None    => Right(None)
      job      <- startJob(EventCatalogHandlers.RederiveJobKind,
                    s"re-derive the dating of ${provider.fold("every catalogue's")(p => infoOf(p).fold(p)(_.title) + "'s")} " +
                      s"events${if req.dryRun then " (dry run)" else ""}", write = !req.dryRun) { (job, progress) =>
                    val r = runRederive(provider, req.dryRun, progress)
                    JobRepo.get(ds, job.id).foreach(j =>
                      JobRepo.setDetail(ds, job.id, j.detail.getOrElse(JobDetail()).copy(eventRederive = Some(r))))
                    Right(EventRederiveReport.summary(r))
                  }
    yield job

  /** Re-derive, blocking (the job runs it): every stored record of `provider` (all catalogues') read
    * back, its dating computed again ([[EventProviders.rederive]]: from the catalogue's own record where
    * it keeps one), and — not a dry run — written with the events their catalogue created refreshed
    * ([[EventCatalogRepo.save]], corrections by hand applied), `rederive-batch` records a transaction. */
  private[events] def runRederive(provider: Option[String], dryRun: Boolean, progress: String => Unit): EventRederiveReport =
    val (rows, undecodable) = EventCatalogRepo.records(ds, provider)
    val present = presentYear()
    def prec(r: CatalogRecord) = EventPrecision.of(r.timeSpan, r.plusMinus)
    val again = rows.map { (eventId, eventKind, r) =>
      // a built-in's record re-parsed from its own; a plugin's derived from the stored one as at the import
      val d =
        if CatalogProvider.all.contains(r.provider) then EventProviders.rederive(r, limitsOf(r.provider, present))
        else PluginEvents.derive(r, limitsOf(r.provider, present))
      // a Wikidata item read in two classes keeps the more specific kind (V085 stored the last class's)
      val k = if r.provider == CatalogProvider.Wikidata then EventProviders.WikidataEvents.moreSpecific(classOrder, d.kind, eventKind) else d.kind
      (eventId, r, d.copy(kind = k))
    }
    val changed = again.filter((_, a, b) =>
      (a.timeSpan, a.central, a.plusMinus, a.datingMethod, a.kind) != (b.timeSpan, b.central, b.plusMinus, b.datingMethod, b.kind))
    // `var`s: the counts of the write
    var updated = 0
    var counts  = CorrectionCounts()
    if !dryRun then
      again.grouped(ec.rederiveBatch.max(1)).zipWithIndex.foreach { (batch, i) =>
        val s = EventCatalogRepo.save(ds, batch.map((id, _, b) => b -> CatalogMerge.Placement.Existing(id, "re-derived")))
        updated += s.updated
        counts = counts + s.corrections
        progress(s"${(i + 1) * ec.rederiveBatch.max(1) min again.size} of ${again.size} record(s) re-derived")
      }
    val examples = changed.take(5).map { (id, a, b) =>
      def years(r: CatalogRecord) = EventGeo.outer(r.timeSpan).fold("undated")((x, y) => s"${YearFmt(x)}…${YearFmt(y)}")
      s"$id: ${years(a)} → ${years(b)}${EventPrecision.note(b.central, b.plusMinus, b.datingMethod).fold("")(n => s" ($n)")}, ${prec(b)}"
    } ++ Option.when(undecodable > 0)(s"$undecodable stored record(s) no longer decode — left alone").toList
    val out = EventRederiveReport(dryRun, provider.toList.ifEmpty((CatalogProvider.all ++ registered.keys).distinct), again.size, changed.size,
      again.count((_, a, _) => a.raw.isDefined && CatalogProvider.all.contains(a.provider)), updated,
      EventCount.of(again.map((_, a, _) => prec(a))), EventCount.of(again.map((_, _, b) => prec(b))),
      again.count((_, _, b) => !EventPrecision.mapped(prec(b))), examples, counts)
    scribe.info(s"events rederive: ${EventRederiveReport.summary(out)}")
    out

  extension (l: List[String]) private def ifEmpty(d: => List[String]): List[String] = if l.isEmpty then d else l

  // ── corrections by hand ─────────────────────────────────────────────────────────────────────

  /** `GET /api/events/corrections`: the corrections of catalogue events, conflicts first. */
  def corrections(status: Option[String], event: Option[String], limit: Option[Int], offset: Option[Int])
      : Either[ApiError, CorrectionList] =
    status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) if !CorrectionStatus.all.contains(s) =>
        Left(invalid(s"status '$s' is not ${CorrectionStatus.all.mkString(" | ")}"))
      case st =>
        Right(CorrectionRepo.list(ds, CorrectionTarget.Event, event.map(id => EventRepo.renamedTo(ds, id).getOrElse(id)),
          st, cfg.limits.page(limit, 50), offset.getOrElse(0)))

  /** The labels and aliases of the Wikidata items among `recs`, in the configured languages. */
  private def withNames(recs: List[CatalogRecord], report: EventImportReport): List[CatalogRecord] =
    val ids = recs.flatMap(_.wikidata).distinct
    if ids.isEmpty then recs
    else
      val c = polite(ec.wikidata.apiIntervalMs)
      val names = ids.grouped(ec.wikidata.batch.max(1)).toList.flatMap { batch =>
        c.json(EventProviders.WikidataEvents.namesUrl(ec.wikidata.apiUrl, batch, ec.wikidata.languageList))
          .flatMap(EventProviders.WikidataEvents.names).getOrElse(Map.empty).toList
      }.toMap
      recs.map(r => r.wikidata.flatMap(names.get).fold(r)(ns => r.copy(names = (r.names ++ ns).filterNot(_ == r.label).distinct)))

  // ── proposed links ──────────────────────────────────────────────────────────────────────────

  private def eventOrFail(id: String): Either[ApiError, EventDetail] =
    EventRepo.get(ds, EventRepo.renamedTo(ds, id).getOrElse(id)).toRight(notFound(s"No event '$id'"))

  /** The names of an event worth searching for. */
  private def namesOf(d: EventDetail): List[String] =
    val stored = EventCatalogRepo.tierAndNames(ds, d.event.id).map(_._2).getOrElse(Nil)
    EventMatcher.searchNames(d.event.label :: stored, ec.links.minNameChars)

  /** The passages of `citekey`'s body that name the event — a whole-word, case- and accent-insensitive
    * match of each name. */
  private def passagesIn(citekey: String, names: List[String], body: => List[com.histmersion.domain.Chunk]): List[EventPassage] =
    lazy val chunks = body
    names.flatMap { n =>
      val words = n.split("\\s+").filter(_.nonEmpty).map(w => java.util.regex.Pattern.quote(w))
      val rx = words.mkString("\\b", "\\s+", "\\b")
      PassageFinder.find(citekey, chunks, rx, regex = true, limit = 5, context = 160, maxMatches = 50,
        timeoutMs = cfg.limits.passages.regexTimeoutMs.toLong).toOption.toList.flatMap(_.passages).flatMap { h =>
        h.matches.map(m => EventPassage(h.offset + m.start, h.offset + m.end, h.text.substring(m.start, m.end), n, h.text))
      }
    }.distinctBy(_.start).sortBy(_.start)

  /** Compute and store an event's candidates. */
  private[events] def compute(d: EventDetail): EventLinkList =
    val w     = ec.links.weights
    val names = namesOf(d)
    val near = EventCatalogRepo.nearFindings(ds, d.event.id, w.km, w.years, 500)
      .map(f => EventMatcher.NearFinding(f.id, f.citekey, f.km, f.yearGap))
    val named = names.flatMap(n => EventCatalogRepo.sourcesNaming(ds, n, ec.links.sourcesPerName)).distinct
      .map(ck => ck -> passagesIn(ck, names, SourceRepo.bodyChunks(ds, ck))).filter(_._2.nonEmpty).toMap
    val cands = EventMatcher.candidates(d.event.id, near, named, w)
    EventCatalogRepo.saveCandidates(ds, d.event.id, cands)
    EventLinkList(d.event.id, d.event.label, EventCatalogRepo.candidates(ds, d.event.id))

  /** An event's candidates — computed on the first ask, stored after. */
  def links(id: String): Either[ApiError, EventLinkList] =
    eventOrFail(id).map { d =>
      if EventCatalogRepo.isChecked(ds, d.event.id) then
        EventLinkList(d.event.id, d.event.label, EventCatalogRepo.candidates(ds, d.event.id))
      else compute(d)
    }

  /** Recompute, confirm or reject. A source is confirmed only from a passage of its body that names
    * the event, read NOW (not the stored evidence): the attestation carries that passage's quote and
    * offsets. A source with none is refused, with why. */
  def applyLinks(id: String, req: EventLinksRequest): Either[ApiError, EventLinksResult] =
    deps.maintenance.guard("event_links") {
      eventOrFail(id).map { d =>
        val eid = d.event.id
        if req.recompute then compute(d)
        val names = namesOf(d)
        val existing = AttestationRepo.list(ds, None, Some(AttestationTarget.Event), Some(eid), 1000, 0).attestations
        val (attested, refused) = req.confirm.map(_.trim).filter(_.nonEmpty).distinct.foldLeft((List.empty[Attestation], List.empty[String])) {
          case ((ok, no), raw) =>
            SourceRepo.canonical(ds, raw) match
              case None => (ok, no :+ s"$raw: no such source")
              case Some(ck) =>
                if existing.exists(_.citekey == ck) then
                  val _ = EventCatalogRepo.setStatus(ds, eid, ck, EventLinkStatus.Confirmed)
                  (ok, no :+ s"$ck: already attests it")
                else
                  EventMatcher.attestationPassage(passagesIn(ck, names, SourceRepo.bodyChunks(ds, ck))) match
                    case None => (ok, no :+ s"$ck: no passage of its text names the event (${names.mkString(", ")}) — not attested")
                    case Some(p) =>
                      val a = AttestationRepo.create(ds, EventMatcher.attestation(eid, ck, p))
                      if !EventCatalogRepo.setStatus(ds, eid, ck, EventLinkStatus.Confirmed) then
                        EventCatalogRepo.saveCandidates(ds, eid, EventCatalogRepo.candidates(ds, eid).filter(_.status == EventLinkStatus.Proposed) :+
                          EventLinkCandidate(eid, ck, 0, EventLinkStatus.Proposed, None, Nil, List(p), List("confirmed by hand")))
                        val _ = EventCatalogRepo.setStatus(ds, eid, ck, EventLinkStatus.Confirmed)
                      (ok :+ a, no)
        }
        val rejected = req.reject.map(_.trim).filter(_.nonEmpty).distinct.filter(ck =>
          EventCatalogRepo.setStatus(ds, eid, SourceRepo.canonical(ds, ck).getOrElse(ck), EventLinkStatus.Rejected))
        EventLinksResult(EventLinkList(eid, d.event.label, EventCatalogRepo.candidates(ds, eid)), attested, refused, rejected)
      }
    }

  /** The candidates of many events, as a job: those never computed (or all), `links.batch` at a time. */
  def candidatesJob(req: EventCandidatesRequest): Either[ApiError, JobInfo] =
    req.event.map(_.trim).filter(_.nonEmpty) match
      case Some(one) => eventOrFail(one).flatMap(_ => startJob(EventCatalogHandlers.CandidatesJobKind, s"candidates of $one", write = true) { (_, _) =>
          eventOrFail(one).map(d => s"${compute(d).candidates.size} candidate(s) for ${d.event.id}").left.map(_.message)
        })
      case None =>
        startJob(EventCatalogHandlers.CandidatesJobKind,
          s"candidate links of ${if req.all then "every event" else "the events not checked yet"}", write = true) { (_, progress) =>
          val ids = EventCatalogRepo.eventsToCheck(ds, req.all, req.limit.filter(_ > 0).getOrElse(Int.MaxValue))
          // `var`s: the counts of the walk
          var n, found = 0
          ids.grouped(ec.links.batch.max(1)).takeWhile(_ => !deps.maintenance.active).foreach { batch =>
            batch.foreach { id =>
              EventRepo.get(ds, id).foreach(d => found += compute(d).candidates.size)
              n += 1
            }
            progress(s"$n of ${ids.size} event(s) checked · $found candidate(s)")
          }
          Right(s"$n event(s) checked · $found candidate(s)")
        }

  // ── leads ───────────────────────────────────────────────────────────────────────────────────

  private def leadOf(r: com.histmersion.db.LeadRow): Option[EventLead] =
    EventLeads.of(LeadFacts(r.id, r.label, r.kind, TimeSpan(r.notBefore, r.beginBy, r.endFrom, r.notAfter), r.tier,
      r.vei, r.magnitude, r.findingsNear, ec.leads.km, ec.leads.years, r.proposed, r.attesting))

  /** The leads of the events `q` selects — catalogue ones by default, with the catalogue's context. */
  def leads(q: Api.EventQuery, scope: Filter => PlaceScope): EventLeadList =
    val f = q.filter
    val where = EventRepo.listWhere(f, scope(f), q.text, q.kind, q.historicity, q.placeId,
      EventRepo.Narrow(q.tier.orElse(Some(EventTier.Catalogue)), context = q.context.getOrElse(true),
        q.area.flatMap(BBox.parse)))
    val (total, rows) = EventCatalogRepo.leadRows(ds, where, ec.leads.km, ec.leads.years,
      cfg.limits.page(Some(f.limit), 50), f.offset)
    EventLeadList(total, rows.flatMap(leadOf))

  // ── an event's card ─────────────────────────────────────────────────────────────────────────

  /** The context an event's card shows: its tier and names, the catalogues that list it, the sources
    * attesting it and the articles citing them, the findings near it, the proposed sources (stored —
    * a card never computes them) and its lead. */
  def context(d: EventDetail): EventDetail =
    val id = d.event.id
    val (tier, names, _) = EventCatalogRepo.tierAndNames(ds, id).getOrElse((d.event.tier, Nil, None))
    val sources = EventCatalogRepo.attestingSources(ds, id)
    val lead = EventCatalogRepo.leadRows(ds, com.histmersion.db.Sql.of("e.id = ?", com.histmersion.db.Param.S(id)),
      ec.leads.km, ec.leads.years, 1, 0)._2.flatMap(leadOf)
    d.copy(tier = tier, names = names.filterNot(_ == d.event.label),
      catalogue = EventCatalogRepo.catalogueRefs(ds, id).map(r =>
        // a plugin's catalogue: its terms come from the plugin, which the store does not know
        if r.licence.nonEmpty then r
        else infoOf(r.provider).fold(r)(i => r.copy(title = i.title, licence = i.licence, citation = i.citation))),
      sources = sources,
      findings = EventCatalogRepo.nearFindings(ds, id, ec.links.km, ec.links.years, ec.links.nearFindings),
      articles = EventCatalogRepo.articlesCiting(ds, sources.map(_.citekey), 10),
      candidates = EventCatalogRepo.candidates(ds, id).filter(_.status == EventLinkStatus.Proposed),
      leads = lead)

  /** The catalogue events an article's period and region hold that its body never names. */
  def articleLeads(body: String, citekeys: List[String], period: Option[Period]): List[ArticleEventLead] =
    EventCatalogRepo.articleCandidates(ds, citekeys, period.map(p => (p.start, p.end)), ec.leads.articleKm,
      ec.leads.largeVei, ec.leads.largeMagnitude, ec.leads.articleLimit * 3)
      .filterNot((_, label, _, _, names, _, _, _, _) => ArticleEventLead.mentioned(body, label :: names))
      .take(ec.leads.articleLimit)
      .map((id, label, kind, ts, _, vei, mag, n, prov) =>
        ArticleEventLead(id, label, EventTaxonomy.canonical(kind), EventLeads.years(ts), EventLeads.size(vei, mag), n,
          infoOf(prov).fold(prov)(_.title)))

  // ── site types ──────────────────────────────────────────────────────────────────────────────

  def refineSiteTypes(req: SiteTypesRequest): Either[ApiError, JobInfo] =
    startJob(EventCatalogHandlers.SiteTypesJobKind,
      s"site types of the Wikidata sites on the map${if req.dryRun then " (dry run)" else ""}", write = !req.dryRun) { (_, progress) =>
      Right(SiteTypesReport.summary(runSiteTypes(req, progress)))
    }

  private[events] def runSiteTypes(req: SiteTypesRequest, progress: String => Unit): SiteTypesReport =
    val started = System.currentTimeMillis()
    if req.dryRun then SiteTypesReport(dryRun = true, pending = EventCatalogRepo.countSitesToType(ds, req.refresh, None))
    else
      val c   = polite(ec.wikidata.apiIntervalMs)
      val cap = req.limit.filter(_ > 0).getOrElse(Int.MaxValue)
      val api = cfg.media.wikidata.apiUrl
      val superCache = scala.collection.mutable.Map.empty[String, List[String]]
      def claims(ids: List[String], prop: String): Map[String, List[String]] =
        ids.grouped(ec.siteTypes.batch.max(1)).toList.flatMap { b =>
          c.json(Wikidata.entitiesUrl(api, b, cfg.media.wikidata.maxlag))
            .flatMap(EventProviders.WikidataEvents.claims(_, prop)).getOrElse(Map.empty).toList
        }.toMap
      /** The superclasses of `classes` within `depth` P279 steps, nearest first (cached per run). */
      def supers(classes: List[String]): List[String] =
        val unknown = classes.filterNot(SiteTypes.wikidataClasses.contains)
        (1 to ec.siteTypes.superclassDepth.max(0)).foldLeft((unknown, List.empty[String])) { case ((frontier, acc), _) =>
          val ask = frontier.filterNot(superCache.contains).distinct
          if ask.nonEmpty then
            val got = claims(ask, "P279")
            superCache ++= ask.map(q => q -> got.getOrElse(q, Nil))
          val next = frontier.flatMap(q => superCache.getOrElse(q, Nil)).distinct.filterNot(acc.contains)
          (next.filterNot(SiteTypes.wikidataClasses.contains), acc ++ next)
        }._2
      // `var`s: the walk
      var report = SiteTypesReport(dryRun = false)
      var batch  = EventCatalogRepo.sitesToType(ds, ec.siteTypes.batch.min(cap), req.refresh, Some(started))
      while batch.nonEmpty && report.asked < cap && !deps.maintenance.active do
        val p31 = claims(batch.map(_._2), "P31")
        val answered = batch.filter((_, q, _) => p31.contains(q))
        val types = answered.map { (pid, q, cls) =>
          val own = p31.getOrElse(q, Nil)
          pid -> SiteTypes.wikidata(own, supers(own), cls)
        }
        EventCatalogRepo.setSiteTypes(ds, types)
        report = report.copy(asked = report.asked + answered.size,
          typed = EventCount.add(report.typed, EventCount.of(types.flatMap(_._2))),
          untyped = report.untyped + types.count(_._2.isEmpty),
          failed = report.failed ++ Option.when(answered.isEmpty)(s"no answer for ${batch.head._2}…").toList)
        progress(s"${report.asked} site(s) asked · ${c.requests} request(s)")
        batch = if answered.isEmpty then Nil
                else EventCatalogRepo.sitesToType(ds, ec.siteTypes.batch.min((cap - report.asked).max(0)), req.refresh, Some(started))
      report.copy(pending = EventCatalogRepo.countSitesToType(ds, req.refresh, Some(started)), requests = c.requests)

object EventCatalogHandlers:
  val ImportJobKind     = "events-import"
  val CandidatesJobKind = "events-candidates"
  val SiteTypesJobKind  = "site-types"
  val RederiveJobKind   = "events-rederive"
