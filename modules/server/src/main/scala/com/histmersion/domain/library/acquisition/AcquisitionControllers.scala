package com.histmersion.domain.library.acquisition

import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.ingest.{FetchedCopy, IngestJobs, IngestMeta}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.Pinning
import com.histmersion.util.ocr.Spool

import com.histmersion.domain.library.Citekey

import com.histmersion.pluginhost.{FetchChain, OaFetcher}
import com.histmersion.domain.library.acquisition.WorkLookup

import com.histmersion.api.{ApiError, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Acquire.Ident

import hearth.kindlings.optics.syntax.*
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  Acquire,
  ArchiveFile,
  ArchiveImportRequest,
  ArchiveItem,
  Assessment,
  AssessRequest,
  CandidateList,
  CandidateMark,
  CandidateMarked,
  Candidates,
  CandidateSweep,
  CandidateSweepRequest,
  DiscoverResult,
  Edge,
  FollowupsRequest,
  FollowupsResult,
  IngestReport,
  IngestRequest,
  IngestStep,
  MetadataPatch,
  PdfInfo,
  RefRow,
  RefsRequest,
  RefsResult,
  RetractionNotice,
  RetractionsUpdate,
  SourcePatch,
  UpdateNotice,
  WikirefsResult,
  WorkRef
}
import com.histmersion.api.library.of

/** Every acquisition & assessment operation (stream 2.6): discovery, a paper's references and follow-ups, its
  * assessment, the Retraction Watch mirror, an ingest by identifier, archive.org's import, the goals' candidates. REST
  * routes and MCP tools both call these; the CLI reaches them over HTTP.
  *
  * Everything network-facing arrives as a trait, so a test can drive the whole surface from captured fixtures.
  * Everything decision-making is a pure function in [[Acquire]]. The writes go through the library's services, and
  * every one is behind the maintenance lock.
  */
