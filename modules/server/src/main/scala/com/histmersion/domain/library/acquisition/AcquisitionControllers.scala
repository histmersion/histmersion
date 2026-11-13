package com.histmersion
package domain.library.acquisition
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.ingest.{FetchedCopy, IngestJobs, IngestMeta}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.Pinning
import com.histmersion.domain.library.acquisition.Spool

import com.histmersion.domain.library.Citekey

import com.histmersion.pluginhost.{FetchChain, OaFetcher}
import com.histmersion.domain.library.acquisition.WorkLookup

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*

import hearth.kindlings.optics.syntax.*
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
  DiscoverResult,
  Edge,
  FollowupRow,
  FollowupsRequest,
  FollowupsResult,
  MetadataPatch,
  PdfInfo,
  RefRow,
  RefsRequest,
  RefsResult,
  RetractionNotice,
  RetractionsUpdate,
  UpdateNotice,
  WikirefsResult,
  WorkRef
}
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.Goal
import com.histmersion.api.library.AcquireApi
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.ArxivId
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.domain.Title
import com.histmersion.api.ByteCount
import com.histmersion.domain.AcquireMatch
import com.histmersion.util.jobs.Job
import com.histmersion.plugin.WorkKey
import com.histmersion.domain.AcquireRecheck

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
    goals: GoalService,
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
)(using clock: java.time.Clock):
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

  private def err(msg: Problem): Api.Error = Api.Error(msg)

  private def step(name: String, ok: Boolean, detail: String): Ingest.Step =
    Ingest.Step(Ingest.Step.Value(name), Ingest.Step.Ok(ok), Ingest.Step.Detail(detail))

  private def held(k: com.histmersion.api.library.Citekey): Boolean = Citekey.option(k.value).exists(sources.exists)

  private def goalSlug(raw: String): Either[Api.Error, Goal.Slug] =
    Goal.Slug
      .either(raw)
      .left
      .map(_ => Write.invalid(Problem.Unclassified(Legacy.Text(s"invalid goal '$raw'"))))

  // ── deterministic new-source search (#3761) ──────────────────────────────────────────────────

  private val search =
    CandidateSearch(
      candidateStore,
      sources,
      goals,
      articles,
      queues,
      cfg().acquire.candidates,
      indexes,
      alerts,
      today = () => java.time.LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC))
    )

  /** A goal's (or every goal's) candidates in one state (`new` by default), with the counts per state. */
  def candidates(
      goal: Option[com.histmersion.api.automation.Goal.Slug],
      state: Option[Candidates.State.Wire],
      limit: Option[AcquireApi.Limit],
      offset: Option[CandidateList.Offset]
  ): Either[Api.Error, CandidateList] =
    val st = state.map(_.value.trim).filter(_.nonEmpty).getOrElse(Candidates.State.New.wire.value)
    for
      s <- Candidates.State
        .parse(Candidates.State.Wire(st))
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown state '$st' (${Candidates.State.values.map(_.wire.value).mkString(", ")})")
            ),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
      g <- goal
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold[Either[Api.Error, Option[Goal.Slug]]](Right(None))(x => goalSlug(x).map(Some(_)))
    yield
      val n = cfg().limits.page(
        limit.flatMap(l => PageSize.option(l.value)),
        PageSize.applyUnsafe(math.max(1, cfg().limits.search.value))
      )
      val from = offset.flatMap(o => Offset.option(o.value)).getOrElse(Offset.Start)
      val page = candidateStore.list(
        g,
        Source.Candidate.State(s.wire.value),
        CandidateService.ListLimit(n.value + 1),
        from
      )
      CandidateList(
        g.map(x => CandidateList.Goal(x.value)),
        CandidateList.State(s.wire.value),
        page.take(n.value),
        candidateStore.counts(g),
        CandidateList.Offset(from.value),
        Option.when(page.size > n.value)(CandidateList.Next(from.value + n.value))
      )

  /** Read a goal's watches from their watermarks and store what is new (no model). */
  def sweepCandidates(r: CandidateSweep.Request): Either[Api.Error, CandidateSweep] =
    goalSlug(r.goal.value.trim).flatMap { g =>
      if r.dryRun.value then search.sweep(g, dryRun = CandidateSearch.SweepDryRun(true))
      else
        maintenance.guard(WriteGate.OperationName("candidates/sweep"))(
          search.sweep(g, dryRun = CandidateSearch.SweepDryRun(false))
        )
    }

  def markCandidates(m: CandidateMark): Either[Api.Error, CandidateMarked] =
    maintenance.guard(WriteGate.OperationName("candidates/mark")):
      Candidates.State
        .parse(Candidates.State.Wire(m.state.value))
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(
                s"unknown state '${m.state.value}' (${Candidates.State.values.map(_.wire.value).mkString(", ")})"
              )
            ),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
        .map(s =>
          CandidateMarked(
            Source.Candidate.State(s.wire.value),
            CandidateMarked.Updated(candidateStore.mark(m.ids.distinct, Source.Candidate.State(s.wire.value)).value)
          )
        )

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  /** OpenAlex search, with each hit marked `inDb` so a queue-builder can skip what we already have. */
  def discover(
      q: AcquireApi.Query,
      since: Option[Year],
      sort: Option[AcquireApi.Filter],
      limit: Option[AcquireApi.Limit],
      notInDb: Boolean
  ): Either[Api.Error, DiscoverResult] =
    val query = q.value.trim
    if query.isEmpty then Left(err(Problem.Unclassified(Legacy.Text("give a search query"))))
    else
      discovery
        .search(
          Search.Query(query),
          since.map(y => com.histmersion.Year(y.value.toInt)),
          sort.map(x => DiscoveryClient.SearchSort(x.value)),
          DiscoveryClient.SearchLimit(
            cfg().limits
              .page(
                limit.flatMap(l => PageSize.option(l.value)),
                PageSize.applyUnsafe(math.max(1, acq.defaults.searchLimit.value))
              )
              .value
          )
        )
        .left
        .map(e => err(e))
        .map { works =>
          // ONE index for the whole page: `mark` used to read `lower(doi) -> citekey` for the entire
          // corpus once per RESULT ROW, i.e. 25 full-table scans for one search.
          val index = sources.doiIndex
          val marked = works.map(mark(_, index))
          val kept = if notInDb then marked.filterNot(_.inDb.value) else marked
          DiscoverResult(DiscoverResult.Query(query), DiscoverResult.Count(kept.size), kept)
        }

  def meta(id: AcquireApi.Identifier): Either[Api.Error, WorkRef] =
    Ident.parse(Ident.Written(id.value)).left.map(e => err(e)).flatMap {
      case Ident.Doi(doi) =>
        discovery.meta(doi).left.map(e => err(e)).map(mark(_, sources.doiIndex))
      case Ident.Arxiv(a) =>
        discovery.meta(arxivDoi(a)).left.map(e => err(e)).map(mark(_, sources.doiIndex))
      case Ident.Citekey(k) =>
        Citekey
          .option(k.value)
          .flatMap(sources.load)
          .toRight(Write.notFound(com.histmersion.api.library.Citekey(k.value)))
          .flatMap { s =>
            s.doi
              .flatMap(d => Doi.option(d.value))
              .toRight(
                err(Problem.Unclassified(Legacy.Text(s"'$k' has no DOI recorded — nothing to look up upstream")))
              )
              .flatMap(discovery.meta(_).left.map(e => err(e)))
              .map(mark(_, sources.doiIndex))
          }
    }

  def wikirefs(url: com.histmersion.api.library.Title): Either[Api.Error, WikirefsResult] =
    wiki.refs(WikiClient.ArticleUrl(url.value)).left.map(e => err(e)).map { r =>
      val index = sources.doiIndex
      r.modify(_.known)
        .setTo(r.dois.filter(d => Doi.option(d.value).exists(index.contains)).map(d => WikirefsResult.Known(d.value)))
    }

  /** Fill in `inDb` and keep the citekey the corpus would use. `index` is passed IN so a caller marking a whole page
    * reads the DOI index once.
    */
  private def mark(w: WorkRef, index: Map[Doi, Citekey]): WorkRef =
    val byDoi =
      w.doi.flatMap(d => Doi.option(d.value)).flatMap(index.get).map(c => com.histmersion.api.library.Citekey(c.value))
    // the generated key, or the pre-#878 key a record with an accented author may still carry
    val keys = w.citekey.toList ++
      Option.when(w.citekey.isDefined)(Acquire.legacyCitekey(w.authors, w.year, w.title)).toList
    val byKey = keys.distinct.find(held)
    val resolved = byDoi.orElse(byKey)
    w.modify(_.inDb).setTo(WorkRef.InDb(resolved.isDefined)).modify(_.citekey).setTo(resolved.orElse(w.citekey))

  /** The generated key `generated`, unless no record has it and one has the pre-#878 key of the same metadata — then
    * that one, so a re-import lands on the existing record, not a duplicate.
    */
  private def stored(
      generated: com.histmersion.api.library.Citekey,
      authors: List[com.histmersion.api.library.AuthorName],
      year: Option[Year],
      title: Option[com.histmersion.api.library.Title]
  ): com.histmersion.api.library.Citekey =
    val legacy = Acquire.legacyCitekey(authors, year, title)
    if legacy != generated && !held(generated) && held(legacy) then legacy
    else generated

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  /** The deposited reference list, with each entry resolved to a stored source where possible. `apply` writes those as
    * `references` edges out of the target.
    */
  def refs(req: RefsRequest): Either[Api.Error, RefsResult] =
    target(req.id.value).flatMap { t =>
      t.doi
        .toRight(err(Problem.Unclassified(Legacy.Text(s"no DOI for '${req.id}' — a reference list needs one"))))
        .flatMap { doi =>
          discovery.references(doi).left.map(e => err(e)).map { (backend, rows) =>
            val resolved = resolveRefs(rows)
            val added: RowCount =
              if !req.apply.value then RowCount(0)
              else
                t.citekey match
                  case None      => RowCount(0)
                  case Some(src) =>
                    sources.addEdges(
                      resolved
                        .flatMap(_.citekey)
                        .distinct
                        .filter(_.value != src.value)
                        .map(Edge(com.histmersion.api.library.Citekey(src.value), _, Edge.Kind.References)),
                      Caller.Local
                    )
            RefsResult(
              RefsResult.Target(req.id.value),
              Some(com.histmersion.api.library.Doi(doi.value)),
              RefsResult.Backend(backend.value),
              RefsResult.Count(resolved.size),
              RefsResult.Resolved(resolved.count(_.citekey.isDefined)),
              RefsResult.EdgesAdded(added.value),
              resolved
            )
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
      val byDoi = r.doi.flatMap(d => Doi.option(d.value)).flatMap(index.get)
      // the current key first, then the pre-#878 one (accented letters dropped) older records carry
      val guesses = Acquire.citekeyCandidates(r.authors, r.year, r.title)
      val byKey = guesses.flatMap(g => Citekey.option(g.value)).find(citekeys.contains)
      val byPrefix = guesses.iterator
        .map { guess =>
          val stem = guess.value.takeWhile(_ != '_')
          val hits = citekeys.filter(k => k.value == stem || k.value.startsWith(stem + "_"))
          Option.when(hits.size == 1)(hits.head)
        }
        .collectFirst { case Some(k) => k }
      r.modify(_.citekey)
        .setTo(byDoi.orElse(byKey).orElse(byPrefix).map(c => com.histmersion.api.library.Citekey(c.value)))
    }

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  /** `store = false` (an agent's dry run, #992): the verdict is computed but not recorded. */
  private def assessNow(req: AssessRequest, store: Boolean = true): Either[Api.Error, Assessment] =
    target(req.id.value).flatMap { t =>
      t.doi.toRight(err(Problem.Unclassified(Legacy.Text(s"no DOI for '${req.id}' — an assessment needs one")))).map {
        doi =>
          val rw = assessments.retractionsFor(doi)
          val updates = discovery.updates(doi).getOrElse(Nil)
          val work = discovery.meta(doi).toOption
          val stored = t.citekey.flatMap(sources.load)
          val cited = work.flatMap(_.citedByCount).orElse(stored.flatMap(_.citedByCount))
          val citedCount = cited.map(c => Acquire.CitedByCount(c.value))
          val influential = stored.flatMap(_.influentialCitations)
          val year = work.flatMap(_.year).orElse(stored.flatMap(_.year))
          val d = acq.defaults
          val verdict = Acquire.verdict(
            rw,
            updates,
            work.exists(_.isRetracted.value),
            citedCount,
            year,
            Acquire.CitationThreshold(d.wellCitedAt.value),
            Acquire.CitationThreshold(d.lowCitedAt.value),
            Year(d.staleBefore.value.toLong)
          )

          val checkedAt = t.citekey.filter(_ => store).map { ck =>
            assessments.record(
              AssessmentRecord(
                ck,
                AssessmentRecord.Verdict(verdict.value),
                AssessmentRecord.Retracted(verdict == Acquire.Verdicts.Retracted),
                noticesJson(rw, updates),
                cited.map(c => AssessmentRecord.CitedBy(c.value)),
                influential.map(i => AssessmentRecord.Influential(i.value))
              )
            )
          }
          val decision = for
            ck <- t.citekey if req.apply.value
            cur <- stored
            d <- Acquire.statusDecision(verdict, cur.status, req.forceStatus.value)
          yield ck -> d
          val applied = decision.flatMap {
            case (ck, Right(status)) =>
              setStatus(ck, status.transformInto[com.histmersion.domain.library.Source.Status]).toOption
                .map(_ => status.wire)
            case (_, Left(_)) => None
          }
          val kept = decision.flatMap(_._2.left.toOption)

          Assessment(
            target = Assessment.Target(req.id.value),
            doi = Some(com.histmersion.api.library.Doi(doi.value)),
            citekey = t.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
            year = year,
            citedByCount = citedCount,
            influentialCitations = influential.map(i => Assessment.InfluentialCitations(i.value)),
            isRetracted = Assessment.IsRetracted(verdict == Acquire.Verdicts.Retracted),
            retractionWatch = rw,
            crossrefUpdates = updates,
            flagged = Assessment.Flagged(Acquire.flagged(verdict)),
            verdict = verdict,
            recommendation = Assessment.Recommendation(Acquire.recommendation(verdict, citedCount).value),
            applied = applied.map(w => Assessment.Applied(w.value)),
            checkedAt = checkedAt.map(c => Assessment.CheckedAt(c.value)),
            kept = kept
          )
      }
    }

  /** The notices, verbatim, so a later reader sees WHY a verdict was reached without asking Crossref again.
    */
  private def noticesJson(rw: List[RetractionNotice], up: List[UpdateNotice]): AssessmentRecord.NoticesJson =
    AssessmentRecord.NoticesJson(Json.obj("retractionWatch" -> rw.asJson, "crossrefUpdates" -> up.asJson).noSpaces)

  /** Refresh the local Retraction Watch mirror — our ground truth, so an assessment never depends on a third party
    * being up at the moment somebody asks.
    *
    * The work is ~30 MB of CSV and ~60 000 rows, which does not fit in one HTTP request (the same constraint that made
    * `reindex` resumable), so this STARTS the refresh on a virtual thread and returns immediately; callers poll
    * [[retractionsStatus]]. The two fields of mutable state below are the price of that: a flag so two callers cannot
    * run it twice, and the last error so a failed refresh is visible instead of silent.
    */
  private def updateRetractionsNow(): Either[Api.Error, RetractionsUpdate] =
    if !rwRunning.compareAndSet(false, true) then Right(retractionsStatus)
    else
      rwError.set(None)
      // on the generation (database, clients) the request was pinned to
      val work = pins.carry(try
        retractionWatch.csv() match
          case Left(e) =>
            scribe.warn(s"acquire: Retraction Watch refresh failed — ${e.sentence}")
            rwError.set(Some(e.sentence))
          case Right(csv) =>
            val n = assessments.replaceRetractionWatch(
              Acquire.parseRetractionWatch(csv.value)
            )
            scribe.info(s"acquire: Retraction Watch refreshed — $n row(s)")
      catch
        case e: Throwable =>
          scribe.error(s"acquire: Retraction Watch refresh crashed: ${e.getMessage}", e)
          rwError.set(Some(e.getMessage))
      finally rwRunning.set(false))
      val _ = Thread.ofVirtual().name("retraction-watch-refresh").start(() => work.run())
      Right(retractionsStatus.modify(_.running).setTo(RetractionsUpdate.Running(true)))

  /** How the mirror is doing: how many rows it holds, whether a refresh is in flight, and the last failure if there was
    * one.
    */
  def retractionsStatus: RetractionsUpdate =
    RetractionsUpdate(
      RetractionsUpdate.Rows(assessments.retractionCount.value),
      RetractionsUpdate.Source(acq.retractionWatch.csvUrl.value),
      running = RetractionsUpdate.Running(rwRunning.get),
      error = rwError.get.map((text => Problem.Unclassified(Legacy.Text(text))))
    )

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  private def followupsNow(req: FollowupsRequest): Either[Api.Error, FollowupsResult] =
    target(req.id.value).flatMap { t =>
      t.doi
        .toRight(err(Problem.Unclassified(Legacy.Text(s"no DOI for '${req.id}' — the citation graph is keyed by DOI"))))
        .flatMap { doi =>
          val work =
            discovery.meta(doi).getOrElse(WorkRef(doi = (Some(doi)).map(d => com.histmersion.api.library.Doi(d.value))))
          val cap = CitationsClient.CitingLimit(
            cfg().limits
              .page(
                req.limit.flatMap(l => PageSize.option(l.value)),
                PageSize.applyUnsafe(math.max(1, acq.defaults.followupsLimit.value))
              )
              .value
          )
          citations
            .citing(work, req.since.map(y => com.histmersion.Year(y.value.toInt)), cap)
            .left
            .map(e => err(e))
            .map { (backend, rows) =>
              val index = sources.doiIndex
              val enriched = Acquire.rankFollowups(rows).map { r =>
                r.modify(_.proposedKind)
                  .setTo(
                    FollowupRow.ProposedKind(
                      Acquire
                        .classify(
                          r.intents.map(i => Acquire.Intent.Wire(i.value)),
                          r.contexts.map(c => Acquire.CitationContext(c.value)),
                          r.isInfluential.value,
                          r.year,
                          work.year
                        )
                        .value
                    )
                  )
                  .modify(_.citekey)
                  .setTo(
                    r.doi
                      .flatMap(d => Doi.option(d.value))
                      .flatMap(index.get)
                      .map(c => com.histmersion.api.library.Citekey(c.value))
                  )
              }
              // an edge can only start at a source we actually hold: the citing paper must be in the
              // corpus, and so must the target
              val added: RowCount = (req.apply.value, t.citekey) match
                case (true, Some(dst)) =>
                  sources.addEdges(
                    enriched.flatMap(r =>
                      r.citekey
                        .filter(_.value != dst.value)
                        .flatMap(src =>
                          Edge.Kind
                            .fromWire(Edge.Kind.Wire(r.proposedKind.value))
                            .map(Edge(src, com.histmersion.api.library.Citekey(dst.value), _))
                        )
                    ),
                    Caller.Local
                  )
                case _ => RowCount(0)
              FollowupsResult(
                FollowupsResult.Target(req.id.value),
                Some(com.histmersion.api.library.Doi(doi.value)),
                FollowupsResult.Backend(backend.value),
                FollowupsResult.Count(enriched.size),
                FollowupsResult.EdgesAdded(added.value),
                enriched
              )
            }
        }
    }

  // ── ingest by identifier ────────────────────────────────────────────────────────────────────

  /** `histmersion ingest <doi|arxiv:id>`: metadata → legal OA PDF → the ingest job → reference edges → assessment.
    * Every step is reported, including the ones that failed, because "no OA copy" is a normal outcome that the user
    * answers with a manual drop-in.
    */
  private def ingestNow(req: Ingest.Request, path: IngestPath): Either[Api.Error, Ingest.Report] =
    Ident.parse(Ident.Written(req.id.value)).left.map(e => err(e)).flatMap { ident =>
      val steps = List.newBuilder[Ingest.Step]

      val (doi, arxiv) = ident match
        case Ident.Doi(d)     => (Some(d), req.arxiv.flatMap(a => Ident.arxiv(Ident.Written(a.value))))
        case Ident.Arxiv(a)   => (Some(arxivDoi(a)), Some(a))
        case Ident.Citekey(k) =>
          (
            Citekey.option(k.value).flatMap(sources.load).flatMap(_.doi).flatMap(d => Doi.option(d.value)),
            req.arxiv.flatMap(a => Ident.arxiv(Ident.Written(a.value)))
          )

      val work = doi.flatMap(discovery.meta(_).toOption)
      work match
        case Some(w) =>
          steps += step(
            "metadata",
            true,
            s"${w.title.getOrElse("(untitled)")} (${w.year.map(_.toString).getOrElse("?")}), " +
              s"${w.citedByCount.getOrElse(0)} citation(s)"
          )
        case None => steps += step("metadata", false, s"no upstream record for ${doi.fold(req.id.value)(_.value)}")

      val explicit = req.citekey.map(_.value.trim).filter(_.nonEmpty)
      val existing = ident match
        case Ident.Citekey(k) => Some(k.value: String)
        case _                => None
      val citekey = explicit
        .orElse(work.flatMap(w => w.citekey.map(stored(_, w.authors, w.year, w.title).value)))
        .orElse(existing)

      // METADATA GUARD. Without a title from an upstream we have nothing to name the source after,
      // and inventing one from the DOI produces exactly the `anonnd`-style junk entry the Calibre
      // guard exists to prevent. A typo'd or unregistered DOI must be an actionable error, not a row.
      if work.flatMap(_.title).isEmpty && citekey.isEmpty then
        Left(
          err(
            Problem.Unclassified(
              Legacy.Text(
                s"no upstream record for '${req.id}' (OpenAlex and Crossref both miss it) — check the " +
                  "identifier, or pass --citekey to create the source anyway and fill the metadata in by hand"
              )
            )
          )
        )
      else
        // the citekey becomes a spool directory name, and it can come from the CALLER (--citekey)
        // or from an upstream record's title — neither is trustworthy as a path segment.
        Citekey
          .parse(citekey.get)
          .left
          .map(p => Write.invalid(p))
          .flatMap(ck => ingestWith(doi, arxiv, work, ck, steps, req.refetch.value, path))
    }

  /** The acquisition item that names `doi`, else a new browser download with `text`; and whether it is new. */
  private def acquireOnce(doi: Doi, text: Queue.QueueItem.Text): (Queue.QueueItem.Id, Boolean) =
    queues.naming(Queue.Kind.Acquire, doi).headOption match
      case Some(existing) => (existing.id, false)
      case None           =>
        (
          queues
            .add(
              Queue.Kind.Acquire,
              Queue.QueueAdd(text, topic = Some(Queue.QueueItem.Topic(Acquire.BrowserDownloadTopic)))
            )
            .id,
          true
        )

  /** The rest of an ingest, once we know what the source will be CALLED. */
  private def ingestWith(
      doi: Option[Doi],
      arxiv: Option[ArxivId],
      work: Option[WorkRef],
      citekey: Citekey,
      steps: scala.collection.mutable.Builder[Ingest.Step, List[Ingest.Step]],
      refetch: Boolean,
      path: IngestPath
  ): Either[Api.Error, Ingest.Report] = {
    // an existing source WITH its original keeps it (and its body) unless asked to refetch; one
    // without (metadata only, e.g. created when no OA copy was found) is fetched and converted
    val original =
      AcquisitionControllers.originalOf(
        sources.load(citekey).map(_.ocrMarkdown),
        sources.original(citekey).map(o => Sha256.applyUnsafe(o.value))
      )
    val skip = original.filter(_ => !refetch)

    // ── the copy: the acquisition chain (the plugins' source fetchers, legal ones first; the legal
    //    ones alone for a watcher's auto-ingest and `--legal-only`) ──
    val key = WorkLookup.key(
      doi.map(d => com.histmersion.plugin.Doi(d.value)),
      arxiv.map(a => com.histmersion.upstreams.ArxivId(a.value)),
      work.map(_.upstream)
    )
    val chain = skip match
      case Some(have) =>
        FetchChain.Outcome(
          Left(
            Problem.Unclassified(
              Legacy.Text(s"skipped: `$citekey` already has $have — `--refetch` fetches and converts it again")
            )
          ),
          Nil
        )
      case None => oa.fetchWorkExplained(key, FetchChain.LegalOnly(path.legalChain.value))
    if skip.isEmpty && path == IngestPath.PersonLegal && acq.allowUnauthorised.value then
      steps += step(
        "legal-only",
        true,
        "the legal chain only: the fetchers the config marks unauthorised were not asked"
      )
    val fetched = chain.file
    fetched match
      case Right(f) =>
        steps += step(
          "fetch",
          true,
          s"${f.source}: ${f.bytes.value.length} bytes" +
            AcquisitionControllers.hostOf(f.url.map(_.value)).fold("")(h => s" from $h") +
            (if f.extension.value == "xml" then " (JATS full text — no PDF is published)" else "") +
            f.licence.map(_.value.trim).filter(_.nonEmpty).fold("")(l => s", licence $l") +
            original.fold("")(h => s" (replacing $h: --refetch)")
        )
      case Left(e) => steps += step("fetch", skip.isDefined, s"${e.sentence}")
    if chain.answers.nonEmpty then steps += step("chain", false, chain.answers.map(_.line).mkString("; "))

    // a gold open-access work from a publisher that refuses scripts: a download in the browser, on every path
    val browserOnly = Option
      .when(fetched.isLeft && skip.isEmpty)(work)
      .flatten
      .flatMap(w =>
        BrowserDownload.of(
          w,
          citekey,
          location =>
            RepositoryLanding
              .onHost(
                RepositoryLanding.OnHostUrl(location.value),
                acq.landing.refusingHosts.map(h => RepositoryLanding.HostPattern(h.value))
              )
              .map(h => BrowserDownload.Host(h.value))
        )
      )
    browserOnly.foreach { b =>
      val (id, added) = acquireOnce(b.doi, Queue.QueueItem.Text(b.text.value))
      if added then
        scribe.info(
          s"acquire: ${b.doi.value} is gold open access on ${b.publisher.value}, which refuses scripts: acquisition item #$id"
        )
      steps += step(
        "acquire",
        true,
        s"gold open access on ${b.publisher.value}, which refuses scripts — " +
          s"acquisition item #$id${if added then "" else " (already listed)"}: download it in the browser"
      )
    }

    // a watcher's auto-ingest brings a work in only WITH its legal file: no metadata-only source
    if path.needsFile.value && fetched.isLeft then
      val answers =
        Option.when(chain.answers.nonEmpty)(chain.answers.map(_.line).mkString(" — ", "; ", "")).getOrElse("")
      Left(
        err(
          Problem.Unclassified(
            Legacy.Text(
              s"${IngestBacklog.NoLegalCopy} ${doi.getOrElse(citekey)}: ${fetched.left.toOption.fold("")(_.sentence)}$answers"
            )
          )
        )
      )
    else
      val spooled: Option[FetchedCopy] = fetched.toOption.flatMap(f => spoolCopy(citekey, f, steps))

      // ── reference edges, before the source is written, so they land with it ──
      val refRows = doi.toList.flatMap(d => discovery.references(d).toOption.toList.flatMap(_._2))
      val edges = resolveRefs(refRows)
        .flatMap(_.citekey)
        .distinct
        .filter(_.value != citekey.value)
        .map(Edge(com.histmersion.api.library.Citekey(citekey.value), _, Edge.Kind.References))
      steps += step(
        "references",
        refRows.nonEmpty,
        s"${refRows.size} reference(s), ${edges.size} resolve to stored sources"
      )

      val meta = IngestMeta(
        title = IngestMeta.Title(work.flatMap(_.title).fold(citekey.value: String)(_.value)),
        authors = work.map(_.authors).getOrElse(Nil).map(a => IngestMeta.Authors(a.value)),
        doi = doi,
        year = work.flatMap(_.year).map(y => com.histmersion.Year(y.value.toInt)),
        venue = work.flatMap(_.venue).map(v => IngestMeta.Venue(v.value)),
        summary = work.flatMap(_.abstractText).map(a => IngestMeta.Summary(a.value)),
        provenance = IngestMeta.Provenance(fetched.map(_.source.toString).getOrElse("oa")),
        edges = edges,
        citedByCount = work.flatMap(_.citedByCount).map(c => IngestMeta.CitedByCount(c.value))
      )

      jobs.enqueue(spooled, citekey, meta).left.map(e => err(e)).map { outcome =>
        steps += step("store", true, s"${outcome.detail}")
        // the copy's licence, when its fetcher knew it, becomes the source's if it has none (#2560)
        fetched.toOption.flatMap(_.licence).map(_.value).foreach { l =>
          if sources.recordLicence(citekey, Licence.Id(l), Caller.Local) then
            steps += step("licence", true, s"recorded ${l.trim}")
        }
        steps ++= acquireSteps(
          doi,
          Title(meta.title.value),
          meta.authors.map(a => AcquireMatch.DecideAuthors(a.value)),
          outcome.jobId
        )
        // an open-access work no script may download goes to the acquisition list once, as a download
        // in the browser (#1581) — a person's ingest only: a watcher's legal path queues its own
        if fetched.isLeft && skip.isEmpty && !path.needsFile.value && browserOnly.isEmpty then
          for
            d <- doi
            w <- work
            text <- Acquire.browserDownloadText(
              w,
              com.histmersion.api.library.Doi(d.value),
              com.histmersion.api.library.Citekey(citekey.value),
              chain.answers
                .filter(_.kind == FetchChain.Answer.Kind.Refused)
                .map(a => Problem.Unclassified(Legacy.Text(s"${a.fetcher}: ${a.why.sentence}")))
            )
          do
            // an item already naming the DOI was reported just above
            if queues.naming(Queue.Kind.Acquire, d).isEmpty then
              val (id, _) = acquireOnce(d, Queue.QueueItem.Text(text.value))
              steps += step(
                "acquire",
                true,
                s"open access, but no script may download it — acquisition item #$id: download it in the browser"
              )

        // assess last: the source now exists, so the verdict can be recorded against it. The status is
        // written for a source we JUST created, and for a retraction whatever the source's age — a
        // retracted paper must never keep a curated "current".
        val assessment = doi.flatMap { _ =>
          assess(AssessRequest(AssessRequest.Id(citekey.value), apply = AssessRequest.Apply(false))).toOption.map { a =>
            val shouldApply = outcome.created.value || a.verdict == Acquire.Verdicts.Retracted
            val applied = for
              status <- Acquire.statusFor(a.verdict) if shouldApply
              _ <- setStatus(citekey, status.transformInto[com.histmersion.domain.library.Source.Status]).toOption
            yield status.wire
            steps += step("assess", true, s"${a.verdict}${applied.fold("")(s => s" → status=$s")}")
            a.modify(_.applied).setTo(applied.map(w => Assessment.Applied(w.value)))
          }
        }

        Ingest.Report(
          citekey = com.histmersion.api.library.Citekey(citekey.value),
          doi = doi.map(d => com.histmersion.api.library.Doi(d.value)),
          title = work.flatMap(_.title),
          created = Ingest.Report.Created(outcome.created.value),
          pdf = fetched.toOption.map(f =>
            PdfInfo(
              PdfInfo.Source(f.source.value),
              f.url.map(u => PdfInfo.Url(u.value)),
              ByteCount(f.bytes.value.length.toLong),
              spooled.map(c => PdfInfo.SpoolPath(c.where.value))
            )
          ),
          edgesAdded = Ingest.Report.EdgesAdded(edges.size),
          assessment = assessment,
          steps = steps.result(),
          dropIn = Option.when(fetched.isLeft && skip.isEmpty)(
            Ingest.Report.DropIn(
              s"No legal open-access PDF exists for this one. Get it through institutional access, " +
                s"an interlibrary loan or the author, then run: histmersion ingest <file.pdf> --citekey $citekey"
            )
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
      doi: Option[Doi],
      title: Title,
      authors: List[AcquireMatch.DecideAuthors],
      jobId: Option[Job.Id]
  ): List[Ingest.Step] =
    val items = queues.list(Queue.Kind.Acquire, QueueScope.Open).toList
    AcquireMatch
      .decide(items, doi, title, authors, AcquireMatch.ProposeThreshold(acq.defaults.titleMatchAt.value))
      .map {
        case AcquireMatch.Decision.Close(i) =>
          step(
            "acquire",
            jobId.isDefined,
            jobId.fold(s"acquisition item #${i.id} names this DOI — left open: no file was acquired")(j =>
              s"acquisition item #${i.id} names this DOI — ticked off when job $j succeeds"
            )
          )
        case p => step("acquire", true, AcquireMatch.describe(p).value)
      }

  /** arXiv mints a DataCite DOI for every submission, which is how OpenAlex indexes preprints. */
  private def arxivDoi(id: ArxivId): Doi =
    Doi.applyUnsafe(s"10.48550/arxiv.${id.value}")

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** Import `file` of the PUBLIC archive.org item `item` (the library's archive.org collection described it, refused a
    * lending-only one and picked the file): the download is the chain's `archive-org` fetcher.
    */
  private def archiveImportNow(
      item: ArchiveItem,
      file: ArchiveFile,
      req: ArchiveImportRequest
  ): Either[Api.Error, Ingest.Report] =
    val steps = List.newBuilder[Ingest.Step]
    val authors = item.creator.map(c => com.histmersion.api.library.AuthorName(c.value))
    val title = item.title.map(t => com.histmersion.api.library.Title(t.value))
    val rawCitekey = req.citekey
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .getOrElse(
        stored(Acquire.citekey(authors, item.year, title), authors, item.year, title).value
      )
    Citekey.parse(rawCitekey).left.map(p => Write.invalid(p)).flatMap { citekey =>
      steps += step(
        "metadata",
        true,
        s"${item.title.getOrElse(item.identifier)} (${item.year.map(_.toString).getOrElse("?")})"
      )
      // the download is the chain's `archive-org` fetcher (the legal chain: a public item only)
      oa.fetchWork(
        WorkKey(
          archive = Some(WorkKey.Archive(item.identifier.value)),
          format = req.format.map(f => WorkKey.Format(f.value)),
          title = item.title.map(t => WorkKey.Title(t.value)),
          authors = item.creator.map(c => WorkKey.Authors(c.value)),
          year = item.year.map(y => com.histmersion.plugin.Year(y.value.toInt))
        ),
        FetchChain.LegalOnly(true)
      ).left
        .map(e => err(Problem.Unclassified(Legacy.Text(s"'${item.identifier}': ${e.sentence}"))))
        .flatMap { f =>
          steps += step("fetch", true, s"archive.org: ${f.bytes.value.length} bytes (${file.name})")
          val spooled = spoolCopy(citekey, f, steps)
          jobs
            .enqueue(
              spooled,
              citekey,
              IngestMeta(
                title = IngestMeta.Title(item.title.fold(citekey.value: String)(_.value)),
                authors = item.creator.map(c => IngestMeta.Authors(c.value)),
                year = item.year.map(y => com.histmersion.Year(y.value.toInt)),
                venue = item.publisher.map(v => IngestMeta.Venue(v.value)),
                summary = item.description.map(d => IngestMeta.Summary(d.value)),
                provenance = IngestMeta.Provenance("archive_org")
              )
            )
            .left
            .map(e => err(e))
            .map { outcome =>
              steps += step("store", true, s"${outcome.detail}")
              steps ++= acquireSteps(
                None,
                Title(item.title.fold(citekey.value: String)(_.value)),
                item.creator.map(c => AcquireMatch.DecideAuthors(c.value)),
                outcome.jobId
              )
              Ingest.Report(
                com.histmersion.api.library.Citekey(citekey.value),
                None,
                title,
                Ingest.Report.Created(outcome.created.value),
                Some(
                  PdfInfo(
                    PdfInfo.Source("archive_org"),
                    None,
                    ByteCount(f.bytes.value.length.toLong),
                    spooled.map(c => PdfInfo.SpoolPath(c.where.value))
                  )
                ),
                Ingest.Report.EdgesAdded(0),
                None,
                steps.result()
              )
            }
        }
    }

  /** The fetched copy in the acquisition spool; a failure to write it is a step of the report. */
  private def spoolCopy(
      citekey: Citekey,
      f: com.histmersion.plugin.FetchedFile,
      steps: scala.collection.mutable.Builder[Ingest.Step, List[Ingest.Step]]
  ): Option[FetchedCopy] =
    val sp = spool()
    sp.write(citekey, Spool.Extension(f.extension.value), Spool.Bytes(f.bytes.value)) match
      case Right(key) => Some(FetchedCopy(key, FetchedCopy.Where(sp.where(key).value)))
      case Left(e)    => steps += step("spool", false, s"${e.sentence}"); None

  /** Patch just the `status` of a source (what `assess --apply` does), without disturbing anything else. */
  private def setStatus(
      citekey: Citekey,
      status: com.histmersion.domain.library.Source.Status
  ): Either[Api.Error, Source.UpdatedAt] =
    sources.patch(
      citekey,
      Source.Patch(metadata = Some(MetadataPatch(status = Some(status.transformInto[Source.Status])))),
      Caller.Local
    )

  // ── the maintenance lock: every acquisition WRITE goes through it ───────────────────────────

  def assess(req: AssessRequest): Either[Api.Error, Assessment] =
    maintenance.guard(WriteGate.OperationName("assess"))(assessNow(req))

  /** The verdict alone: nothing applied, nothing recorded (what an agent's dry run reads). */
  def assessReadOnly(id: AssessRequest.Id): Either[Api.Error, Assessment] =
    maintenance.guard(WriteGate.OperationName("assess"))(assessNow(AssessRequest(id), store = false))

  def followups(req: FollowupsRequest): Either[Api.Error, FollowupsResult] =
    maintenance.guard(WriteGate.OperationName("followups"))(followupsNow(req))

  /** A person's ingest; `legalOnly` asks the legal chain alone, and is otherwise the same. */
  def ingest(req: Ingest.Request): Either[Api.Error, Ingest.Report] =
    maintenance.guard(WriteGate.OperationName("acquire/ingest"))(
      ingestNow(
        req,
        if req.legalOnly.value then IngestPath.PersonLegal else IngestPath.Person
      )
    )

  /** [[ingest]] for a watcher's auto-ingest (docs/research-process-managers.md §4 (c)) and the acquisition list's
    * re-check: the LEGAL open-access chain only — no unauthorised fetcher is asked, whatever
    * `acquire.allow-unauthorised` says — and a work with no legal file is refused (Left) rather than stored as metadata
    * alone, so it goes to (or stays on) the acquisition list instead. An existing source with its original is never
    * fetched again; a metadata-only one named by `req.citekey` is fetched and converted into, keeping its enrichment.
    */
  def ingestLegal(req: Ingest.Request): Either[Api.Error, Ingest.Report] =
    maintenance.guard(WriteGate.OperationName("acquire/ingest"))(
      ingestNow(req.modify(_.refetch).setTo(Ingest.Request.Refetch(false)), IngestPath.Watcher)
    )

  /** The legal chain's generation now ([[FetcherGeneration]]): it changes when a legal fetcher or a fetcher plugin's
    * settings or key change, which makes the acquisition list's items due for a re-check again.
    */
  def fetcherGeneration: AcquireRecheck.LastCheck.Generation = AcquireRecheck.LastCheck.Generation(generation)

  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report] =
    maintenance.guard(WriteGate.OperationName("archive/import"))(archiveImportNow(item, file, req))

  def updateRetractions(): Either[Api.Error, RetractionsUpdate] =
    maintenance.guard(WriteGate.OperationName("retractions/update"))(updateRetractionsNow())

  // ── shared: what does the user's identifier point at? ───────────────────────────────────────

  final private case class Target(doi: Option[Doi], citekey: Option[Citekey])

  /** Resolve `<doi|arxiv:id|citekey>` to both a DOI (for the upstreams) and a stored citekey (for the writes),
    * whichever of the two the caller happened to give us.
    */
  private def target(id: String): Either[Api.Error, Target] =
    Ident.parse(Ident.Written(id)).left.map(e => err(e)).map {
      case Ident.Doi(doi) =>
        Target(Some(doi), sources.doiIndex.get(doi))
      case Ident.Arxiv(a) =>
        val doi = arxivDoi(a)
        Target(Some(doi), sources.doiIndex.get(doi))
      case Ident.Citekey(k) =>
        val ck = Citekey.option(k.value)
        Target(ck.flatMap(sources.load).flatMap(_.doi).flatMap(d => Doi.option(d.value)), ck.filter(sources.exists))
    }

object AcquisitionControllers:
  def hostOf(url: Option[String]): Option[String] =
    url
      .flatMap(u => scala.util.Try(java.net.URI.create(u).getHost).toOption)
      .flatMap(Option(_))
      .filter(_.nonEmpty)

  /** What original an existing source already has, in words — a converted body, else a stored file (`sources.sha256`) —
    * or None for a metadata-only source (or no source at all). `body` is None when the source does not exist.
    */
  def originalOf(body: Option[Option[Source.OcrMarkdown]], sha256: Option[Sha256]): Option[String] =
    body.flatMap { b =>
      if b.exists(_.value.trim.nonEmpty) then Some("a converted body")
      else sha256.map(sha => s"a stored original (sha256 ${sha.value.take(12)}…)")
    }
