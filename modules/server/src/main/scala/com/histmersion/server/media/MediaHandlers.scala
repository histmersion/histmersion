package com.histmersion.server.media

import hearth.kindlings.optics.syntax.*
import com.histmersion.pluginhost.{HttpTransport, JavaHttpTransport}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.server.{BackgroundJob, ConverterClient, Deps}

import java.util.concurrent.atomic.AtomicReference

/** Media from outside providers (#1446): the import (one run, or a JOB over every place not asked yet), the sources'
  * pictures (covers and illustrations), the list and its facets, one picture, the bytes the cards and the media layer
  * show, and the figure-licence fill. Reached through `Handlers.media`, so REST and the CLI share this one
  * implementation.
  *
  * Not an MCP tool, on purpose: an import is an operator's decision that sends traffic to a third party, and a picture
  * is for the reader of the web UI — an agent compiling prose has no use for either (its `places`/`events` tools say
  * nothing new with a thumbnail URL). `lint --rule media-licence` is how an agent sees the media's state.
  */
final class MediaHandlers(deps: Deps, http: HttpTransport, converter: ConverterClient):

  private val ds = deps.ds
  private def cfg = deps.cfg

  def this(deps: Deps, http: HttpTransport) = this(deps, http, MediaHandlers.NoThumbnails)
  def this(deps: Deps, converter: ConverterClient) =
    this(deps, JavaHttpTransport(deps.cfg.media.timeoutSeconds), converter)
  def this(deps: Deps) = this(deps, MediaHandlers.NoThumbnails)

  val pictures: SourcePictures = SourcePictures(ds, cfg.media, http, converter)

  /** What runs now — an import, a job — one at a time: the polite pace is per run, and two runs would ask twice as
    * fast. `None` when idle.
    */
  private val busy = AtomicReference[Option[String]](None)

  private def claim(what: String): Either[ApiError, Unit] =
    if busy.compareAndSet(None, Some(what)) then Right(())
    else
      Left(
        ApiError(
          s"a media run is already going (${busy.get.getOrElse("?")}) — see `histmersion jobs`; try again when it ends"
        )
      )

  private val Uuid = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  private def notFound(uid: String) = ApiError(s"No picture '$uid'", Some(Write.CodeNotFound))

  /** The importer an import request names, from the media collections the plugins of the calling thread's generation
    * registered (built-in and third-party), built for this server's config — or why it cannot run (unknown, its plugin
    * switched off, or its own `media.<provider>.enabled` off).
    */
  private[media] def providerOf(p: String): Either[ApiError, MediaImporter] =
    MediaImporters
      .resolve(p, deps.plugins.registries.mediaCollections, deps, http, converter)
      .left
      .map(ApiError(_, Some(Write.CodeInvalid)))

  /** `GET /api/media/providers`: what `media import` can run, as the registries hold it. */
  def providers: List[MediaProviderInfo] =
    val gen = deps.plugins
    val owner = gen.contributions.toList.flatMap((id, r) => r.mediaCollections.keys.map(_ -> id)).toMap
    MediaImporters.infos(
      gen.registries.mediaCollections,
      n => owner.getOrElse(n, ""),
      n => cfg.media.collections.licencesFor(n)
    )

  /** One import run, answered when it ends: a dry run (at most `media.max-limit`), or a real one of ONE place or event.
    * A real run over many is a job ([[importJob]], #1491): asked here it outlived the CLI's request while the server
    * kept going.
    */
  def importMedia(req: MediaImportRequest): Either[ApiError, MediaImportReport] =
    def go: Either[ApiError, MediaImportReport] =
      providerOf(req.provider).flatMap { imp =>
        claim("media import").flatMap { _ =>
          try
            imp
              .targets(req.place, req.event, cfg.media.limit(req.limit), req.refresh, None)
              .left
              .map(ApiError(_, Some(Write.CodeNotFound)))
              .map { targets =>
                val run = imp.start()
                val report = run.run(targets, req.dryRun, req.refresh)
                scribe.info(
                  s"media import ${imp.check} (${if req.dryRun then "dry run" else "stored"}): ${targets.size} target(s), " +
                    s"${report.files} file(s), ${report.stored} stored, ${run.requests} request(s)"
                )
                report.copy(pending = imp.pending(None))
              }
          finally busy.set(None)
        }
      }
    if req.dryRun then go
    else if !MediaHandlers.single(req) then
      Left(
        ApiError(
          "an import over many places/events is a job: POST /api/media/import/job " +
            "(`histmersion media import wikidata` starts and follows it)",
          Some(Write.CodeInvalid)
        )
      )
    else deps.maintenance.guard("media_import")(go)

  // ── jobs ──────────────────────────────────────────────────────────────────────────────────────

  /** Media jobs share the one-place runs' `busy` mark: one media run of any sort at a time. */
  private val jobSlot = BackgroundJob.Slot(busy)

  /** Start `body` as a `jobs` row of `kind` ([[BackgroundJob]]): `histmersion jobs <id>` follows it, a restart fails it
    * as `interrupted` like every other unfinished job, and a person may pause, resume or cancel it between its batches.
    * `body` returns the summary the finished job shows, or why it failed.
    */
  private def startJob(kind: String, describe: String)(
      body: (String => Unit) => Either[String, String]
  ): Either[ApiError, JobInfo] =
    deps.maintenance.guard(kind) {
      BackgroundJob.start(
        ds,
        kind,
        Some(JobDetail(media = Some(describe))),
        jobSlot,
        "a media run",
        pins = deps.pins,
        stops = Some(deps.jobStops)
      )((_, progress) => BackgroundJob.Outcome.of(body(progress)))
    }

  /** The import as a job: `media.import-batch` items at a time, most-cited sites first, each batch recorded before the
    * next (so a stopped job resumes after the last one), until none is left, `limit` targets are done, or a batch gets
    * no answer at all.
    */
  def importJob(req: MediaImportRequest): Either[ApiError, JobInfo] =
    providerOf(req.provider).flatMap { imp =>
      if req.dryRun then Left(ApiError("a dry run answers at once: POST /api/media/import", Some(Write.CodeInvalid)))
      else
        val single = MediaHandlers.single(req)
        // the one target must exist (and have an item) before a job is started for it
        val check =
          if single then
            imp
              .targets(req.place, req.event, 1, req.refresh, None)
              .left
              .map(ApiError(_, Some(Write.CodeNotFound)))
              .map(_ => ())
          else Right(())
        check.flatMap { _ =>
          val cap = req.limit.filter(_ > 0)
          val what =
            (if single then s"${imp.title} of ${req.place.orElse(req.event).getOrElse("")}"
             else s"${imp.title} of ${cap.fold(s"every ${imp.over}")(n => s"up to $n ${imp.over}")} not asked yet") +
              (if req.refresh then " (refresh)" else "")
          // a refresh asks again what was asked before this job started — a resumed job too
          val since = Option.when(req.refresh)(System.currentTimeMillis())
          startJob(MediaHandlers.ImportJobKind, what) { progress =>
            Right(MediaHandlers.summary(runImport(imp, req, single, cap, since, progress)))
          }
        }
    }

  /** The job's body. Progress is `n/total` (#1491): the total is counted once, at the start — the places/events not
    * asked yet (since this job's start, for a refresh), capped by `limit`. A failed picture is a line in the report,
    * never the end of the job; a batch that throws is reported and ends it (its targets are not recorded as asked, so
    * the next job resumes there, not past it).
    */
  private def runImport(
      imp: MediaImporter,
      req: MediaImportRequest,
      single: Boolean,
      limit: Option[Int],
      since: Option[Long],
      progress: String => Unit
  ): MediaImportReport =
    val run = imp.start()
    val batch = imp.batch.max(1)
    val cap = limit.getOrElse(Int.MaxValue)
    progress(s"counting the ${imp.over} not asked yet")
    val total = if single then 1 else imp.pending(since).min(cap)
    @annotation.tailrec
    def loop(acc: MediaImportReport, seen: Set[String]): MediaImportReport =
      val left = cap - acc.targets
      if left <= 0 || deps.maintenance.active then acc
      else
        imp.targets(req.place, req.event, batch.min(left), req.refresh, since) match
          case Left(e)    => acc.modify(_.failed).using(_ :+ e)
          case Right(Nil) => acc
          // none of the last batch's checks were recorded: asking again would spin on the same ones
          case Right(targets) if !single && targets.forall(t => seen(s"${t.targetType}:${t.id}")) =>
            acc
              .modify(_.failed)
              .using(_ :+ s"${targets.size} place(s)/event(s) came back unrecorded (${targets.head.id}…); stopped")
          case Right(targets) =>
            scala.util.Try(run.run(targets, dryRun = false, req.refresh)) match
              case scala.util.Failure(e: InterruptedException) => throw e
              case scala.util.Failure(e)                       =>
                scribe.error(s"media import job: batch ${acc.batches + 1} failed: ${e.getMessage}", e)
                acc
                  .modify(_.failed)
                  .using(
                    _ :+ s"batch ${acc.batches + 1} (${targets.head.id}…): " +
                      s"${e.getClass.getSimpleName}: ${e.getMessage}"
                  )
              case scala.util.Success(r) =>
                val next = MediaImportReport.combine(acc, r.copy(batches = 1))
                progress(MediaHandlers.progressLine(next, total, run.requests))
                // a batch the provider did not answer at all ends the job (the next one resumes) rather than spin
                if single || (r.items == 0 && r.failed.nonEmpty) then next
                else loop(next, seen ++ targets.map(t => s"${t.targetType}:${t.id}"))
    val out = loop(MediaImportReport(imp.check, dryRun = false), Set.empty)
    scribe.info(
      s"media import job ${imp.check}: ${out.targets} target(s) in ${out.batches} batch(es), ${out.stored} stored, " +
        s"${run.requests} request(s), ${out.failed.size} failed"
    )
    out.failed.take(50).foreach(f => scribe.warn(s"media import job: $f"))
    out.copy(pending = imp.pending(since))

  /** The sources' pictures, one run: a dry run (from the database alone), or — for one `citekey` — done at once. A run
    * over every source is [[coversJob]].
    */
  def covers(req: MediaCoversRequest): Either[ApiError, MediaCoversReport] =
    if req.dryRun then Right(pictures.run(req))
    else if req.citekey.forall(_.trim.isEmpty) then
      Left(ApiError("a run over every source is a job: POST /api/media/covers/job", Some(Write.CodeInvalid)))
    else
      deps.maintenance.guard("media_covers") {
        claim("media covers").map { _ =>
          try pictures.run(req)
          finally busy.set(None)
        }
      }

  def coversJob(req: MediaCoversRequest): Either[ApiError, JobInfo] =
    val what =
      List(Option.when(req.covers)("Open Library covers"), Option.when(req.illustrations)("illustrations")).flatten
        .mkString(" and ") + req.citekey.fold(" of every source")(c => s" of $c") +
        (if req.refresh then " (refresh)" else "")
    startJob(MediaHandlers.CoversJobKind, what) { progress =>
      val r = pictures.run(req.copy(dryRun = false), progress)
      Right(
        s"${r.sources} source(s) · ${r.covers} cover(s) · ${r.illustrations} illustration(s)" +
          (if r.failed.isEmpty then "" else s" · ${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}")
      )
    }

  /** A cover the CLI read from the local Calibre library. */
  def putCover(p: SourceCoverParams, bytes: Array[Byte]): Either[ApiError, MediaItem] =
    deps.maintenance.guard("media_cover")(pictures.storeCover(p, bytes))

  /** After a conversion stored a source's figures (the OCR job's hook). */
  def afterIngest(citekey: String): Option[String] = pictures.afterIngest(citekey)

  // ── reads ─────────────────────────────────────────────────────────────────────────────────────

  def list(q: Api.MediaQuery): MediaList = MediaRepo.list(ds, q, cfg.limits.page(q.limit, 50))

  def facets: MediaFacets = MediaRepo.facets(ds)

  def show(uid: String): Either[ApiError, MediaItem] =
    Option(uid.trim).filter(_.matches(Uuid)).flatMap(MediaRepo.show(ds, _)).toRight(notFound(uid))

  def thumb(uid: String): Either[ApiError, (Array[Byte], String)] =
    Option(uid.trim)
      .filter(_.matches(Uuid))
      .flatMap(MediaRepo.thumb(ds, _))
      .map((m, b) => (b, m))
      .toRight(notFound(uid))

  def file(uid: String): Either[ApiError, (Array[Byte], String)] =
    Option(uid.trim).filter(_.matches(Uuid)).flatMap(MediaRepo.file(ds, _)).map((m, b) => (b, m)).toRight(notFound(uid))

  /** A place's pictures for its card, through its equivalents ([[MediaRepo.placeThumbsIn]]). */
  def placeThumbs(placeId: String): List[MediaThumb] = MediaRepo.placeThumbs(ds, placeId, cfg.media.sameSiteKm)

  /** A source's picture for its hover card. */
  def sourceThumb(citekey: String): Option[MediaThumb] = MediaRepo.sourceThumb(ds, citekey)

  def fillLicences(apply: Boolean): Either[ApiError, FigureLicenceFill] =
    if apply then deps.maintenance.guard("media_fill_licences")(Right(MediaRepo.fillFigureLicences(ds, apply = true)))
    else Right(MediaRepo.fillFigureLicences(ds, apply = false))

