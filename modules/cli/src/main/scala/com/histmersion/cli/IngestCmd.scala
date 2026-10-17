package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.{Files, Path}

/** `histmersion ingest <file>` · `reocr` · `assets` · `jobs` — OCR that keeps the figures, as a job
  * (plan §1.8).
  *
  * The upload half is exposed as [[upload]] rather than as its own subcommand, because the name
  * `ingest` is shared with stream 2.6's acquisition-by-identifier: ONE command takes either a file
  * (upload → convert here) or a DOI/arXiv id (fetch upstream, then the same job), and
  * [[AcquireCmd.ingest]] is the single place that dispatches between them. Everything else lives
  * here.
  */
object IngestCmd:

  /** Poll one job until it reaches a terminal state ([[JobFollow.job]]: one rewritten progress line),
    * then print it. The exit code is the point: a failed conversion must not look like success in a
    * script — it exits [[ExitCode.Refused]]. */
  def pollJob(c: Client, id: String, waited: Int): Either[CliError, Unit] =
    JobFollow.job(c.job, id, waited * 1000L).map(j => println(IngestMarkdown.job(j)))

  /** Upload one document and (unless `noWait`) follow its OCR job to completion. */
  def upload(
      c: Client,
      file: Path,
      citekey: String,
      quality: Boolean,
      force: Boolean,
      title: Option[String],
      authors: List[String],
      year: Option[Int],
      doi: Option[String],
      noWait: Boolean,
      provenance: Option[String] = None,
      acquireItem: Option[Int] = None
  ): Either[CliError, Unit] =
    if citekey.trim.isEmpty then
      Left(CliError("uploading a file needs --citekey to store it under", exitCode = ExitCode.Usage))
    // Refused HERE, not by the server: a zero-length request body leaves the server's netty
    // connection mis-framed, so the NEXT upload on it reads as empty too (see
    // ZeroLengthUploadSuite). Never sending one keeps that hazard off our own wire entirely.
    else if scala.util.Try(Files.size(file)).getOrElse(0L) == 0L then
      Left(CliError(s"$file is empty — there is nothing to convert", exitCode = ExitCode.Usage))
    else
      val prov = provenance.orElse(Some("manual"))
      val params =
        IngestParams(citekey.trim, Some(file.getFileName.toString), quality, force, title, authors, year, doi, prov,
          acquireItem)
      c.ingest(params, file.toFile).flatMap { a =>
        println(s"queued job ${a.jobId} for ${a.citekey}" +
          acquireItem.fold("")(i => s" — acquisition item $i is ticked off when it succeeds"))
        if noWait then Right(()) else pollJob(c, a.jobId, 0)
      }

  private val reocr =
    Opts.subcommand("reocr", "Convert a source's stored original again (e.g. under the slow, accurate OCR path)") {
      (Opts.argument[String]("citekey"),
        Opts.flag("quality", "Slow, accurate OCR (docling)").orFalse,
        Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse)
        .mapN { (ck, quality, noWait) => (c: Client) =>
          c.reocr(ck, quality).flatMap { a =>
            println(s"queued job ${a.jobId} for ${a.citekey}")
            if noWait then Right(()) else pollJob(c, a.jobId, 0)
          }
        }
    }

  private val assets =
    Opts.subcommand("assets", "The figures stored with a source, and the URLs they are served at") {
      Opts.argument[String]("citekey").map(ck => (c: Client) =>
        c.assets(ck).map(rows => println(IngestMarkdown.assets(ck, rows.toVector))))
    }

  private val jobs = Opts.subcommand("jobs", "Recent OCR jobs, or one job by id") {
    (Opts.argument[String]("id").orNone,
      Opts.option[String]("status", "queued|running|done|failed").orNone,
      Opts.option[Int]("limit", "Max rows").orNone,
      Opts.option[String]("before", "The next older page: the id of the last job shown (the listing prints the command)").orNone)
      .mapN { (id, status, limit, before) => (c: Client) =>
        id match
          case Some(i) => c.job(i).map(j => println(IngestMarkdown.job(j)))
          case None    =>
            c.jobs(status, limit, before).map { (js, page) =>
              println(IngestMarkdown.jobs(js.toVector, page, status.map(_.trim).filter(_.nonEmpty),
                PageCmd.older(page, "jobs", List("status" -> status, "limit" -> limit.map(_.toString)))))
            }
      }
  }

  val all: Opts[Client => Either[CliError, Unit]] = reocr orElse assets orElse jobs