final class AcquisitionControllers(
    sources: SourceService,
    assessments: AssessmentService,
    candidateStore: CandidateService,
    queues: QueueService,
    spool: () => Spool,
    cfg: () => AppConfig,
    maintenance: WriteGate,
    pins: Pinning,
    goals: com.histmersion.domain.automation.goals.GoalService,
    articles: ArticleCitations,
    // by name: the server passes the acquisition node's clients, read at each use, so a reload that
    // changed `acquire.*` or the plugins (the chain) takes effect without rebuilding this
    discovery: => DiscoveryClient,
    citations: => CitationsClient,
    oa: => OaFetcher,
    wiki: => WikiClient,
    retractionWatch: => RetractionWatchClient,
    jobs: IngestJobs,
    indexes: => List[ScholarlyIndex] = Nil,
    alerts: => CitationAlerts = NoAlerts,
    generation: => String = ""
):
  private def acq = cfg().acquire

  // The restore lock (plan §1.10) covers every acquisition WRITE — `assess --apply` patches a status, `followups` adds
  // edges, `ingest`/`archive import` create sources, the Retraction Watch refresh replaces a whole table: a source
  // created while `pg_restore --clean` runs is a source in a schema being dropped.

  /** The Retraction Watch refresh runs in the background (see [[updateRetractions]]) and at most ONE may be in flight.
    * Per INSTANCE, not per process: the handler is constructed once per server, and a test run holds several servers in
    * one JVM — a process-global flag let one server's refresh block another's and, worse, made one server report the
    * other's error.
    */
  private val rwRunning = java.util.concurrent.atomic.AtomicBoolean(false)
  private val rwError = java.util.concurrent.atomic.AtomicReference[Option[String]](None)

  private def err(msg: String): ApiError = ApiError(msg)

  // ── deterministic new-source search (#3761) ──────────────────────────────────────────────────

  private val search =
    CandidateSearch(candidateStore, sources, goals, articles, queues, cfg().acquire.candidates, indexes, alerts)

  /** A goal's (or every goal's) candidates in one state (`new` by default), with the counts per state. */
  def candidates(
      goal: Option[String],
      state: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ): Either[ApiError, CandidateList] =
    val st = state.map(_.trim).filter(_.nonEmpty).getOrElse(Candidates.State.New.wire)
    Candidates.State
      .parse(st)
      .toRight(
        ApiError(
          s"unknown state '$st' (${Candidates.State.values.map(_.wire).mkString(", ")})",
          Some(Write.CodeInvalid)
        )
      )
      .map { s =>
        val g = goal.map(_.trim).filter(_.nonEmpty)
        val n = cfg().limits.page(limit, cfg().limits.search)
        val from = offset.getOrElse(0).max(0)
        val page = candidateStore.list(g, s.wire, n + 1, from)
        CandidateList(g, s.wire, page.take(n), candidateStore.counts(g), from, Option.when(page.size > n)(from + n))
      }

  /** Read a goal's watches from their watermarks and store what is new (no model). */
  def sweepCandidates(r: CandidateSweepRequest): Either[ApiError, CandidateSweep] =
    if r.dryRun then search.sweep(r.goal.trim, dryRun = true)
    else maintenance.guard("candidates/sweep")(search.sweep(r.goal.trim, dryRun = false))

  def markCandidates(m: CandidateMark): Either[ApiError, CandidateMarked] =
    maintenance.guard("candidates/mark"):
      Candidates.State
        .parse(m.state)
        .toRight(
          ApiError(
            s"unknown state '${m.state}' (${Candidates.State.values.map(_.wire).mkString(", ")})",
            Some(Write.CodeInvalid)
          )
        )
        .map(s => CandidateMarked(s.wire, candidateStore.mark(m.ids.distinct, s.wire)))

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  /** OpenAlex search, with each hit marked `inDb` so a queue-builder can skip what we already have. */
  def discover(
      q: String,
      since: Option[Int],
      sort: Option[String],
      limit: Option[Int],
      notInDb: Boolean
  ): Either[ApiError, DiscoverResult] =
    val query = q.trim
    if query.isEmpty then Left(err("give a search query"))
    else
      discovery
        .search(query, since, sort, cfg().limits.page(limit, acq.defaults.searchLimit.value))
        .left
        .map(err)
        .map { works =>
          // ONE index for the whole page: `mark` used to read `lower(doi) -> citekey` for the entire
          // corpus once per RESULT ROW, i.e. 25 full-table scans for one search.
          val index = sources.doiIndex
          val marked = works.map(mark(_, index))
          val kept = if notInDb then marked.filterNot(_.inDb) else marked
          DiscoverResult(query, kept.size, kept)
        }

  def meta(id: String): Either[ApiError, WorkRef] =
    Acquire.parseIdent(id).left.map(err).flatMap {
      case Ident.Doi(doi) => discovery.meta(doi).left.map(err).map(mark(_, sources.doiIndex))
      case Ident.Arxiv(a) =>
        discovery.meta(arxivDoi(a)).left.map(err).map(mark(_, sources.doiIndex))
      case Ident.Citekey(k) =>
        sources.load(k).toRight(Write.notFound(k)).flatMap { s =>
          s.doi
            .toRight(err(s"'$k' has no DOI recorded — nothing to look up upstream"))
            .flatMap(discovery.meta(_).left.map(err))
            .map(mark(_, sources.doiIndex))
        }
    }

  def wikirefs(url: String): Either[ApiError, WikirefsResult] =
    wiki.refs(url).left.map(err).map { r =>
      val index = sources.doiIndex
      r.modify(_.known).setTo(r.dois.filter(index.contains))
    }

  /** Fill in `inDb` and keep the citekey the corpus would use. `index` is passed IN so a caller marking a whole page
    * reads the DOI index once.
    */
  private def mark(w: WorkRef, index: Map[String, String]): WorkRef =
    val byDoi = w.doi.flatMap(index.get)
    // the generated key, or the pre-#878 key a record with an accented author may still carry
    val keys = w.citekey.toList ++
      Option.when(w.citekey.isDefined)(Acquire.legacyCitekey(w.authors, w.year, w.title)).toList
    val byKey = keys.distinct.find(sources.exists)
    val resolved = byDoi.orElse(byKey)
    w.modify(_.inDb).setTo(resolved.isDefined).modify(_.citekey).setTo(resolved.orElse(w.citekey))

  /** The generated key `generated`, unless no record has it and one has the pre-#878 key of the same metadata — then
    * that one, so a re-import lands on the existing record, not a duplicate.
    */
  private def stored(generated: String, authors: List[String], year: Option[Int], title: Option[String]): String =
    val legacy = Acquire.legacyCitekey(authors, year, title)
    if legacy != generated && !sources.exists(generated) && sources.exists(legacy) then legacy
    else generated

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  /** The deposited reference list, with each entry resolved to a stored source where possible. `apply` writes those as
    * `references` edges out of the target.
    */
  def refs(req: RefsRequest): Either[ApiError, RefsResult] =
    target(req.id).flatMap { t =>
      t.doi.toRight(err(s"no DOI for '${req.id}' — a reference list needs one")).flatMap { doi =>
        discovery.references(doi).left.map(err).map { (backend, rows) =>
          val resolved = resolveRefs(rows)
          val added =
            if !req.apply then 0
            else
              t.citekey match
                case None      => 0
                case Some(src) =>
                  sources.addEdges(
                    resolved.flatMap(_.citekey).distinct.filter(_ != src).map(Edge(src, _, "references")),
                    Caller.Local
                  )
          RefsResult(req.id, Some(doi), backend, resolved.size, resolved.count(_.citekey.isDefined), added, resolved)
        }
      }
    }

  /** Match a reference to a stored source: by DOI first, then by the citekey the metadata would generate, then by
    * unique citekey PREFIX — the same widening `resolveEdges` does for short edge targets (`heggarty2023` →
    * `heggarty2023_language`).
    */
  private def resolveRefs(rows: List[RefRow]): List[RefRow] =
    val index = sources.doiIndex
    val citekeys = sources.citekeys
    rows.map { r =>
      val byDoi = r.doi.flatMap(index.get)
      // the current key first, then the pre-#878 one (accented letters dropped) older records carry
      val guesses = Acquire.citekeyCandidates(r.authors, r.year, r.title)
      val byKey = guesses.find(citekeys.contains)
      val byPrefix = guesses.iterator
        .map { guess =>
          val stem = guess.takeWhile(_ != '_')
          val hits = citekeys.filter(k => k == stem || k.startsWith(stem + "_"))
          Option.when(hits.size == 1)(hits.head)
        }
        .collectFirst { case Some(k) => k }
      r.modify(_.citekey).setTo(byDoi.orElse(byKey).orElse(byPrefix))
    }

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  /** `store = false` (an agent's dry run, #992): the verdict is computed but not recorded. */
  private def assessNow(req: AssessRequest, store: Boolean = true): Either[ApiError, Assessment] =
    target(req.id).flatMap { t =>
      t.doi.toRight(err(s"no DOI for '${req.id}' — an assessment needs one")).map { doi =>
        val rw = assessments.retractionsFor(doi)
        val updates = discovery.updates(doi).getOrElse(Nil)
        val work = discovery.meta(doi).toOption
        val stored = t.citekey.flatMap(sources.load)
        val cited = work.flatMap(_.citedByCount).orElse(stored.flatMap(_.citedByCount))
        val year = work.flatMap(_.year).orElse(stored.flatMap(_.year))
        val d = acq.defaults
        val verdict = Acquire.verdict(
          rw,
          updates,
          work.exists(_.isRetracted),
          cited,
          year,
          d.wellCitedAt.value,
          d.lowCitedAt.value,
          d.staleBefore.value
        )

        val checkedAt = t.citekey.filter(_ => store).map { ck =>
          assessments.record(
            AssessmentRecord(
              ck,
              verdict,
              verdict == Acquire.Verdicts.Retracted,
              noticesJson(rw, updates),
              cited,
              stored.flatMap(_.influentialCitations)
            )
          )
        }
        val decision = for
          ck <- t.citekey if req.apply
          cur <- stored
          d <- Acquire.statusDecision(verdict, cur.status, req.forceStatus)
        yield ck -> d
        val applied = decision.flatMap {
          case (ck, Right(status)) =>
            setStatus(ck, status).toOption
              .map(_ => status.wire)
          case (_, Left(_)) => None
        }
        val kept = decision.flatMap(_._2.left.toOption)

        Assessment(
          target = req.id,
          doi = Some(doi),
          citekey = t.citekey,
          year = year,
          citedByCount = cited,
          influentialCitations = stored.flatMap(_.influentialCitations),
          isRetracted = verdict == Acquire.Verdicts.Retracted,
          retractionWatch = rw,
          crossrefUpdates = updates,
          flagged = Acquire.flagged(verdict),
          verdict = verdict,
          recommendation = Acquire.recommendation(verdict, cited),
          applied = applied,
          checkedAt = checkedAt,
          kept = kept
        )
      }
    }

  /** The notices, verbatim, so a later reader sees WHY a verdict was reached without asking Crossref again.
    */
  private def noticesJson(rw: List[RetractionNotice], up: List[UpdateNotice]): String =
    Json.obj("retractionWatch" -> rw.asJson, "crossrefUpdates" -> up.asJson).noSpaces

  /** Refresh the local Retraction Watch mirror — our ground truth, so an assessment never depends on a third party
    * being up at the moment somebody asks.
    *
    * The work is ~30 MB of CSV and ~60 000 rows, which does not fit in one HTTP request (the same constraint that made
    * `reindex` resumable), so this STARTS the refresh on a virtual thread and returns immediately; callers poll
    * [[retractionsStatus]]. The two fields of mutable state below are the price of that: a flag so two callers cannot
    * run it twice, and the last error so a failed refresh is visible instead of silent.
    */
  private def updateRetractionsNow(): Either[ApiError, RetractionsUpdate] =
    if !rwRunning.compareAndSet(false, true) then Right(retractionsStatus)
    else
      rwError.set(None)
      // on the generation (database, clients) the request was pinned to
      val work = pins.carry(try
        retractionWatch.csv() match
          case Left(e) =>
            scribe.warn(s"acquire: Retraction Watch refresh failed — $e")
            rwError.set(Some(e))
          case Right(csv) =>
            val n = assessments.replaceRetractionWatch(Acquire.parseRetractionWatch(csv))
            scribe.info(s"acquire: Retraction Watch refreshed — $n row(s)")
      catch
        case e: Throwable =>
          scribe.error(s"acquire: Retraction Watch refresh crashed: ${e.getMessage}", e)
          rwError.set(Some(e.getMessage))
      finally rwRunning.set(false))
      val _ = Thread.ofVirtual().name("retraction-watch-refresh").start(() => work.run())
      Right(retractionsStatus.modify(_.running).setTo(true))

  /** How the mirror is doing: how many rows it holds, whether a refresh is in flight, and the last failure if there was
    * one.
    */
  def retractionsStatus: RetractionsUpdate =
    RetractionsUpdate(
      assessments.retractionCount,
      acq.retractionWatch.csvUrl,
      running = rwRunning.get,
      error = rwError.get
    )

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  private def followupsNow(req: FollowupsRequest): Either[ApiError, FollowupsResult] =
    target(req.id).flatMap { t =>
      t.doi
        .toRight(err(s"no DOI for '${req.id}' — the citation graph is keyed by DOI"))
        .flatMap { doi =>
          val work = discovery.meta(doi).getOrElse(WorkRef(doi = Some(doi)))
          val cap = cfg().limits.page(req.limit, acq.defaults.followupsLimit.value)
          citations.citing(work, req.since, cap).left.map(err).map { (backend, rows) =>
            val index = sources.doiIndex
            val enriched = Acquire.rankFollowups(rows).map { r =>
              r.modify(_.proposedKind)
                .setTo(Acquire.classify(r.intents, r.contexts, r.isInfluential, r.year, work.year))
                .modify(_.citekey)
                .setTo(r.doi.flatMap(index.get))
            }
            // an edge can only start at a source we actually hold: the citing paper must be in the
            // corpus, and so must the target
            val added = (req.apply, t.citekey) match
              case (true, Some(dst)) =>
                sources.addEdges(
                  enriched.flatMap(r => r.citekey.filter(_ != dst).map(src => Edge(src, dst, r.proposedKind))),
                  Caller.Local
                )
              case _ => 0
            FollowupsResult(req.id, Some(doi), backend, enriched.size, added, enriched)
          }
        }
    }

  // ── ingest by identifier ────────────────────────────────────────────────────────────────────

  /** `histmersion ingest <doi|arxiv:id>`: metadata → legal OA PDF → the ingest job → reference edges → assessment.
    * Every step is reported, including the ones that failed, because "no OA copy" is a normal outcome that the user
    * answers with a manual drop-in.
    */
  private def ingestNow(req: IngestRequest, path: IngestPath): Either[ApiError, IngestReport] =
    Acquire.parseIdent(req.id).left.map(err).flatMap { ident =>
      val steps = List.newBuilder[IngestStep]

      val (doi, arxiv) = ident match
        case Ident.Doi(d)     => (Some(d), req.arxiv.flatMap(Acquire.normalizeArxiv))
        case Ident.Arxiv(a)   => (Some(arxivDoi(a)), Some(a))
        case Ident.Citekey(k) => (sources.load(k).flatMap(_.doi), req.arxiv.flatMap(Acquire.normalizeArxiv))

      val work = doi.flatMap(discovery.meta(_).toOption)
      work match
        case Some(w) =>
          steps += IngestStep(
            "metadata",
            true,
            s"${w.title.getOrElse("(untitled)")} (${w.year.map(_.toString).getOrElse("?")}), " +
              s"${w.citedByCount.getOrElse(0)} citation(s)"
          )
        case None => steps += IngestStep("metadata", false, s"no upstream record for ${doi.getOrElse(req.id)}")

      val explicit = req.citekey.map(_.trim).filter(_.nonEmpty)
      val existing = ident match
        case Ident.Citekey(k) => Some(k)
        case _                => None
      val citekey = explicit
        .orElse(work.flatMap(w => w.citekey.map(stored(_, w.authors, w.year, w.title))))
        .orElse(existing)

      // METADATA GUARD. Without a title from an upstream we have nothing to name the source after,
      // and inventing one from the DOI produces exactly the `anonnd`-style junk entry the Calibre
      // guard exists to prevent. A typo'd or unregistered DOI must be an actionable error, not a row.
      if work.flatMap(_.title).isEmpty && citekey.isEmpty then
        Left(
          err(
            s"no upstream record for '${req.id}' (OpenAlex and Crossref both miss it) — check the " +
              "identifier, or pass --citekey to create the source anyway and fill the metadata in by hand"
          )
        )
      else
        // the citekey becomes a spool directory name, and it can come from the CALLER (--citekey)
        // or from an upstream record's title — neither is trustworthy as a path segment.
        Citekey.of(citekey.get).flatMap(ck => ingestWith(doi, arxiv, work, ck.value, steps, req.refetch, path))
    }

  /** The acquisition item that names `doi`, else a new browser download with `text`; and whether it is new. */
  private def acquireOnce(doi: String, text: String): (Int, Boolean) =
    queues.naming(Queue.Kind.Acquire, doi).headOption match
      case Some(existing) => (existing.id, false)
      case None           =>
        (
          queues.add(Queue.Kind.Acquire, Queue.QueueAdd(text, topic = Some(Acquire.BrowserDownloadTopic))).id,
          true
        )

  /** The rest of an ingest, once we know what the source will be CALLED. */
  private def ingestWith(
      doi: Option[String],
      arxiv: Option[String],
      work: Option[WorkRef],
      citekey: String,
      steps: scala.collection.mutable.Builder[IngestStep, List[IngestStep]],
      refetch: Boolean,
      path: IngestPath
  ): Either[ApiError, IngestReport] = {
    // an existing source WITH its original keeps it (and its body) unless asked to refetch; one
    // without (metadata only, e.g. created when no OA copy was found) is fetched and converted
    val original =
      AcquisitionControllers.originalOf(sources.load(citekey).map(_.ocrMarkdown), sources.original(citekey))
    val skip = original.filter(_ => !refetch)

    // ── the copy: the acquisition chain (the plugins' source fetchers, legal ones first; the legal
    //    ones alone for a watcher's auto-ingest and `--legal-only`) ──
    val key = WorkLookup.key(doi, arxiv, work.map(_.upstream))
    val chain = skip match
      case Some(have) =>
        FetchChain.Outcome(
          Left(s"skipped: `$citekey` already has $have — `--refetch` fetches and converts it again"),
          Nil
        )
      case None => oa.fetchWorkExplained(key, path.legalChain)
    if skip.isEmpty && path == IngestPath.PersonLegal && acq.allowUnauthorised then
      steps += IngestStep(
        "legal-only",
        true,
        "the legal chain only: the fetchers the config marks unauthorised were not asked"
      )
    val fetched = chain.file
    fetched match
      case Right(f) =>
        steps += IngestStep(
          "fetch",
          true,
          s"${f.source}: ${f.bytes.length} bytes" +
            AcquisitionControllers.hostOf(f.url).fold("")(h => s" from $h") +
            (if f.extension == "xml" then " (JATS full text — no PDF is published)" else "") +
            f.licence.map(_.value.trim).filter(_.nonEmpty).fold("")(l => s", licence $l") +
            original.fold("")(h => s" (replacing $h: --refetch)")
        )
      case Left(e) => steps += IngestStep("fetch", skip.isDefined, e)
    if chain.answers.nonEmpty then steps += IngestStep("chain", false, chain.answers.map(_.line).mkString("; "))

    // a gold open-access work from a publisher that refuses scripts: a download in the browser, on every path
    val browserOnly = Option
      .when(fetched.isLeft && skip.isEmpty)(work)
      .flatten
      .flatMap(w =>
        Citekey
          .parse(citekey)
          .toOption
          .flatMap(
            BrowserDownload
              .of(w, _, com.histmersion.upstreams.landing.RepositoryLanding.onHost(_, acq.landing.refusingHosts))
          )
      )
    browserOnly.foreach { b =>
      val (id, added) = acquireOnce(b.doi.value, b.text)
      if added then
        scribe.info(
          s"acquire: ${b.doi.value} is gold open access on ${b.publisher.value}, which refuses scripts: acquisition item #$id"
        )
      steps += IngestStep(
        "acquire",
        true,
        s"gold open access on ${b.publisher.value}, which refuses scripts — " +
          s"acquisition item #$id${if added then "" else " (already listed)"}: download it in the browser"
      )
    }

    // a watcher's auto-ingest brings a work in only WITH its legal file: no metadata-only source
    if path.needsFile && fetched.isLeft then
      val answers =
        Option.when(chain.answers.nonEmpty)(chain.answers.map(_.line).mkString(" — ", "; ", "")).getOrElse("")
      Left(err(s"${IngestBacklog.NoLegalCopy} ${doi.getOrElse(citekey)}: ${fetched.left.getOrElse("")}$answers"))
    else
      val spooled: Option[FetchedCopy] = fetched.toOption.flatMap(f => spoolCopy(citekey, f, steps))

      // ── reference edges, before the source is written, so they land with it ──
      val refRows = doi.toList.flatMap(d => discovery.references(d).toOption.toList.flatMap(_._2))
      val edges = resolveRefs(refRows)
        .flatMap(_.citekey)
        .distinct
        .filter(_ != citekey)
        .map(Edge(citekey, _, "references"))
      steps += IngestStep(
        "references",
        refRows.nonEmpty,
        s"${refRows.size} reference(s), ${edges.size} resolve to stored sources"
      )

      val meta = IngestMeta(
        title = work.flatMap(_.title).getOrElse(citekey),
        authors = work.map(_.authors).getOrElse(Nil),
        doi = doi,
        year = work.flatMap(_.year),
        venue = work.flatMap(_.venue),
        summary = work.flatMap(_.abstractText),
        provenance = fetched.map(_.source).getOrElse("oa"),
        edges = edges,
        citedByCount = work.flatMap(_.citedByCount)
      )

      jobs.enqueue(spooled, citekey, meta).left.map(err).map { outcome =>
        steps += IngestStep("store", true, outcome.detail)
        // the copy's licence, when its fetcher knew it, becomes the source's if it has none (#2560)
        fetched.toOption.flatMap(_.licence).map(_.value).foreach { l =>
          if sources.recordLicence(citekey, Licence.Id(l), Caller.Local) then
            steps += IngestStep("licence", true, s"recorded ${l.trim}")
        }
        steps ++= acquireSteps(doi, meta.title, meta.authors, outcome.jobId)
        // an open-access work no script may download goes to the acquisition list once, as a download
        // in the browser (#1581) — a person's ingest only: a watcher's legal path queues its own
        if fetched.isLeft && skip.isEmpty && !path.needsFile && browserOnly.isEmpty then
          for
            d <- doi
            w <- work
            text <- Acquire.browserDownloadText(
              w,
              d,
              citekey,
              chain.answers.filter(_.kind == FetchChain.Answer.Kind.Refused).map(a => s"${a.fetcher}: ${a.why}")
            )
          do
            // an item already naming the DOI was reported just above
            if queues.naming(Queue.Kind.Acquire, d).isEmpty then
              val (id, _) = acquireOnce(d, text)
              steps += IngestStep(
                "acquire",
                true,
                s"open access, but no script may download it — acquisition item #$id: download it in the browser"
              )

        // assess last: the source now exists, so the verdict can be recorded against it. The status is
        // written for a source we JUST created, and for a retraction whatever the source's age — a
        // retracted paper must never keep a curated "current".
        val assessment = doi.flatMap { _ =>
          assess(AssessRequest(citekey, apply = false)).toOption.map { a =>
            val shouldApply = outcome.created || a.verdict == Acquire.Verdicts.Retracted
            val applied = for
              status <- Acquire.statusFor(a.verdict) if shouldApply
              _ <- setStatus(citekey, status).toOption
            yield status.wire
            steps += IngestStep("assess", true, s"${a.verdict}${applied.fold("")(s => s" → status=$s")}")
            a.modify(_.applied).setTo(applied)
          }
        }

        IngestReport(
          citekey = citekey,
          doi = doi,
          title = work.flatMap(_.title),
          created = outcome.created,
          pdf = fetched.toOption.map(f => PdfInfo(f.source, f.url, f.bytes.length.toLong, spooled.map(_.where))),
          edgesAdded = edges.size,
          assessment = assessment,
          steps = steps.result(),
          dropIn = Option.when(fetched.isLeft && skip.isEmpty)(
            s"No legal open-access PDF exists for this one. Get it through institutional access, " +
              s"an interlibrary loan or the author, then run: histmersion ingest <file.pdf> --citekey $citekey"
          )
        )
      }
  }

  /** What the import will do about the open acquisition items (#970), said up front: the OCR job closes the ones naming
    * this DOI when it succeeds ([[com.histmersion.domain.library.ingest.IngestQueue]]), and lists the merely similar
    * ones without closing them. With no file there is no job, and an item asking for the work stays open — the record
    * alone does not fulfil it.
    */
  private def acquireSteps(
      doi: Option[String],
      title: String,
      authors: List[String],
      jobId: Option[String]
  ): List[IngestStep] =
    val items = queues.list(Queue.Kind.Acquire, QueueScope.Open).toList
    AcquireMatch.decide(items, doi, title, authors, acq.defaults.titleMatchAt.value).map {
      case AcquireMatch.Decision.Close(i) =>
        IngestStep(
          "acquire",
          jobId.isDefined,
          jobId.fold(s"acquisition item #${i.id} names this DOI — left open: no file was acquired")(j =>
            s"acquisition item #${i.id} names this DOI — ticked off when job $j succeeds"
          )
        )
      case p => IngestStep("acquire", true, AcquireMatch.describe(p))
    }

  /** arXiv mints a DataCite DOI for every submission, which is how OpenAlex indexes preprints. */
  private def arxivDoi(id: String): String = s"10.48550/arxiv.$id"

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** Import `file` of the PUBLIC archive.org item `item` (the library's archive.org collection described it, refused a
    * lending-only one and picked the file): the download is the chain's `archive-org` fetcher.
    */
  private def archiveImportNow(
      item: ArchiveItem,
      file: ArchiveFile,
      req: ArchiveImportRequest
  ): Either[ApiError, IngestReport] =
    val steps = List.newBuilder[IngestStep]
    val rawCitekey = req.citekey
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        stored(Acquire.citekey(item.creator, item.year, item.title), item.creator, item.year, item.title)
      )
    Citekey.of(rawCitekey).flatMap { validCk =>
      val citekey: String = validCk.value
      steps += IngestStep(
        "metadata",
        true,
        s"${item.title.getOrElse(item.identifier)} (${item.year.map(_.toString).getOrElse("?")})"
      )
      // the download is the chain's `archive-org` fetcher (the legal chain: a public item only)
      oa.fetchWork(
        com.histmersion.plugin.WorkKey(
          archive = Some(item.identifier),
          format = req.format,
          title = item.title,
          authors = item.creator,
          year = item.year.map(com.histmersion.plugin.Year(_))
        ),
        legalOnly = true
      ).left
        .map(e => err(s"'${item.identifier}': $e"))
        .flatMap { f =>
          steps += IngestStep("fetch", true, s"archive.org: ${f.bytes.length} bytes (${file.name})")
          val spooled = spoolCopy(citekey, f, steps)
          jobs
            .enqueue(
              spooled,
              citekey,
              IngestMeta(
                title = item.title.getOrElse(citekey),
                authors = item.creator,
                year = item.year,
                venue = item.publisher,
                summary = item.description,
                provenance = "archive_org"
              )
            )
            .left
            .map(err)
            .map { outcome =>
              steps += IngestStep("store", true, outcome.detail)
              steps ++= acquireSteps(None, item.title.getOrElse(citekey), item.creator, outcome.jobId)
              IngestReport(
                citekey,
                None,
                item.title,
                outcome.created,
                Some(PdfInfo("archive_org", None, f.bytes.length.toLong, spooled.map(_.where))),
                0,
                None,
                steps.result()
              )
            }
        }
    }

  /** The fetched copy in the acquisition spool; a failure to write it is a step of the report. */
  private def spoolCopy(
      citekey: String,
      f: com.histmersion.plugin.FetchedFile,
      steps: scala.collection.mutable.Builder[IngestStep, List[IngestStep]]
  ): Option[FetchedCopy] =
    val sp = spool()
    sp.write(citekey, f.extension, f.bytes) match
      case Right(key) => Some(FetchedCopy(key, sp.where(key)))
      case Left(e)    => steps += IngestStep("spool", false, e); None

  /** Patch just the `status` of a source (what `assess --apply` does), without disturbing anything else. */
  private def setStatus(
      citekey: String,
      status: com.histmersion.domain.library.Source.Status
  ): Either[ApiError, String] =
    sources.patch(citekey, SourcePatch(metadata = Some(MetadataPatch(status = Some(status)))), Caller.Local)

  // ── the maintenance lock: every acquisition WRITE goes through it ───────────────────────────

  def assess(req: AssessRequest): Either[ApiError, Assessment] =
    maintenance.guard("assess")(assessNow(req))

  /** The verdict alone: nothing applied, nothing recorded (what an agent's dry run reads). */
  def assessReadOnly(id: String): Either[ApiError, Assessment] =
    maintenance.guard("assess")(assessNow(AssessRequest(id), store = false))

  def followups(req: FollowupsRequest): Either[ApiError, FollowupsResult] =
    maintenance.guard("followups")(followupsNow(req))

  /** A person's ingest; `legalOnly` asks the legal chain alone, and is otherwise the same. */
  def ingest(req: IngestRequest): Either[ApiError, IngestReport] =
    maintenance.guard("acquire/ingest")(
      ingestNow(
        req,
        if req.legalOnly then IngestPath.PersonLegal else IngestPath.Person
      )
    )

  /** [[ingest]] for a watcher's auto-ingest (docs/research-process-managers.md §4 (c)) and the acquisition list's
    * re-check: the LEGAL open-access chain only — no unauthorised fetcher is asked, whatever
    * `acquire.allow-unauthorised` says — and a work with no legal file is refused (Left) rather than stored as metadata
    * alone, so it goes to (or stays on) the acquisition list instead. An existing source with its original is never
    * fetched again; a metadata-only one named by `req.citekey` is fetched and converted into, keeping its enrichment.
    */
  def ingestLegal(req: IngestRequest): Either[ApiError, IngestReport] =
    maintenance.guard("acquire/ingest")(ingestNow(req.modify(_.refetch).setTo(false), IngestPath.Watcher))

  /** The legal chain's generation now ([[FetcherGeneration]]): it changes when a legal fetcher or a fetcher plugin's
    * settings or key change, which makes the acquisition list's items due for a re-check again.
    */
  def fetcherGeneration: String = generation

  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[ApiError, IngestReport] =
    maintenance.guard("archive/import")(archiveImportNow(item, file, req))

  def updateRetractions(): Either[ApiError, RetractionsUpdate] =
    maintenance.guard("retractions/update")(updateRetractionsNow())

  // ── shared: what does the user's identifier point at? ───────────────────────────────────────

  final private case class Target(doi: Option[String], citekey: Option[String])

  /** Resolve `<doi|arxiv:id|citekey>` to both a DOI (for the upstreams) and a stored citekey (for the writes),
    * whichever of the two the caller happened to give us.
    */
  private def target(id: String): Either[ApiError, Target] =
    Acquire.parseIdent(id).left.map(err).map {
      case Ident.Doi(doi) =>
        Target(Some(doi), sources.doiIndex.get(doi))
      case Ident.Arxiv(a) =>
        val doi = arxivDoi(a)
        Target(Some(doi), sources.doiIndex.get(doi))
      case Ident.Citekey(k) =>
        Target(sources.load(k).flatMap(_.doi), Option.when(sources.exists(k))(k))
    }

object AcquisitionControllers:

  /** The host a file was fetched from (`www.ebi.ac.uk`, `pmc-oa-opendata.s3.amazonaws.com`), for the fetch step: the
    * provenance names the channel, this names where the copy actually came from.
    */
  def hostOf(url: Option[String]): Option[String] =
    url
      .flatMap(u => scala.util.Try(java.net.URI.create(u).getHost).toOption)
      .flatMap(Option(_))
      .filter(_.nonEmpty)

  /** What original an existing source already has, in words — a converted body, else a stored file (`sources.sha256`) —
    * or None for a metadata-only source (or no source at all). `body` is None when the source does not exist.
    */
  def originalOf(body: Option[Option[String]], sha256: Option[String]): Option[String] =
    body.flatMap { b =>
      if b.exists(_.trim.nonEmpty) then Some("a converted body")
      else sha256.map(sha => s"a stored original (sha256 ${sha.take(12)}…)")
    }
