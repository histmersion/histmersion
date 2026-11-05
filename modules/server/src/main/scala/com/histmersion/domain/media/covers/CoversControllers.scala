package com.histmersion.domain.media.covers

import com.histmersion.api.Write
import com.histmersion.domain.media.MediaJobs

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{MediaCoversReport, MediaCoversRequest, MediaItem, SourceCoverParams}
import com.histmersion.api.Api

/** The sources' pictures (#1446): their covers and illustrations as one run (a dry run from the database alone, or one
  * source at once) or as a job over every source; a cover the CLI read from the local Calibre library; the picture a
  * stored conversion brings.
  */
final class CoversControllers(pictures: SourcePictures, jobs: MediaJobs):

  /** The sources' pictures, one run: a dry run (from the database alone), or — for one `citekey` — done at once. A run
    * over every source is [[coversJob]].
    */
  def covers(req: MediaCoversRequest): Either[Api.Error, MediaCoversReport] =
    if req.dryRun then Right(pictures.run(req))
    else if req.citekey.forall(_.trim.isEmpty) then
      Left(Api.Error("a run over every source is a job: POST /api/media/covers/job", Some(Write.CodeInvalid)))
    else jobs.guarded("media_covers")(jobs.exclusive("media covers")(Right(pictures.run(req))))

  def coversJob(req: MediaCoversRequest): Either[Api.Error, JobInfo] =
    val what =
      List(Option.when(req.covers)("Open Library covers"), Option.when(req.illustrations)("illustrations")).flatten
        .mkString(" and ") + req.citekey.fold(" of every source")(c => s" of $c") +
        (if req.refresh then " (refresh)" else "")
    jobs.start(CoversControllers.CoversJobKind, what) { progress =>
      val r = pictures.run(req.modify(_.dryRun).setTo(false), progress)
      Right(
        s"${r.sources} source(s) · ${r.covers} cover(s) · ${r.illustrations} illustration(s)" +
          (if r.failed.isEmpty then "" else s" · ${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}")
      )
    }

  /** A cover the CLI read from the local Calibre library. */
  def putCover(p: SourceCoverParams, bytes: Array[com.histmersion.domain.media.covers.CoversControllers.PutCoverBytes]): Either[Api.Error, MediaItem] =
    jobs.guarded("media_cover")(pictures.storeCover(p, bytes))

  /** After a conversion stored a source's figures (the OCR job's hook). */
  def afterIngest(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.media.covers.CoversControllers.AfterIngestResult] = pictures.afterIngest(citekey)

object CoversControllers:
  type PutCoverBytes = PutCoverBytes.T
  object PutCoverBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterIngestResult = AfterIngestResult.T
  object AfterIngestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoversJobKind = CoversJobKind.T
  object CoversJobKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val CoversJobKind: com.histmersion.domain.media.covers.CoversControllers.CoversJobKind = com.histmersion.api.media.CoversJobKinds.MediaCovers.kind.value
