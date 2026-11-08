package com.histmersion
package domain.media.covers

import com.histmersion.api.Write
import com.histmersion.api.jobs.JobKinds
import com.histmersion.domain.media.MediaJobs
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.BackgroundJob

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{MediaCoversReport, MediaCoversRequest, MediaItem, SourceCoverParams}
import com.histmersion.api.Api
import com.histmersion.api.library.RepairAcquire
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import com.histmersion.api.media.CoversJobKinds

/** The sources' pictures (#1446): their covers and illustrations as one run (a dry run from the database alone, or one
  * source at once) or as a job over every source; a cover the CLI read from the local Calibre library; the picture a
  * stored conversion brings.
  */
final class CoversControllers(pictures: SourcePictures, jobs: MediaJobs):

  /** The sources' pictures, one run: a dry run (from the database alone), or — for one `citekey` — done at once. A run
    * over every source is [[coversJob]].
    */
  def covers(req: MediaCoversRequest): Either[Api.Error, MediaCoversReport] =
    if req.dryRun.value then Right(pictures.run(req))
    else if req.citekey.forall(_.value.trim.isEmpty) then
      Left(Api.Error(Api.Error.Message("a run over every source is a job: POST /api/media/covers/job"), Some(Api.Error.Code(Write.CodeInvalid))))
    else jobs.guarded(WriteGate.OperationName("media_covers"))(jobs.exclusive(BackgroundJob.Work("media covers"))(Right(pictures.run(req))))

  def coversJob(req: MediaCoversRequest): Either[Api.Error, JobInfo] =
    val what =
      List(Option.when(req.covers.value)("Open Library covers"), Option.when(req.illustrations.value)("illustrations")).flatten
        .mkString(" and ") + req.citekey.fold(" of every source")(c => s" of $c") +
        (if req.refresh.value then " (refresh)" else "")
    jobs.start(CoversControllers.CoversJobKind, JobKinds.Subject(what)) { progress =>
      val r = pictures.run(req.modify(_.dryRun).setTo(RepairAcquire.RepairRequest.DryRun(false)), progress)
      Right(
        BackgroundJob.Outcome.Summary(
          s"${r.sources} source(s) · ${r.covers} cover(s) · ${r.illustrations} illustration(s)" +
            (if r.failed.isEmpty then "" else s" · ${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}")
        )
      )
    }

  /** A cover the CLI read from the local Calibre library. */
  def putCover(p: SourceCoverParams, bytes: AssetService.Bytes): Either[Api.Error, MediaItem] =
    jobs.guarded(WriteGate.OperationName("media_cover"))(pictures.storeCover(p, bytes))

  /** After a conversion stored a source's figures (the OCR job's hook). */
  def afterIngest(citekey: Citekey): Option[SourcePictures.AfterIngestText] = pictures.afterIngest(citekey)

object CoversControllers:
  val CoversJobKind: Job.Kind = Job.Kind.assume(CoversJobKinds.MediaCovers.kind.value)
