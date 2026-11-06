package com.histmersion.domain.media.imports

import com.histmersion.api.Write
import com.histmersion.config.MediaConfig
import com.histmersion.domain.media.MediaJobs
import com.histmersion.plugin.MediaCollection
import com.histmersion.pluginhost.HttpTransport
import com.histmersion.util.ocr.ConverterClient

import hearth.kindlings.optics.syntax.*

import scala.collection.immutable.ListMap
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{MediaImportReport, MediaImportRequest}
import com.histmersion.api.Api
import com.histmersion.api.media.MediaProvider

/** Media from outside providers (#1446): the collections `media import` can run, one import run (a dry run, or one
  * place or event), and the import as a JOB over every place not asked yet. Every collection — first-party or a
  * plugin's — comes from the calling thread's generation (`collections`, with the plugin that registered each) and is
  * run by the host's one import under the allowlist the host keeps for it. `limits` gives a request's limit its bounds.
  *
  * Not an MCP tool, on purpose: an import is an operator's decision that sends traffic to a third party. `lint --rule
  * media-licence` is how an agent sees the media's state.
  */
final class ImportsControllers(
    imports: ImportService,
    store: MediaStore,
    jobs: MediaJobs,
    collections: () => (ListMap[ImportsControllers.CollectionsResultPart1Key, MediaCollection], Map[ImportsControllers.CollectionsResultPart2Key, ImportsControllers.CollectionsResultPart2Value]),
    cfg: () => MediaConfig,
    http: HttpTransport,
    converter: ConverterClient,
    clock: java.time.Clock
):

  /** The importer an import request names, or why it cannot run (unknown, its plugin switched off, or its own
    * `media.<provider>.enabled` off).
    */
  private def providerOf(p: com.histmersion.domain.media.imports.ImportsControllers.ProviderOfP): Either[Api.Error, MediaImporter] =
    MediaImporters
      .resolve(p, collections()._1, imports, store, cfg, http, converter)
      .left
      .map(Api.Error(_, Some(Write.CodeInvalid)))

  /** `GET /api/media/providers`: what `media import` can run, as the registries hold it. */
  def providers: List[MediaProvider.Info] =
    val (registered, owner) = collections()
    MediaImporters.infos(registered, n => owner.getOrElse(n, ""), n => cfg().collections.licencesFor(n))

  /** One import run, answered when it ends: a dry run (at most `media.max-limit`), or a real one of ONE place or event.
    * A real run over many is a job ([[importJob]], #1491): asked here it outlived the CLI's request while the server
    * kept going.
    */
  def importMedia(req: MediaImportRequest): Either[Api.Error, MediaImportReport] =
    def go: Either[Api.Error, MediaImportReport] =
      providerOf(req.provider).flatMap { imp =>
        jobs.exclusive("media import") {
          imp
            .targets(req.place, req.event, cfg().limit(req.limit), req.refresh, None)
            .left
            .map(Api.Error(_, Some(Write.CodeNotFound)))
            .map { targets =>
              val run = imp.start()
              val report = run.run(targets, req.dryRun, req.refresh)
              scribe.info(
                s"media import ${imp.check} (${if req.dryRun then "dry run" else "stored"}): ${targets.size} target(s), " +
                  s"${report.files} file(s), ${report.stored} stored, ${run.requests} request(s)"
              )
              report.modify(_.pending).setTo(imp.pending(None))
            }
        }
      }
    if req.dryRun then go
    else if !ImportsControllers.single(req) then
      Left(
        Api.Error(
          "an import over many places/events is a job: POST /api/media/import/job " +
            "(`histmersion media import wikidata` starts and follows it)",
          Some(Write.CodeInvalid)
        )
      )
    else jobs.guarded("media_import")(go)

  /** The import as a job: `media.import-batch` items at a time, most-cited sites first, each batch recorded before the
    * next (so a stopped job resumes after the last one), until none is left, `limit` targets are done, or a batch gets
    * no answer at all.
    */
  def importJob(req: MediaImportRequest): Either[Api.Error, JobInfo] =
    providerOf(req.provider).flatMap { imp =>
      if req.dryRun then Left(Api.Error("a dry run answers at once: POST /api/media/import", Some(Write.CodeInvalid)))
      else
        val single = ImportsControllers.single(req)
        // the one target must exist (and have an item) before a job is started for it
        val check =
          if single then
            imp
              .targets(req.place, req.event, 1, req.refresh, None)
              .left
              .map(Api.Error(_, Some(Write.CodeNotFound)))
              .map(_ => ())
          else Right(())
        check.flatMap { _ =>
          val cap = req.limit.filter(_ > 0)
          val what =
            (if single then s"${imp.title} of ${req.place.orElse(req.event).getOrElse("")}"
             else s"${imp.title} of ${cap.fold(s"every ${imp.over}")(n => s"up to $n ${imp.over}")} not asked yet") +
              (if req.refresh then " (refresh)" else "")
          // a refresh asks again what was asked before this job started — a resumed job too
          val since = Option.when(req.refresh)(clock.millis())
          jobs.start(ImportsControllers.ImportJobKind, what) { progress =>
            Right(ImportsControllers.summary(runImport(imp, req, single, cap, since, progress)))
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
      single: com.histmersion.domain.media.imports.ImportsControllers.RunImportSingle,
      limit: Option[com.histmersion.domain.media.imports.ImportsControllers.RunImportLimit],
      since: Option[com.histmersion.domain.media.imports.ImportsControllers.RunImportSince],
      progress: com.histmersion.domain.media.imports.ImportsControllers.RunImportProgressArg1 => Unit
  ): MediaImportReport =
    val run = imp.start()
    val batch = imp.batch.max(1)
    val cap = limit.getOrElse(Int.MaxValue)
    progress(s"counting the ${imp.over} not asked yet")
    val total = if single then 1 else imp.pending(since).min(cap)
    @annotation.tailrec
    def loop(acc: MediaImportReport, seen: Set[com.histmersion.domain.media.imports.ImportsControllers.RunImportLoopSeen]): MediaImportReport =
      val left = cap - acc.targets
      if left <= 0 || jobs.held then acc
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
                val next = MediaImportReport.combine(acc, r.modify(_.batches).setTo(1))
                progress(ImportsControllers.progressLine(next, total, run.requests))
                // a batch the provider did not answer at all ends the job (the next one resumes) rather than spin
                if single || (r.items == 0 && r.failed.nonEmpty) then next
                else loop(next, seen ++ targets.map(t => s"${t.targetType}:${t.id}"))
    val out = loop(MediaImportReport(imp.check, dryRun = false), Set.empty)
    scribe.info(
      s"media import job ${imp.check}: ${out.targets} target(s) in ${out.batches} batch(es), ${out.stored} stored, " +
        s"${run.requests} request(s), ${out.failed.size} failed"
    )
    out.failed.take(50).foreach(f => scribe.warn(s"media import job: $f"))
    out.modify(_.pending).setTo(imp.pending(since))

object ImportsControllers:
  type RunImportLoopSeen = RunImportLoopSeen.T
  object RunImportLoopSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CollectionsResultPart1Key = CollectionsResultPart1Key.T
  object CollectionsResultPart1Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionsResultPart2Key = CollectionsResultPart2Key.T
  object CollectionsResultPart2Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionsResultPart2Value = CollectionsResultPart2Value.T
  object CollectionsResultPart2Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ProviderOfP = ProviderOfP.T
  object ProviderOfP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunImportSingle = RunImportSingle.T
  object RunImportSingle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunImportLimit = RunImportLimit.T
  object RunImportLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunImportSince = RunImportSince.T
  object RunImportSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunImportProgressArg1 = RunImportProgressArg1.T
  object RunImportProgressArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportJobKind = ImportJobKindValue
  type ImportJobKindValue = ImportJobKindValue.T
  object ImportJobKindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingleResult = SingleResult.T
  object SingleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressLineResult = ProgressLineResult.T
  object ProgressLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressLineTotal = ProgressLineTotal.T
  object ProgressLineTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressLineRequests = ProgressLineRequests.T
  object ProgressLineRequests extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryResult = SummaryResult.T
  object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ImportJobKind: com.histmersion.domain.media.imports.ImportsControllers.ImportJobKind = com.histmersion.api.media.ImportJobKinds.MediaImport.kind.value

  /** Pure: does the request name one place or event (not "every one not asked yet")? */
  def single(req: MediaImportRequest): com.histmersion.domain.media.imports.ImportsControllers.SingleResult =
    req.place.exists(_.trim.nonEmpty) || req.event.exists(_.trim.nonEmpty)

  /** Pure: a running import job's progress line — `n/total` asked (a total of 0 or less is unknown, and a total passed
    * by the asked count is the asked count: places cited meanwhile join in).
    */
  def progressLine(r: MediaImportReport, total: com.histmersion.domain.media.imports.ImportsControllers.ProgressLineTotal, requests: com.histmersion.domain.media.imports.ImportsControllers.ProgressLineRequests): com.histmersion.domain.media.imports.ImportsControllers.ProgressLineResult =
    val of = if total > 0 then s"/${total.max(r.targets)}" else ""
    List(
      Some(s"${r.targets}$of place(s)/event(s) asked"),
      Some(s"batch ${r.batches}"),
      Some(s"${r.stored} stored · ${r.linked} linked"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed"),
      Some(s"$requests request(s)")
    ).flatten.mkString(" · ")

  /** Pure: a finished import job's line in `histmersion jobs`. */
  def summary(r: MediaImportReport): com.histmersion.domain.media.imports.ImportsControllers.SummaryResult =
    List(
      Some(s"${r.targets} place(s)/event(s) asked in ${r.batches} batch(es)"),
      Some(s"${r.files} file(s) · ${r.stored} stored · ${r.updated} refreshed · ${r.linked} link(s)"),
      Option.when(r.rejected.nonEmpty)("not stored: " + r.rejected.map(c => s"${c.key} ${c.n}").mkString(", ")),
      Option.when(r.revoked.nonEmpty)(s"${r.revoked.size} licence(s) no longer allowed (kept, review)"),
      Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
      Some(s"${r.pending} not asked yet")
    ).flatten.mkString(" · ")
