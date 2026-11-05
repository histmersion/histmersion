package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.library.Ingest
import com.histmersion.util.console.Console

/** `histmersion ingest <file>` · `reocr` · `assets` · `jobs` — OCR that keeps the figures, as a job (plan §1.8).
  *
  * The upload half is exposed as [[upload]] rather than as its own subcommand, because the name `ingest` is shared with
  * stream 2.6's acquisition-by-identifier: ONE command takes either a file (upload → convert here) or a DOI/arXiv id
  * (fetch upstream, then the same job), and [[AcquireCmd.ingest]] is the single place that dispatches between them.
  * Everything else lives here.
  */
object IngestCmd:

  /** Poll one job until it reaches a terminal state ([[JobFollow.job]]: one rewritten progress line), then print it.
    * The exit code is the point: a failed conversion must not look like success in a script — it exits
    * [[ExitCode.Refused]].
    */
  type PollJobWaited = PollJobWaited.T
  object PollJobWaited extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadCitekey = UploadCitekey.T
  object UploadCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadQuality = UploadQuality.T
  object UploadQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadForce = UploadForce.T
  object UploadForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadTitle = UploadTitle.T
  object UploadTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadAuthors = UploadAuthors.T
  object UploadAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadYear = UploadYear.T
  object UploadYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadDoi = UploadDoi.T
  object UploadDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadNoWait = UploadNoWait.T
  object UploadNoWait extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadProvenance = UploadProvenance.T
  object UploadProvenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UploadAcquireItem = UploadAcquireItem.T
  object UploadAcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlAction = ControlAction.T
  object ControlAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlHelp = ControlHelp.T
  object ControlHelp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def pollJob(c: Client, id: com.histmersion.api.jobs.JobInfo.Id, waited: com.histmersion.cli.IngestCmd.PollJobWaited)(using console: Console) : Either[Cli.Error, Unit] =
    JobFollow.job(c.job, id, waited * 1000L).map(j => Cli.out.println(IngestMarkdown.job(j)))

  /** Upload one document and (unless `noWait`) follow its OCR job to completion. */
  def upload(using console: Console)(
      c: Client,
      file: Path,
      citekey: com.histmersion.cli.IngestCmd.UploadCitekey,
      quality: com.histmersion.cli.IngestCmd.UploadQuality,
      force: com.histmersion.cli.IngestCmd.UploadForce,
      title: Option[com.histmersion.cli.IngestCmd.UploadTitle],
      authors: List[com.histmersion.cli.IngestCmd.UploadAuthors],
      year: Option[com.histmersion.cli.IngestCmd.UploadYear],
      doi: Option[com.histmersion.cli.IngestCmd.UploadDoi],
      noWait: com.histmersion.cli.IngestCmd.UploadNoWait,
      provenance: Option[com.histmersion.cli.IngestCmd.UploadProvenance] = None,
      acquireItem: Option[com.histmersion.cli.IngestCmd.UploadAcquireItem] = None
  ) : Either[Cli.Error, Unit] =
    if citekey.trim.isEmpty then
      Left(Cli.Error("uploading a file needs --citekey to store it under", exitCode = ExitCode.Usage))
    // Refused HERE, not by the server: a zero-length request body leaves the server's netty
    // connection mis-framed, so the NEXT upload on it reads as empty too (see
    // ZeroLengthUploadSuite). Never sending one keeps that hazard off our own wire entirely.
    else if scala.util.Try(Files.size(file)).getOrElse(0L) == 0L then
      Left(Cli.Error(s"$file is empty — there is nothing to convert", exitCode = ExitCode.Usage))
    else
      val prov = provenance.orElse(Some("manual"))
      val params =
        Ingest.Params(
          citekey.trim,
          Some(file.getFileName.toString),
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
        if noWait then Right(()) else pollJob(c, com.histmersion.api.jobs.JobInfo.Id(a.jobId), 0)
      }

  private def reocr (using console: Console) =
    Opts.subcommand("reocr", "Convert a source's stored original again (e.g. under the slow, accurate OCR path)") {
      (
        Opts.argument[String]("citekey"),
        Opts.flag("quality", "Slow, accurate OCR (docling)").orFalse,
        Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse
      )
        .mapN { (ck, quality, noWait) => (c: Client) =>
          c.reocr(ck, quality).flatMap { a =>
            Cli.out.println(s"queued job ${a.jobId} for ${a.citekey}")
            if noWait then Right(()) else pollJob(c, com.histmersion.api.jobs.JobInfo.Id(a.jobId), 0)
          }
        }
    }

  private def assets (using console: Console) =
    Opts.subcommand("assets", "The figures stored with a source, and the URLs they are served at") {
      Opts
        .argument[String]("citekey")
        .map(ck => (c: Client) => c.assets(ck).map(rows => Cli.out.println(IngestMarkdown.assets(ck, rows.toVector))))
    }

  /** `histmersion jobs pause|resume|cancel <id>`. */
  private def control(action: com.histmersion.cli.IngestCmd.ControlAction, help: com.histmersion.cli.IngestCmd.ControlHelp)(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(action, help) {
      (Opts.argument[String]("id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
        c.call(Api.jobControl)((id, action)).map(j => Output.emit(json, j)(IngestMarkdown.job))
      }
    }

  private def controls (using console: Console) =
    control(
      "pause",
      "Pause a long background job (a picture or event import, a base-map download, an acquisition " +
        "re-check, the init job's gazetteer loads) after the part it is on — or an agent run; it keeps what it did"
    ) orElse
      control("resume", "Resume a paused job: it goes on with the parts it had not done") orElse
      control("cancel", "Cancel a job: a background job stops after the part it is on and is recorded as cancelled")

  private def jobs (using console: Console) = Opts.subcommand(
    "jobs",
    "Recent background jobs (OCR, imports, agent runs, …), filtered on the server, " +
      "or one job by id; `jobs pause|resume|cancel <id>` controls one"
  ) {
    controls orElse (
      Opts.argument[String]("id").orNone,
      ListOpts.jobs,
      Opts.option[Int]("limit", "Max rows").orNone,
      Opts
        .option[String]("before", "The next older page: the id of the last job shown (the listing prints the command)")
        .orNone,
      Output.jsonFlag
    )
      .mapN { (id, f, limit, before, json) => (c: Client) =>
        id match
          case Some(i) => c.job(com.histmersion.api.jobs.JobInfo.Id(i)).map(j => Output.emit(json, j)(IngestMarkdown.job))
          case None    =>
            c.jobs(f, limit, before).map { (js, page) =>
              Output.emit(json, js)(js =>
                IngestMarkdown.jobs(
                  js.toVector,
                  page,
                  f.qualifier,
                  PageCmd.older(page, "jobs", f.options ++ List("limit" -> limit.map(_.toString)))
                )
              )
            }
      }
  }

  def all(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = reocr orElse assets orElse jobs
