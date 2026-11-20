package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.Api
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.{AuthorName, Citekey, Doi, Title}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.SpatialHierarchyResult.Job
import com.histmersion.util.console.Console
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.Paging

/** `histmersion ingest <file>` · `reocr` · `assets` · `jobs` — OCR that keeps the figures, as a job (plan §1.8).
  *
  * The upload half is exposed as [[upload]] rather than as its own subcommand, because the name `ingest` is shared with
  * stream 2.6's acquisition-by-identifier: ONE command takes either a file (upload → convert here) or a DOI/arXiv id
  * (fetch upstream, then the same job), and [[AcquireCmd.ingest]] is the single place that dispatches between them.
  * Everything else lives here.
  */
object IngestCmd:

  /** Poll one job until it reaches a terminal state ([[JobFollow.job]]: one rewritten progress line), then print it.
    * The exit code is the point: a failed conversion must not look like success in a script — it exits with its
    * problem's code; a job still running when the wait ([[JobFollow.Limits]]) ran out exits "outcome unknown".
    */

  def pollJob(c: Client, id: Job.Id, waited: Int)(using console: Console): Either[Cli.Error, Unit] =
    followLimits.flatMap(limits =>
      JobFollow
        .job(c.job, id, c.unanswered, limits, waited = waited * 1000L)
        .map(j => Cli.out.println(IngestMarkdown.job(j)))
    )

  /** How long a job is followed: [[JobFollow.Limits.TimeoutVariable]] when it is set. */
  private[cli] def followLimits: Either[Cli.Error, JobFollow.Limits] =
    JobFollow.Limits.of(Option(System.getenv(JobFollow.Limits.TimeoutVariable))).left.map(Cli.Error(_))

  /** Upload one document and (unless `noWait`) follow its OCR job to completion. */
  def upload(using
      console: Console
  )(
      c: Client,
      file: Path,
      citekey: Citekey,
      quality: Ingest.Params.Quality,
      force: Ingest.Params.Force,
      title: Option[Title],
      authors: List[AuthorName],
      year: Option[Year],
      doi: Option[Doi],
      noWait: Cli.NoWait,
      provenance: Option[Ingest.Params.Provenance] = None,
      acquireItem: Option[Ingest.Params.AcquireItem] = None
  ): Either[Cli.Error, Unit] =
    if citekey.value.trim.isEmpty then
      Left(
        Cli.Error(
          Problem.Unclassified(Legacy.Text("uploading a file needs --citekey to store it under"))
        )
      )
    // Refused HERE, not by the server: a zero-length request body leaves the server's netty
    // connection mis-framed, so the NEXT upload on it reads as empty too (see
    // ZeroLengthUploadSuite). Never sending one keeps that hazard off our own wire entirely.
    else if scala.util.Try(Files.size(file)).getOrElse(0L) == 0L then
      Left(
        Cli.Error(
          Problem.Unclassified(Legacy.Text(s"$file is empty — there is nothing to convert"))
        )
      )
    else
      val prov = provenance.orElse(Some(Ingest.Params.Provenance("manual")))
      val params =
        Ingest.Params(
          Citekey(citekey.value.trim),
          Some(Ingest.Params.Filename(file.getFileName.toString)),
          quality,
          force,
          title,
          authors,
          year,
          doi,
          prov,
          acquireItem
        )
      c.ingest(params, file.toFile).flatMap { a =>
        Cli.out.println(
          s"queued job ${a.jobId} for ${a.citekey}" +
            acquireItem.fold("")(i => s" — acquisition item $i is ticked off when it succeeds")
        )
        if noWait.value then Right(()) else pollJob(c, a.jobId, 0)
      }

  /** The help of `jobs pause|resume|cancel`. */
  object ControlHelp:
    val pause: String =
      "Pause a long background job (a picture or event import, a base-map download, an acquisition " +
        "re-check, the init job's gazetteer loads) after the part it is on — or an agent run; it keeps what it did"
    val resume: String = "Resume a paused job: it goes on with the parts it had not done"
    val cancel: String = "Cancel a job: a background job stops after the part it is on and is recorded as cancelled"

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Reocr(ck: String, quality: Boolean, noWait: Boolean)
    case Assets(ck: String)
    case Jobs(
        id: Option[String],
        filter: ListFilters.JobFilter,
        limit: Option[Int],
        before: Option[String],
        json: Cli.JsonOutput
    )
    case Control(action: Api.JobControl.Action, id: String, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Reocr(ck, quality, noWait) =>
          c.reocr(Citekey(ck), Api.Overwrite(quality)).flatMap { a =>
            Cli.out.println(s"queued job ${a.jobId} for ${a.citekey}")
            if noWait then Right(()) else pollJob(c, a.jobId, 0)
          }
        case Command.Assets(ck) =>
          c.assets(Citekey(ck)).map(rows => Cli.out.println(IngestMarkdown.assets(Citekey(ck), rows.toVector)))
        case Command.Control(action, id, json) =>
          c.call(Api.jobControl)((Job.Id(id), action)).map(j => Output.emit(json, j)(IngestMarkdown.job))
        case Command.Jobs(id, f, limit, before, json) =>
          id match
            case Some(i) => c.job(Job.Id(i)).map(j => Output.emit(json, j)(IngestMarkdown.job))
            case None    =>
              c.jobs(f, limit.map(Paging.Limit(_)), before.map(Api.Jobs.Status(_))).map { (js, page) =>
                Output.emit(json, js)(js =>
                  IngestMarkdown.jobs(
                    js.toVector,
                    page,
                    f.qualifier.map(q => Ingest.JobStatus(q.value)),
                    PageCmd
                      .older(
                        page,
                        "jobs",
                        f.options.map((k, v) => (k.value, v.map(_.value))) ++ List("limit" -> limit.map(_.toString))
                      )
                      .map(t => Ingest.JobAge(t.value))
                  )
                )
              }
