package com.histmersion
package domain.catalogue.events

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.{BBox, Write}
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  CatalogRecord,
  EventImportReport,
  EventImportRequest,
  EventRederiveReport,
  EventRederiveRequest
}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.{CatalogueJobs, Event}
import com.histmersion.util.jobs.Job
import com.histmersion.{Offset, PageSize}
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction
import com.histmersion.api.jobs.JobKinds
import com.histmersion.util.jobs.BackgroundJob
import com.histmersion.api.catalogue.EventsJobKinds

/** The event catalogues' operations as REST, MCP and the CLI reach them: the catalogues, an import (a job, dry run or
  * not), a re-derivation of the stored records' dating (a job), and the corrections by hand. `page` is the page size a
  * request's limit makes, from a default.
  */
final class EventImportsControllers(
    imports: EventImports,
    corrections: CorrectionService,
    events: EventService,
    jobs: CatalogueJobs,
    page: (Option[PageSize], PageSize) => PageSize
):
  private def invalid(m: Problem) = Api.Error(m, Some(Api.Error.Code(Write.CodeInvalid)))

  def catalogues: List[CatalogProviderInfo] = imports.catalogues

  def importJob(caller: Caller, req: EventImportRequest): Either[Api.Error, JobInfo] =
    for
      provider <- imports
        .resolve(CatalogRecord.Provider(req.provider.value))
        .left
        .map(p => invalid(p))
      area <- req.bbox.map(_.value.trim).filter(_.nonEmpty) match
        case Some(b) =>
          BBox
            .parse(BBox.Text(b))
            .toRight(
              invalid(
                Problem.Invalid.Unreadable(
                  Problem.TypeName.of(BBox.Text),
                  Problem.Raw(b),
                  Vector(Problem.Raw("minLon,minLat,maxLon,maxLat")),
                  Vector(Problem.Field("--bbox"))
                )
              )
            )
            .map(Some(_))
        case None => Right(None)
      job <- jobs.start(
        EventImportsControllers.ImportJobKind,
        JobKinds.Subject(
          s"${imports.infoOf(provider).fold(provider.value)(_.title.value)} events${
              if req.dryRun.value then " (dry run)" else ""
            }"
        ),
        write = !req.dryRun.value
      ) { (job, progress) =>
        val r = imports.runImport(provider, req, area, progress)
        jobs.amend(Job.Id.assume(job.id.value))(_.set(EventImportReport.Detail, Some(r)))
        Right(BackgroundJob.Outcome.Summary(EventImportReport.summary(r)))
      }
    yield job

  def rederiveJob(caller: Caller, req: EventRederiveRequest): Either[Api.Error, JobInfo] =
    for
      provider <- req.provider.map(_.value.trim).filter(_.nonEmpty) match
        // no network: a built-in catalogue switched off since still has its records to re-derive
        case Some(p) =>
          imports
            .resolveStored(CatalogRecord.Provider(p))
            .left
            .map(why => invalid(why))
            .map(Some(_))
        case None => Right(None)
      job <- jobs.start(
        EventImportsControllers.RederiveJobKind,
        JobKinds.Subject(
          s"re-derive the dating of ${provider
              .fold("every catalogue's")(p => imports.infoOf(p).fold(p.value)(_.title.value) + "'s")} " +
            s"events${if req.dryRun.value then " (dry run)" else ""}"
        ),
        write = !req.dryRun.value
      ) { (job, progress) =>
        val r = imports.runRederive(provider, req.dryRun.value, progress)
        jobs.amend(Job.Id.assume(job.id.value))(_.set(EventRederiveReport.Detail, Some(r)))
        Right(BackgroundJob.Outcome.Summary(EventRederiveReport.summary(r)))
      }
    yield job

  /** `GET /api/events/corrections`: the corrections of catalogue events, conflicts first. */
  def corrections(
      status: Option[Correction.StatusValue],
      event: Option[Event.Id],
      limit: Option[PageSize],
      offset: Option[Offset]
  ): Either[Api.Error, Correction.List] =
    status.map(_.value.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) if !Correction.Status.all.exists(_.value == s) =>
        Left(
          invalid(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(Correction.StatusValue),
              Problem.Raw(s),
              Correction.Status.all.toVector.map(Problem.Raw.of(_))
            )
          )
        )
      case st =>
        Right(
          corrections.list(
            Correction.TargetKind(Correction.Target.Event),
            event.map(id => events.renamedTo(id).getOrElse(id)),
            st.map(Correction.StatusValue(_)),
            page(limit, PageSize(50)),
            offset.getOrElse(Offset.Start)
          )
        )

object EventImportsControllers:
  val ImportJobKind: Job.Kind = Job.Kind.applyUnsafe(EventsJobKinds.EventsImport.kind.value)
  val RederiveJobKind: Job.Kind =
    Job.Kind.applyUnsafe(EventsJobKinds.EventsRederive.kind.value)
