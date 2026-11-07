package com.histmersion.domain.catalogue.events

import com.histmersion.api.{BBox, Write}
import com.histmersion.api.catalogue.{
  CatalogProviderInfo,
  EventImportReport,
  EventImportRequest,
  EventRederiveReport,
  EventRederiveRequest
}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueJobs
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction

/** The event catalogues' operations as REST, MCP and the CLI reach them: the catalogues, an import (a job, dry run or
  * not), a re-derivation of the stored records' dating (a job), and the corrections by hand. `page` is the page size a
  * request's limit makes, from a default.
  */
final class EventImportsControllers(
    imports: EventImports,
    corrections: CorrectionService,
    events: EventService,
    jobs: CatalogueJobs,
    page: (Option[EventImportsControllers.PageArg1], EventImportsControllers.PageArg2) => EventImportsControllers.PageResult
):
  private def invalid(m: com.histmersion.domain.catalogue.events.EventImportsControllers.Problem) = Api.Error(m, Some(Write.CodeInvalid))

  def catalogues: List[CatalogProviderInfo] = imports.catalogues

  def importJob(caller: Caller, req: EventImportRequest): Either[Api.Error, JobInfo] =
    for
      provider <- imports.resolve(req.provider).left.map(invalid)
      area <- req.bbox.map(_.trim).filter(_.nonEmpty) match
        case Some(b) => BBox.parse(b).toRight(invalid(s"--bbox '$b' is not minLon,minLat,maxLon,maxLat")).map(Some(_))
        case None    => Right(None)
      job <- jobs.start(
        EventImportsControllers.ImportJobKind,
        s"${imports.infoOf(provider).fold(provider)(_.title)} events${if req.dryRun then " (dry run)" else ""}",
        write = !req.dryRun
      ) { (job, progress) =>
        val r = imports.runImport(provider, req, area, progress)
        jobs.amend(job.id.value)(_.set(EventImportReport.Detail, Some(r)))
        Right(EventImportReport.summary(r))
      }
    yield job

  def rederiveJob(caller: Caller, req: EventRederiveRequest): Either[Api.Error, JobInfo] =
    for
      provider <- req.provider.map(_.trim).filter(_.nonEmpty) match
        // no network: a built-in catalogue switched off since still has its records to re-derive
        case Some(p) => imports.resolveStored(p).left.map(invalid).map(Some(_))
        case None    => Right(None)
      job <- jobs.start(
        EventImportsControllers.RederiveJobKind,
        s"re-derive the dating of ${provider.fold("every catalogue's")(p => imports.infoOf(p).fold(p)(_.title) + "'s")} " +
          s"events${if req.dryRun then " (dry run)" else ""}",
        write = !req.dryRun
      ) { (job, progress) =>
        val r = imports.runRederive(provider, req.dryRun, progress)
        jobs.amend(job.id.value)(_.set(EventRederiveReport.Detail, Some(r)))
        Right(EventRederiveReport.summary(r))
      }
    yield job

  /** `GET /api/events/corrections`: the corrections of catalogue events, conflicts first. */
  def corrections(
      status: Option[com.histmersion.domain.catalogue.events.EventImportsControllers.CorrectionsStatus],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: Option[com.histmersion.PageSize],
      offset: Option[com.histmersion.Offset]
  ): Either[Api.Error, Correction.List] =
    status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) if !Correction.Status.all.contains(s) =>
        Left(invalid(s"status '$s' is not ${Correction.Status.all.mkString(" | ")}"))
      case st =>
        Right(
          corrections.list(
            Correction.Target.Event,
            event.map(id => events.renamedTo(id).getOrElse(id)),
            st,
            page(limit, 50),
            offset.getOrElse(0)
          )
        )

object EventImportsControllers:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageArg1 = PageArg1.T
  object PageArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageArg2 = PageArg2.T
  object PageArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CorrectionsStatus = CorrectionsStatus.T
  object CorrectionsStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportJobKind = ImportJobKindValue
  type ImportJobKindValue = ImportJobKindValue.T
  object ImportJobKindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RederiveJobKind = RederiveJobKindValue
  type RederiveJobKindValue = RederiveJobKindValue.T
  object RederiveJobKindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ImportJobKind: com.histmersion.domain.catalogue.events.EventImportsControllers.ImportJobKind = com.histmersion.api.catalogue.EventsJobKinds.EventsImport.kind.value
  val RederiveJobKind: com.histmersion.domain.catalogue.events.EventImportsControllers.RederiveJobKind = com.histmersion.api.catalogue.EventsJobKinds.EventsRederive.kind.value
