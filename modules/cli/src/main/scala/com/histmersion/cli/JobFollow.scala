package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.config.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.backup.Backup
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult

/** ONE way the CLI follows server work to its end (#1798 item 14): a background job (`jobs`) and a backup. A run that
  * ends badly is ALWAYS `ExitCode.Refused` (2) — a script that follows a job must not read a failure as success, nor as
  * a usage error (a failed backup used to exit 1). The fetch and the clock are parameters, so the rule is tested
  * without a server.
  */
object JobFollow:

  /** How often a job is asked for. This process's own cadence, not a server tunable. */
  type PollMs = PollMs.T
  object PollMs extends Newtype[Long, Pure]
  type BackupPollMs = BackupPollMs.T
  object BackupPollMs extends Newtype[Long, Pure]

  val Poll: PollMs = PollMs(1000)

  /** How often a backup is asked for: a dump runs for minutes, and each answer prints a line. */
  val BackupPoll: BackupPollMs = BackupPollMs(5000)

  /** One rewritten progress line on stderr, always the same width. */
  object Progress:

    val Width = 72
    def line(text: String)(using console: Console): Unit = Cli.err.print(s"\r${" " * Width}\r$text")
    def clear()(using console: Console): Unit = Cli.err.print("\r" + " " * Width + "\r")

  private def failed(what: String, why: Option[String]): Cli.Error =
    Cli.Error(
      Problem.Unclassified(Legacy.Text(s"$what failed: ${why.filter(_.nonEmpty).getOrElse("unknown reason")}")),
      ExitCode.Refused
    )

  /** Poll job `id` until it is done or failed; `Right(job)` only when it succeeded. */
  @annotation.tailrec
  def job(using
      console: Console
  )(
      fetch: SpatialHierarchyResult.Job.Id => Either[Cli.Error, JobInfo],
      id: SpatialHierarchyResult.Job.Id,
      waited: Long = 0,
      sleep: Long => Unit = Thread.sleep,
      progress: String => Unit = Progress.line,
      clear: () => Unit = () => Progress.clear()
  ): Either[Cli.Error, JobInfo] =
    fetch(id) match
      case Left(e)  => clear(); Left(e)
      case Right(j) =>
        val detail = j.progress.map(p => s"$p").orElse(j.error.map(_.sentence)).map(t => s" — $t").getOrElse("")
        progress(s"job $id: ${j.status}$detail (${waited / 1000}s)")
        if !j.isTerminal then
          sleep(Poll.value)
          job(fetch, id, waited + Poll.value, sleep, progress, clear)
        else
          clear()
          if j.succeeded then Right(j) else Left(failed(s"job $id", j.error.map(_.sentence)))

  /** Poll a backup the server answered `running` until its archive is written; a failure, or a "done" without a
    * checksum, is `ExitCode.Refused`.
    */
  @annotation.tailrec
  def backup(using
      console: Console
  )(
      fetch: () => Either[Cli.Error, Backup.Status],
      s: Backup.Status,
      pollMs: BackupPollMs = BackupPoll,
      sleep: Long => Unit = Thread.sleep,
      progress: String => Unit = l => Cli.err.println(l)
  ): Either[Cli.Error, Backup.Result] =
    s.stateOf match
      case Some(Backup.State.Running) =>
        progress(s"backup ${s.file} running for ${s.durationMs.value / 1000} s…")
        sleep(pollMs.value)
        fetch() match
          case Right(next) => backup(fetch, next, pollMs, sleep, progress)
          case Left(e)     => Left(e)
      case Some(Backup.State.Done) =>
        s.result.toRight(
          Cli.Error(
            Problem.Unclassified(Legacy.Text(s"backup ${s.file} finished without a checksum")),
            ExitCode.Refused
          )
        )
      case _ => Left(failed(s"backup ${s.file}", s.error.map(_.sentence)))
