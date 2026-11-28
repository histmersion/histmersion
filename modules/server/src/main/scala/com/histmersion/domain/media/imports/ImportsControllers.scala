package com.histmersion
package domain.media.imports

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Write
import com.histmersion.config.{MediaConfig, MediaPluginCollectionsConfig}
import com.histmersion.domain.catalogue.{Event, Place}
import com.histmersion.domain.media.MediaJobs
import com.histmersion.plugin.{MediaCollection, Plugin}
import com.histmersion.pluginhost.{HttpTransport, Polite, Registries}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, Job}
import com.histmersion.util.ocr.ConverterClient

import hearth.kindlings.optics.syntax.*

import scala.collection.immutable.ListMap
import com.histmersion.api.jobs.{JobInfo, JobKinds}
import com.histmersion.api.media.{MediaImportReport, MediaImportRequest}
import com.histmersion.api.Api
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.media.MediaItem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.lint.{tolerated, LintRule}

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
    collections: () => (
        ListMap[Registries.MediaCollectionsKey, MediaCollection],
        Map[Registries.MediaCollectionsKey, Plugin.Info.Id]
    ),
    cfg: () => MediaConfig,
    http: HttpTransport,
    converter: ConverterClient,
    clock: java.time.Clock
):

  /** The importer an import request names, or why it cannot run (unknown, its plugin switched off, or its own
    * `media.<provider>.enabled` off).
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def providerOf(p: MediaItem.Provider): Either[Api.Error, MediaImporter] =
    MediaImporters
      .resolve(MediaImporters.ImporterKey(p.value), collections()._1, imports, store, cfg, http, converter)
      .left
      .map(why => ImportsControllers.error(why, Write.CodeInvalid))

  /** The place or event a request names, as the domain's ids: a blank one names none, a malformed one is no place or
    * event that exists. A place wins over an event.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def targetsOf(req: MediaImportRequest): Either[Api.Error, (Option[Place.Id], Option[Event.Id])] =
    for
      place <- req.place.map(_.value.trim).filter(_.nonEmpty) match
        case Some(p) =>
          Place.Id
            .either(p)
            .left
            .map(_ =>
              ImportsControllers
                .error(Problem.NotFound.Entity(Problem.TypeName.of(Place.Id), Problem.Raw(s"$p")), Write.CodeNotFound)
            )
            .map(Some(_))
        case None => Right(None)
      event <- req.event.map(_.value.trim).filter(_.nonEmpty) match
        case Some(e) if place.isEmpty =>
          Event.Id
            .either(e)
            .left
            .map(_ =>
              ImportsControllers
                .error(Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$e")), Write.CodeNotFound)
            )
            .map(Some(_))
        case _ => Right(None)
    yield (place, event)

  /** `GET /api/media/providers`: what `media import` can run, as the registries hold it. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def providers: List[MediaProvider.Info] =
    val (registered, owner) = collections()
    MediaImporters.infos(
      registered,
      n => owner.getOrElse(n, Plugin.Info.Id("")),
      n => cfg().collections.licencesFor(MediaPluginCollectionsConfig.PerCollectionKey(n.value))
    )

  private def importer(imp: MediaImporter): Creator.New =
    val (registered, owners) = collections()
    InTreeWorkflow.imported(
      registered.collectFirst { case (key, c) if c eq imp.collection => owners.get(key) }.flatten,
      InTreeWorkflow.Media,
      InTreeWorkflow.MediaImporting
    )

  /** One import run, answered when it ends: a dry run (at most `media.max-limit`), or a real one of ONE place or event.
    * A real run over many is a job ([[importJob]], #1491): asked here it outlived the CLI's request while the server
    * kept going.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def importMedia(req: MediaImportRequest)(using Creator.New): Either[Api.Error, MediaImportReport] =
    def go(using WritePermit)(using Creator.New): Either[Api.Error, MediaImportReport] =
      providerOf(req.provider).flatMap { imp =>
        given Creator.New = importer(imp)
        targetsOf(req).flatMap { (place, event) =>
          jobs.exclusive(BackgroundJob.Work("media import")) {
            imp
              .targets(
                place,
                event,
                ImportService.TargetsLimit(cfg().limit(req.limit.map(l => MediaConfig.Limit(l.value))).value),
                (if req.refresh.refresh then ImportService.Refresh.Refresh else ImportService.Refresh.Reuse),
                None
              )
              .left
              .map(why => ImportsControllers.error(why, Write.CodeNotFound))
              .map { targets =>
                val run = imp.start()
                val report =
                  run.run(
                    targets,
                    (if req.dryRun.dryRun then MediaImporter.DryRun.DryRun else MediaImporter.DryRun.Applied),
                    (if req.refresh.refresh then ImportService.Refresh.Refresh else ImportService.Refresh.Reuse)
                  )
                scribe.info(
                  s"media import ${imp.check} (${if req.dryRun.dryRun then "dry run" else "stored"}): ${targets.size} target(s), " +
                    s"${report.files} file(s), ${report.stored} stored, ${run.requests} request(s)"
                )
                report.modify(_.pending).setTo(MediaImportReport.Pending(imp.pending(None).value))
              }
          }
        }
      }
    // a dry run stores nothing but runs the importer, which may: under the gate too
    if req.dryRun.dryRun then jobs.guarded(WriteGate.OperationName("media_import"))(go)
    else if !ImportsControllers.single(req) then
      Left(
        ImportsControllers.error(
          Problem.Unclassified(
            Legacy.Text(
              "an import over many places/events is a job: POST /api/media/import/job " +
                "(`histmersion media import wikidata` starts and follows it)"
            )
          ),
          Write.CodeInvalid
        )
      )
    else jobs.guarded(WriteGate.OperationName("media_import"))(go)

  /** The import as a job: `media.import-batch` items at a time, most-cited sites first, each batch recorded before the
    * next (so a stopped job resumes after the last one), until none is left, `limit` targets are done, or a batch gets
    * no answer at all.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def importJob(req: MediaImportRequest)(using Creator.New): Either[Api.Error, JobInfo] =
    providerOf(req.provider).flatMap { imp =>
      if req.dryRun.dryRun then
        Left(
          ImportsControllers.error(
            Problem.Unclassified(Legacy.Text("a dry run answers at once: POST /api/media/import")),
            Write.CodeInvalid
          )
        )
      else
        val single = ImportsControllers.single(req)
        val refresh = (if req.refresh.refresh then ImportService.Refresh.Refresh else ImportService.Refresh.Reuse)
        targetsOf(req).flatMap { (place, event) =>
          // the one target must exist (and have an item) before a job is started for it
          val check =
            if single then
              imp
                .targets(place, event, ImportService.TargetsLimit(1), refresh, None)
                .left
                .map(why => ImportsControllers.error(why, Write.CodeNotFound))
                .map(_ => ())
            else Right(())
          check.flatMap { _ =>
            val cap = req.limit.map(_.value).filter(_ > 0).map(ImportsControllers.ImportLimit(_))
            val what =
              (if single then s"${imp.title} of ${req.place.map(_.value).orElse(req.event.map(_.value)).getOrElse("")}"
               else s"${imp.title} of ${cap.fold(s"every ${imp.over}")(n => s"up to $n ${imp.over}")} not asked yet") +
                (if req.refresh.refresh then " (refresh)" else "")
            // a refresh asks again what was asked before this job started — a resumed job too
            val since = Option.when(req.refresh.refresh)(ImportService.Since(clock.millis()))
            jobs.start(ImportsControllers.ImportJobKind, JobKinds.Subject(what)) { progress =>
              given Creator.New = importer(imp)
              Right(ImportsControllers.summary(runImport(imp, place, event, refresh, single, cap, since, progress)))
            }
          }
        }
    }

  /** The job's body. Progress is `n/total` (#1491): the total is counted once, at the start — the places/events not
    * asked yet (since this job's start, for a refresh), capped by `limit`. A failed picture is a line in the report,
    * never the end of the job; a batch that throws is reported and ends it (its targets are not recorded as asked, so
    * the next job resumes there, not past it).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def runImport(
      imp: MediaImporter,
      place: Option[Place.Id],
      event: Option[Event.Id],
      refresh: ImportService.Refresh,
      single: Boolean,
      limit: Option[ImportsControllers.ImportLimit],
      since: Option[ImportService.Since],
      progress: JobInfo.Progress => Unit
  )(using WritePermit)(using Creator.New): MediaImportReport =
    val run = imp.start()
    val batch = imp.batch.value.max(1)
    val cap = limit.fold(Int.MaxValue)(_.value)
    progress(JobInfo.Progress(s"counting the ${imp.over} not asked yet"))
    val total = ImportsControllers.TotalCount(if single then 1 else imp.pending(since).value.min(cap))
    @annotation.tailrec
    def loop(acc: MediaImportReport, seen: Set[ImportsControllers.SeenKey]): MediaImportReport =
      val left = cap - acc.targets.value
      if left <= 0 || jobs.held then acc
      else
        imp.targets(place, event, ImportService.TargetsLimit(batch.min(left)), refresh, since) match
          case Left(e)    => acc.modify(_.failed).using(_ :+ MediaImportReport.Failed(e.sentence))
          case Right(Nil) => acc
          // none of the last batch's checks were recorded: asking again would spin on the same ones
          case Right(targets) if !single && targets.forall(t => seen(ImportsControllers.seenKey(t))) =>
            acc
              .modify(_.failed)
              .using(
                _ :+ MediaImportReport.Failed(
                  s"${targets.size} place(s)/event(s) came back unrecorded (${targets.head.id}…); stopped"
                )
              )
          case Right(targets) =>
            scala.util.Try(run.run(targets, dryRun = MediaImporter.DryRun.Applied, refresh)) match
              case scala.util.Failure(e: InterruptedException) => throw e
              case scala.util.Failure(e)                       =>
                scribe.error(s"media import job: batch ${acc.batches.value + 1} failed: ${e.getMessage}", e)
                acc
                  .modify(_.failed)
                  .using(
                    _ :+ MediaImportReport.Failed(
                      s"batch ${acc.batches.value + 1} (${targets.head.id}…): " +
                        s"${e.getClass.getSimpleName}: ${e.getMessage}"
                    )
                  )
              case scala.util.Success(r) =>
                val next = MediaImportReport.combine(acc, r.modify(_.batches).setTo(MediaImportReport.Batches(1)))
                progress(ImportsControllers.progressLine(next, total, run.requests))
                // a batch the provider did not answer at all ends the job (the next one resumes) rather than spin
                if single || (r.items.value == 0 && r.failed.nonEmpty) then next
                else loop(next, seen ++ targets.map(ImportsControllers.seenKey))
    val out = loop(
      MediaImportReport(MediaItem.Provider(imp.check.value), dryRun = MediaImportRequest.DryRun.Apply),
      Set.empty
    )
    scribe.info(
      s"media import job ${imp.check}: ${out.targets} target(s) in ${out.batches} batch(es), ${out.stored} stored, " +
        s"${run.requests} request(s), ${out.failed.size} failed"
    )
    out.failed.take(50).foreach(f => scribe.warn(s"media import job: $f"))
    out.modify(_.pending).setTo(MediaImportReport.Pending(imp.pending(since).value))

object ImportsControllers:
  type ImportLimit = ImportLimit.T
  object ImportLimit extends Newtype[Int, Pure]
  type SeenKey = SeenKey.T
  object SeenKey extends Newtype[String, Pure]
  type TotalCount = TotalCount.T
  object TotalCount extends Newtype[Int, Pure]

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  val ImportJobKind: Job.Kind = Job.Kind.applyUnsafe(ImportJobKinds.MediaImport.kind.value)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private[imports] def error(message: Problem, code: String): Api.Error =
    Api.Error(message, Some(Api.Error.Code(code)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private[imports] def seenKey(t: Target): SeenKey = SeenKey(s"${t.targetType}:${t.id}")

  /** Pure: does the request name one place or event (not "every one not asked yet")? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def single(req: MediaImportRequest): Boolean =
    req.place.exists(_.value.trim.nonEmpty) || req.event.exists(_.value.trim.nonEmpty)

  /** Pure: a running import job's progress line — `n/total` asked (a total of 0 or less is unknown, and a total passed
    * by the asked count is the asked count: places cited meanwhile join in).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def progressLine(r: MediaImportReport, total: TotalCount, requests: Polite.RequestCount): JobInfo.Progress =
    val of = if total.value > 0 then s"/${total.value.max(r.targets.value)}" else ""
    JobInfo.Progress(
      List(
        Some(s"${r.targets}$of place(s)/event(s) asked"),
        Some(s"batch ${r.batches}"),
        Some(s"${r.stored} stored · ${r.linked} linked"),
        Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed"),
        Some(s"$requests request(s)")
      ).flatten.mkString(" · ")
    )

  /** Pure: a finished import job's line in `histmersion jobs`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def summary(r: MediaImportReport): BackgroundJob.Outcome.Summary =
    BackgroundJob.Outcome.Summary(
      List(
        Some(s"${r.targets} place(s)/event(s) asked in ${r.batches} batch(es)"),
        Some(s"${r.files} file(s) · ${r.stored} stored · ${r.updated} refreshed · ${r.linked} link(s)"),
        Option.when(r.rejected.nonEmpty)("not stored: " + r.rejected.map(c => s"${c.key} ${c.n}").mkString(", ")),
        Option.when(r.revoked.nonEmpty)(s"${r.revoked.size} licence(s) no longer allowed (kept, review)"),
        Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"),
        Some(s"${r.pending} not asked yet")
      ).flatten.mkString(" · ")
    )
