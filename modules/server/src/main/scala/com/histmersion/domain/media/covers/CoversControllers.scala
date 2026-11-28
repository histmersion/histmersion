package com.histmersion
package domain.media.covers
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.Write
import com.histmersion.api.jobs.JobKinds
import com.histmersion.domain.media.MediaJobs
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.database.WritePermit
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
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The sources' pictures (#1446): their covers and illustrations as one run (a dry run from the database alone, or one
  * source at once) or as a job over every source; a cover the CLI read from the local Calibre library; the picture a
  * stored conversion brings.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class CoversControllers(pictures: SourcePictures, jobs: MediaJobs):

  /** The sources' pictures, one run: a dry run (from the database alone), or — for one `citekey` — done at once. A run
    * over every source is [[coversJob]].
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def covers(req: MediaCoversRequest)(using Creator.New): Either[Api.Error, MediaCoversReport] =
    // a dry run writes nothing, but runs the one path that may: under the gate too
    if req.dryRun.dryRun then jobs.guarded(WriteGate.OperationName("media_covers"))(Right(pictures.run(req)))
    else if req.citekey.forall(_.value.trim.isEmpty) then
      Left(
        Api.Error(
          Problem.Unclassified(Legacy.Text("a run over every source is a job: POST /api/media/covers/job")),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      jobs.guarded(WriteGate.OperationName("media_covers"))(
        jobs.exclusive(BackgroundJob.Work("media covers"))(Right(pictures.run(req)))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def coversJob(req: MediaCoversRequest)(using Creator.New): Either[Api.Error, JobInfo] =
    val what =
      List(
        Option.when(req.covers.included)("Open Library covers"),
        Option.when(req.illustrations.included)("illustrations")
      ).flatten
        .mkString(" and ") + req.citekey.fold(" of every source")(c => s" of $c") +
        (if req.refresh.refresh then " (refresh)" else "")
    jobs.start(CoversControllers.CoversJobKind, JobKinds.Subject(what)) { progress =>
      val r = pictures.run(req.modify(_.dryRun).setTo(RepairAcquire.RepairRequest.DryRun.Apply), progress)
      Right(
        BackgroundJob.Outcome.Summary(
          s"${r.sources} source(s) · ${r.covers} cover(s) · ${r.illustrations} illustration(s)" +
            (if r.failed.isEmpty then "" else s" · ${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}")
        )
      )
    }

  /** A cover the CLI read from the local Calibre library. */
  def putCover(p: SourceCoverParams, bytes: AssetService.Bytes)(using Creator.New): Either[Api.Error, MediaItem] =
    jobs.guarded(WriteGate.OperationName("media_cover"))(pictures.storeCover(p, bytes))

  /** After a conversion stored a source's figures (the OCR job's hook). */
  def afterIngest(citekey: Citekey)(using WritePermit)(using Creator.New): Option[SourcePictures.AfterIngestText] =
    pictures.afterIngest(citekey)

object CoversControllers:
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  val CoversJobKind: Job.Kind = Job.Kind.assume(CoversJobKinds.MediaCovers.kind.value)
