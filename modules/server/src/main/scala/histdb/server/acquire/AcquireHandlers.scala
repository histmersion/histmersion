package histdb.server.acquire

import histdb.core.*
import histdb.core.Acquire.Ident
import histdb.db.{AcquireRepo, SourceRepo}
import histdb.server.Deps

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path

/** The ONE implementation of every acquisition & assessment operation (stream 2.6). REST routes and
  * MCP tools both call these; the CLI reaches them over HTTP — the same rule the rest of the server
  * follows, which is what makes "the CLI and the MCP tools agree" mechanical rather than hopeful.
  *
  * Everything network-facing arrives as a trait, so a test can drive the whole handler from captured
  * fixtures. Everything decision-making is a pure function in [[Acquire]].
  */
final class AcquireHandlers(
    deps: Deps,
    discovery: DiscoveryClient,
    citations: CitationsClient,
    oa: OaFetcher,
    wiki: WikiClient,
    archive: ArchiveClient,
    retractionWatch: RetractionWatchClient,
    jobs: IngestJobs
):
  private val ds  = deps.ds
  private val cfg = deps.cfg
  private val acq = deps.cfg.acquire

  private def err(msg: String): ApiError = ApiError(msg)

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
      discovery.search(query, since, sort, limit.filter(_ > 0).getOrElse(acq.defaults.searchLimit))
        .left.map(err)
        .map { works =>
          val marked = works.map(mark)
          val kept   = if notInDb then marked.filterNot(_.inDb) else marked
          DiscoverResult(query, kept.size, kept)
        }

  def meta(id: String): Either[ApiError, WorkRef] =
    Acquire.parseIdent(id).left.map(err).flatMap {
      case Ident.Doi(doi)  => discovery.meta(doi).left.map(err).map(mark)
      case Ident.Arxiv(a)  => discovery.meta(arxivDoi(a)).left.map(err).map(mark)
      case Ident.Citekey(k) =>
        SourceRepo.load(ds, k).toRight(Write.notFound(k)).flatMap { s =>
          s.doi.toRight(err(s"'$k' has no DOI recorded — nothing to look up upstream"))
            .flatMap(discovery.meta(_).left.map(err)).map(mark)
        }
    }

  def wikirefs(url: String): Either[ApiError, WikirefsResult] =
    wiki.refs(url).left.map(err).map { r =>
      val index = AcquireRepo.doiIndex(ds)
      r.copy(known = r.dois.filter(index.contains))
    }

  /** Fill in `inDb` and keep the citekey the corpus would use. */
  private def mark(w: WorkRef): WorkRef =
    val index    = AcquireRepo.doiIndex(ds)
    val byDoi    = w.doi.flatMap(index.get)
    val byKey    = w.citekey.filter(AcquireRepo.exists(ds, _))
    val resolved = byDoi.orElse(byKey)
    w.copy(inDb = resolved.isDefined, citekey = resolved.orElse(w.citekey))

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  /** The deposited reference list, with each entry resolved to a stored source where possible.
    * `apply` writes those as `references` edges out of the target. */
  def refs(req: RefsRequest): Either[ApiError, RefsResult] =
    target(req.id).flatMap { t =>
      t.doi.toRight(err(s"no DOI for '${req.id}' — a reference list needs one")).flatMap { doi =>
        discovery.references(doi).left.map(err).map { (backend, rows) =>
          val resolved = resolveRefs(rows)
          val added =
            if !req.apply then 0
            else
              t.citekey match
                case None => 0
                case Some(src) =>
                  AcquireRepo.addEdges(ds,
                    resolved.flatMap(_.citekey).distinct.filter(_ != src).map(Edge(src, _, "references")))
          RefsResult(req.id, Some(doi), backend, resolved.size, resolved.count(_.citekey.isDefined),
            added, resolved)
        }
      }
    }

  /** Match a reference to a stored source: by DOI first, then by the citekey the metadata would
    * generate, then by unique citekey PREFIX — the same widening `resolveEdges` does for short edge
    * targets (`heggarty2023` → `heggarty2023_language`). */
  private def resolveRefs(rows: List[RefRow]): List[RefRow] =
    val index    = AcquireRepo.doiIndex(ds)
    val citekeys = AcquireRepo.allCitekeys(ds)
    rows.map { r =>
      val byDoi = r.doi.flatMap(index.get)
      val guess = Acquire.citekey(r.authors, r.year, r.title)
      val byKey = Option.when(citekeys.contains(guess))(guess)
      val byPrefix =
        val stem = guess.takeWhile(_ != '_')
        val hits = citekeys.filter(k => k == stem || k.startsWith(stem + "_"))
        Option.when(hits.size == 1)(hits.head)
      r.copy(citekey = byDoi.orElse(byKey).orElse(byPrefix))
    }

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  def assess(req: AssessRequest): Either[ApiError, Assessment] =
    target(req.id).flatMap { t =>
      t.doi.toRight(err(s"no DOI for '${req.id}' — an assessment needs one")).map { doi =>
        val rw      = AcquireRepo.retractionsFor(ds, doi)
        val updates = discovery.updates(doi).getOrElse(Nil)
        val work    = discovery.meta(doi).toOption
        val stored  = t.citekey.flatMap(SourceRepo.load(ds, _))
        val cited   = work.flatMap(_.citedByCount).orElse(stored.flatMap(_.citedByCount))
        val year    = work.flatMap(_.year).orElse(stored.flatMap(_.year))
        val d       = acq.defaults
        val verdict = Acquire.verdict(rw, updates, work.exists(_.isRetracted), cited, year,
          d.wellCitedAt, d.lowCitedAt, d.staleBefore)

        val checkedAt = t.citekey.map { ck =>
          AcquireRepo.putAssessment(ds, ck, verdict, verdict == Acquire.Verdicts.Retracted,
            noticesJson(rw, updates), cited, stored.flatMap(_.influentialCitations))
        }
        val applied = for
          ck     <- t.citekey if req.apply
          status <- Acquire.statusFor(verdict)
          _      <- AcquireRepo.setStatus(ds, ck, status, cfg.limits.chunkChars).toOption
        yield status.toString.toLowerCase

        Assessment(
          target = req.id, doi = Some(doi), citekey = t.citekey, year = year, citedByCount = cited,
          influentialCitations = stored.flatMap(_.influentialCitations),
          isRetracted = verdict == Acquire.Verdicts.Retracted,
          retractionWatch = rw, crossrefUpdates = updates, flagged = Acquire.flagged(verdict),
          verdict = verdict, recommendation = Acquire.recommendation(verdict, cited),
          applied = applied, checkedAt = checkedAt)
      }
    }

  /** The notices, verbatim, so a later reader sees WHY a verdict was reached without asking Crossref
    * again. */
  private def noticesJson(rw: List[RetractionNotice], up: List[UpdateNotice]): String =
    Json.obj("retractionWatch" -> rw.asJson, "crossrefUpdates" -> up.asJson).noSpaces

  /** Refresh the local Retraction Watch mirror — our ground truth, so an assessment never depends on
    * a third party being up at the moment somebody asks.
    *
    * The work is ~30 MB of CSV and ~60 000 rows, which does not fit in one HTTP request (the same
    * constraint that made `reindex` resumable), so this STARTS the refresh on a virtual thread and
    * returns immediately; callers poll [[retractionsStatus]]. The two fields of mutable state below
    * are the price of that: a flag so two callers cannot run it twice, and the last error so a failed
    * refresh is visible instead of silent.
    */
  def updateRetractions(): Either[ApiError, RetractionsUpdate] =
    if !AcquireHandlers.rwRunning.compareAndSet(false, true) then Right(retractionsStatus)
    else
      AcquireHandlers.rwError.set(None)
      val _ = Thread.ofVirtual().name("retraction-watch-refresh").start(() =>
        try
          retractionWatch.csv() match
            case Left(e) =>
              scribe.warn(s"acquire: Retraction Watch refresh failed — $e")
              AcquireHandlers.rwError.set(Some(e))
            case Right(csv) =>
              val n = AcquireRepo.replaceRetractionWatch(ds, Acquire.parseRetractionWatch(csv))
              scribe.info(s"acquire: Retraction Watch refreshed — $n row(s)")
        catch
          case e: Throwable =>
            scribe.error(s"acquire: Retraction Watch refresh crashed: ${e.getMessage}", e)
            AcquireHandlers.rwError.set(Some(e.getMessage))
        finally AcquireHandlers.rwRunning.set(false))
      Right(retractionsStatus.copy(running = true))

  /** How the mirror is doing: how many rows it holds, whether a refresh is in flight, and the last
    * failure if there was one. */
  def retractionsStatus: RetractionsUpdate =
    RetractionsUpdate(AcquireRepo.retractionCount(ds), acq.retractionWatch.csvUrl,
      running = AcquireHandlers.rwRunning.get, error = AcquireHandlers.rwError.get)

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  def followups(req: FollowupsRequest): Either[ApiError, FollowupsResult] =
    target(req.id).flatMap { t =>
      t.doi.toRight(err(s"no DOI for '${req.id}' — the citation graph is keyed by DOI"))
        .flatMap { doi =>
          val work = discovery.meta(doi).getOrElse(WorkRef(doi = Some(doi)))
          val cap  = req.limit.filter(_ > 0).getOrElse(acq.defaults.followupsLimit)
          citations.citing(work, req.since, cap).left.map(err).map { (backend, rows) =>
            val index    = AcquireRepo.doiIndex(ds)
            val enriched = Acquire.rankFollowups(rows).map { r =>
              r.copy(
                proposedKind = Acquire.classify(r.intents, r.contexts, r.isInfluential, r.year, work.year),
                citekey = r.doi.flatMap(index.get))
            }
            // an edge can only start at a source we actually hold: the citing paper must be in the
            // corpus, and so must the target
            val added = (req.apply, t.citekey) match
              case (true, Some(dst)) =>
                AcquireRepo.addEdges(ds, enriched.flatMap(r =>
                  r.citekey.filter(_ != dst).map(src => Edge(src, dst, r.proposedKind))))
              case _ => 0
            FollowupsResult(req.id, Some(doi), backend, enriched.size, added, enriched)
          }
        }
    }

  // ── ingest by identifier ────────────────────────────────────────────────────────────────────

  /** `histdb ingest <doi|arxiv:id>`: metadata → legal OA PDF → the ingest job → reference edges →
    * assessment. Every step is reported, including the ones that failed, because "no OA copy" is a
    * normal outcome that the user answers with a manual drop-in. */
  def ingest(req: IngestRequest): Either[ApiError, IngestReport] =
    Acquire.parseIdent(req.id).left.map(err).flatMap { ident =>
      val steps = List.newBuilder[IngestStep]

      val (doi, arxiv) = ident match
        case Ident.Doi(d)     => (Some(d), req.arxiv.flatMap(Acquire.normalizeArxiv))
        case Ident.Arxiv(a)   => (Some(arxivDoi(a)), Some(a))
        case Ident.Citekey(k) => (SourceRepo.load(ds, k).flatMap(_.doi), req.arxiv.flatMap(Acquire.normalizeArxiv))

      val work = doi.flatMap(discovery.meta(_).toOption)
      work match
        case Some(w) => steps += IngestStep("metadata", true,
          s"${w.title.getOrElse("(untitled)")} (${w.year.map(_.toString).getOrElse("?")}), " +
            s"${w.citedByCount.getOrElse(0)} citation(s)")
        case None => steps += IngestStep("metadata", false,
          s"no upstream record for ${doi.getOrElse(req.id)}")

      val explicit = req.citekey.map(_.trim).filter(_.nonEmpty)
      val existing = ident match
        case Ident.Citekey(k) => Some(k)
        case _                => None
      val citekey = explicit.orElse(work.flatMap(_.citekey)).orElse(existing)

      // METADATA GUARD. Without a title from an upstream we have nothing to name the source after,
      // and inventing one from the DOI produces exactly the `anonnd`-style junk entry the Calibre
      // guard exists to prevent. A typo'd or unregistered DOI must be an actionable error, not a row.
      if work.flatMap(_.title).isEmpty && citekey.isEmpty then
        Left(err(
          s"no upstream record for '${req.id}' (OpenAlex and Crossref both miss it) — check the " +
            "identifier, or pass --citekey to create the source anyway and fill the metadata in by hand"))
      else
        ingestWith(doi, arxiv, work, citekey.get, steps)
    }

  /** The rest of an ingest, once we know what the source will be CALLED. */
  private def ingestWith(
      doi: Option[String],
      arxiv: Option[String],
      work: Option[WorkRef],
      citekey: String,
      steps: scala.collection.mutable.Builder[IngestStep, List[IngestStep]]
  ): Either[ApiError, IngestReport] =
    {

      // ── the PDF: legal OA first, the unauthorised mirror only when a deployment switched it on ──
      val fetched = oa.fetch(doi, arxiv.orElse(work.flatMap(_.arxivId)))
      fetched match
        case Right(f) => steps += IngestStep("fetch", true, s"${f.source}: ${f.bytes.length} bytes")
        case Left(e)  => steps += IngestStep("fetch", false, e)

      val spooled: Option[Path] = fetched.toOption.flatMap { f =>
        Spool.write(acq.spoolDir, citekey, f.extension, f.bytes) match
          case Right(p) => Some(p)
          case Left(e)  => steps += IngestStep("spool", false, e); None
      }

      // ── reference edges, before the source is written, so they land with it ──
      val refRows = doi.toList.flatMap(d => discovery.references(d).toOption.toList.flatMap(_._2))
      val edges   = resolveRefs(refRows).flatMap(_.citekey).distinct.filter(_ != citekey)
        .map(Edge(citekey, _, "references"))
      steps += IngestStep("references", refRows.nonEmpty,
        s"${refRows.size} reference(s), ${edges.size} resolve to stored sources")

      val meta = IngestMeta(
        title = work.flatMap(_.title).getOrElse(citekey),
        authors = work.map(_.authors).getOrElse(Nil),
        doi = doi,
        year = work.flatMap(_.year),
        venue = work.flatMap(_.venue),
        summary = work.flatMap(_.abstractText),
        provenance = fetched.map(_.source).getOrElse("oa"),
        edges = edges,
        citedByCount = work.flatMap(_.citedByCount))

      jobs.enqueue(spooled, citekey, meta).left.map(err).map { outcome =>
        steps += IngestStep("store", true, outcome.detail)

        // assess last: the source now exists, so the verdict can be recorded against it. The status is
        // written for a source we JUST created, and for a retraction whatever the source's age — a
        // retracted paper must never keep a curated "current".
        val assessment = doi.flatMap { _ =>
          assess(AssessRequest(citekey, apply = false)).toOption.map { a =>
            val shouldApply = outcome.created || a.verdict == Acquire.Verdicts.Retracted
            val applied = for
              status <- Acquire.statusFor(a.verdict) if shouldApply
              _      <- AcquireRepo.setStatus(ds, citekey, status, cfg.limits.chunkChars).toOption
            yield status.toString.toLowerCase
            steps += IngestStep("assess", true, s"${a.verdict}${applied.fold("")(s => s" → status=$s")}")
            a.copy(applied = applied)
          }
        }

        IngestReport(
          citekey = citekey,
          doi = doi,
          title = work.flatMap(_.title),
          created = outcome.created,
          pdf = fetched.toOption.map(f =>
            PdfInfo(f.source, f.url, f.bytes.length.toLong, spooled.map(_.toString))),
          edgesAdded = edges.size,
          assessment = assessment,
          steps = steps.result(),
          dropIn = Option.when(fetched.isLeft)(
            s"No legal open-access PDF exists for this one. Get it through institutional access, " +
              s"an interlibrary loan or the author, then run: histdb ingest <file.pdf> --citekey $citekey"))
      }
    }

  /** arXiv mints a DataCite DOI for every submission, which is how OpenAlex indexes preprints. */
  private def arxivDoi(id: String): String = s"10.48550/arxiv.$id"

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  def archiveSearch(q: String, rows: Option[Int]): Either[ApiError, ArchiveSearchResult] =
    val query = q.trim
    if query.isEmpty then Left(err("give a search query"))
    else
      archive.search(query, rows.filter(_ > 0).getOrElse(acq.defaults.archiveRows)).left.map(err)
        .map(items => ArchiveSearchResult(query, items.size, items))

  def archiveInfo(id: String): Either[ApiError, ArchiveItem] = archive.info(id).left.map(err)

  /** Import a PUBLIC archive.org item. A lending-only scan is refused, not worked around: it must be
    * borrowed on archive.org, and circumventing that is not something this pipeline does. */
  def archiveImport(req: ArchiveImportRequest): Either[ApiError, IngestReport] =
    archive.info(req.id).left.map(err).flatMap { item =>
      if item.lendingOnly then
        Left(err(s"'${item.identifier}' is a lending-only item — borrow it on archive.org; " +
          "it is not downloadable here (no DRM circumvention)"))
      else
        AcquireParse.pickArchiveFile(item.files, req.format)
          .toRight(err(s"'${item.identifier}' has no epub/pdf file to download"))
          .flatMap { file =>
            val steps   = List.newBuilder[IngestStep]
            val citekey = req.citekey.map(_.trim).filter(_.nonEmpty)
              .getOrElse(Acquire.citekey(item.creator, item.year, item.title))
            steps += IngestStep("metadata", true,
              s"${item.title.getOrElse(item.identifier)} (${item.year.map(_.toString).getOrElse("?")})")
            archive.download(item.identifier, file.name).left.map(err).flatMap { f =>
              steps += IngestStep("fetch", true, s"archive.org: ${f.bytes.length} bytes (${file.name})")
              val spooled = Spool.write(acq.spoolDir, citekey, f.extension, f.bytes) match
                case Right(p) => Some(p)
                case Left(e)  => steps += IngestStep("spool", false, e); None
              jobs.enqueue(spooled, citekey, IngestMeta(
                title = item.title.getOrElse(citekey),
                authors = item.creator,
                year = item.year,
                venue = item.publisher,
                summary = item.description,
                provenance = "archive_org")).left.map(err).map { outcome =>
                steps += IngestStep("store", true, outcome.detail)
                IngestReport(citekey, None, item.title, outcome.created,
                  Some(PdfInfo("archive_org", None, f.bytes.length.toLong, spooled.map(_.toString))),
                  0, None, steps.result())
              }
            }
          }
    }

  // ── Calibre gaps (the push itself runs on the host) ─────────────────────────────────────────

  def calibreGaps(limit: Option[Int]): CalibreGaps =
    val gaps = AcquireRepo.calibreGaps(ds, limit)
    CalibreGaps(gaps.size, gaps)

  def calibreMeta(citekey: String): Either[ApiError, CalibreGap] =
    AcquireRepo.calibrePushMeta(ds, citekey).toRight(Write.notFound(citekey))

  def calibrePushed(req: CalibrePushRequest): Either[ApiError, CalibrePushResult] =
    AcquireRepo.setCalibreId(ds, req.citekey, req.calibreId)
      .map(_ => CalibrePushResult(req.citekey, req.calibreId, ok = true))

  // ── shared: what does the user's identifier point at? ───────────────────────────────────────

  private final case class Target(doi: Option[String], citekey: Option[String])

  /** Resolve `<doi|arxiv:id|citekey>` to both a DOI (for the upstreams) and a stored citekey (for the
    * writes), whichever of the two the caller happened to give us. */
  private def target(id: String): Either[ApiError, Target] =
    Acquire.parseIdent(id).left.map(err).map {
      case Ident.Doi(doi) =>
        Target(Some(doi), AcquireRepo.doiIndex(ds).get(doi))
      case Ident.Arxiv(a) =>
        val doi = arxivDoi(a)
        Target(Some(doi), AcquireRepo.doiIndex(ds).get(doi))
      case Ident.Citekey(k) =>
        Target(SourceRepo.load(ds, k).flatMap(_.doi), Option.when(AcquireRepo.exists(ds, k))(k))
    }

object AcquireHandlers:
  /** The Retraction Watch refresh runs in the background (see [[AcquireHandlers.updateRetractions]]).
    * These live on the companion, not the instance, because the handler is constructed per server and
    * the invariant we need — at most ONE refresh in flight — is per process. */
  private val rwRunning = java.util.concurrent.atomic.AtomicBoolean(false)
  private val rwError   = java.util.concurrent.atomic.AtomicReference[Option[String]](None)
