package com.histmersion.server.events

import com.histmersion.core.*
import com.histmersion.db.{AttestationRepo, EventCatalogRepo, EventRepo, JobRepo, PlaceScope, SourceRepo}
import com.histmersion.server.Deps
import com.histmersion.server.media.{JavaMediaHttp, MediaHttp, Polite}

import java.util.concurrent.atomic.AtomicReference

/** Events from catalogues (#1449): the import (a job, dry run or not), the proposed links of an event
  * to the corpus and their confirmation, the leads, an event's context for its card, an article's
  * catalogue leads, and the refinement of Wikidata sites' types. Reached through `Handlers.catalog`,
  * so REST, MCP and the CLI share this one implementation. */
final class EventCatalogHandlers(deps: Deps, http: MediaHttp):

  def this(deps: Deps) = this(deps, JavaMediaHttp(deps.cfg.media.timeoutSeconds))

  private val ds  = deps.ds
  private val cfg = deps.cfg
  private val ec  = cfg.events

  /** One catalogue run at a time (a run's pace is per run; two would ask twice as fast). */
  private val busy = AtomicReference[Option[String]](None)

  private def invalid(m: String)  = ApiError(m, Some(Write.CodeInvalid))
  private def notFound(m: String) = ApiError(m, Some(Write.CodeNotFound))

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise
    * (User-Agent, retries) — and up to two minutes' Retry-After, which the query service asks for. */
  private def polite(intervalMs: Int): Polite =
    Polite(http, cfg.media.copy(requestIntervalMs = intervalMs,
      maxRetryAfterSeconds = cfg.media.maxRetryAfterSeconds.max(120)))

  /** Start `body` as a `jobs` row of `kind` on its own daemon thread (the media jobs' pattern). `write`
    * jobs take the maintenance guard; a dry run does not. */
  private def startJob(kind: String, describe: String, write: Boolean)(body: (JobInfo, String => Unit) => Either[String, String])
      : Either[ApiError, JobInfo] =
    def go: Either[ApiError, JobInfo] =
      if !busy.compareAndSet(None, Some(describe)) then
        Left(ApiError(s"an events run is already going (${busy.get.getOrElse("?")}) — see `histmersion jobs`"))
      else
        val job = JobRepo.create(ds, kind, None, None, detail = Some(JobDetail(events = Some(describe))))
        val t = Thread.ofPlatform().daemon(true).name(s"histmersion-$kind").unstarted { () =>
          try
            val _ = JobRepo.transition(ds, job.id, JobState.Queued, JobState.Running, Some("starting"))
            val out =
              try body(job, p => JobRepo.progress(ds, job.id, p))
              catch
                case _: InterruptedException => Left("interrupted")
                case e: Throwable =>
                  scribe.error(s"$kind job ${job.id} died: ${e.getMessage}", e)
                  Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
            val _ = out match
              case Right(summary) => JobRepo.transition(ds, job.id, JobState.Running, JobState.Done, Some(summary))
              case Left(why)      => JobRepo.transition(ds, job.id, JobState.Running, JobState.Failed, None, Some(why))
          finally busy.set(None)
        }
        t.start()
        Right(JobRepo.get(ds, job.id).getOrElse(job))
    if write then deps.maintenance.guard(kind)(go) else go

  // ── the import ──────────────────────────────────────────────────────────────────────────────

  def catalogues: List[CatalogProviderInfo] = CatalogProvider.infos

  def importJob(req: EventImportRequest): Either[ApiError, JobInfo] =
    for
      provider <- CatalogProvider.parse(req.provider).left.map(invalid)
      area     <- req.bbox.map(_.trim).filter(_.nonEmpty) match
                    case Some(b) => BBox.parse(b).toRight(invalid(s"--bbox '$b' is not minLon,minLat,maxLon,maxLat")).map(Some(_))
                    case None    => Right(None)
      job      <- startJob(EventCatalogHandlers.ImportJobKind,
                    s"${CatalogProvider.info(provider).fold(provider)(_.title)} events${if req.dryRun then " (dry run)" else ""}",
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
    val info  = CatalogProvider.info(provider)
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
        case Right(recs) =>
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
          val updated =
            if req.dryRun then 0
            else
              val s = EventCatalogRepo.save(ds, placed)
              EventCatalogRepo.markPart(ds, provider, part, recs.size)
              s.updated
          report = EventImportReport.combine(report, EventImportReport(provider, req.dryRun, recs.size, kept.size,
            created, merged, matched, updated, EventCount.of(kept.map(r => EventTaxonomy.canonical(r.kind))),
            EventCount.of(skipped), matchedIds.result().take(20), parts = 1))
      progress(s"${report.parts} part(s) · ${report.records} read · ${report.kept} kept · ${report.created} new · " +
        s"${report.matched} matched")

    def full = report.kept >= limit || deps.maintenance.active

    provider match
      case CatalogProvider.Gvp =>
        val c = client(ec.gvp.requestIntervalMs)
        // `var`s: the page walk
        var start = 0
        var total = Int.MaxValue
        while start < total && !full do
          val url = EventProviders.Gvp.url(ec.gvp.url, from, to, start, ec.gvp.pageSize)
          val got = c.json(url).flatMap(EventProviders.Gvp.parse)
          got.foreach((_, t) => total = t.getOrElse(start))
          handle(s"page:$start", got.map(_._1))
          if got.isLeft then total = start   // a page without an answer ends the walk (the next run resumes)
          start += ec.gvp.pageSize
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
    val pending = provider match
      case CatalogProvider.Wikidata if !req.dryRun =>
        val now = EventCatalogRepo.partsDone(ds, provider)
        ec.wikidata.classList.count((q, _) => !now(s"class:$q"))
      case _ => 0
    val out = report.copy(pending = pending, requests = clients.map(_.requests).sum + report.requests)
    scribe.info(s"events import: ${EventImportReport.summary(out)}")
    out

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
  private def passagesIn(citekey: String, names: List[String], body: => List[com.histmersion.core.Chunk]): List[EventPassage] =
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
      catalogue = EventCatalogRepo.catalogueRefs(ds, id),
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
          CatalogProvider.info(prov).fold(prov)(_.title)))

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
          if ask.nonEmpty then superCache ++= claims(ask, "P279") ++ ask.filterNot(superCache.contains).map(_ -> Nil)
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