object MediaHandlers:
  val ImportJobKind = com.histmersion.api.JobKind.MediaImport.wire
  val CoversJobKind = com.histmersion.api.JobKind.MediaCovers.wire

  /** A converter that makes no thumbnails (tests; a server whose sidecar is down still serves cards). */
  object NoThumbnails extends ConverterClient:
    def convert(file: java.nio.file.Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] =
      Left("no converter")
    def health: Either[String, String] = Left("no converter")

  /** Pure: does the request name one place or event (not "every one not asked yet")? */
  def single(req: MediaImportRequest): Boolean =
    req.place.exists(_.trim.nonEmpty) || req.event.exists(_.trim.nonEmpty)

  /** Pure: a running import job's progress line — `n/total` asked (a total of 0 or less is unknown, and a total passed
    * by the asked count is the asked count: places cited meanwhile join in).
    */
  def progressLine(r: MediaImportReport, total: Int, requests: Int): String =
    val of = if total > 0 then s"/${total.max(r.targets)}" else ""
    List(
      Some(s"${r.targets}$of place(s)/event(s) asked"),
      Some(s"batch ${r.batches}"),
      Some(s"${r.stored} stored · ${r.linked} linked"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed"),
      Some(s"$requests request(s)")
    ).flatten.mkString(" · ")

  /** Pure: a finished import job's line in `histmersion jobs`. */
  def summary(r: MediaImportReport): String =
    List(
      Some(s"${r.targets} place(s)/event(s) asked in ${r.batches} batch(es)"),
      Some(s"${r.files} file(s) · ${r.stored} stored · ${r.updated} refreshed · ${r.linked} link(s)"),
      Option.when(r.rejected.nonEmpty)("not stored: " + r.rejected.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.revoked.nonEmpty)(s"${r.revoked.size} licence(s) no longer allowed (kept, review)"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
      Some(s"${r.pending} not asked yet")
    ).flatten.mkString(" · ")
