package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.config.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.backup.Backup
import com.histmersion.util.console.Console
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult

/** ONE way the CLI follows server work to its end (#1798 item 14): a background job (`jobs`) and a backup. Work that
  * ends badly exits with its problem's code ([[ExitCode]]), never 0 — a script that follows a job must not read a
  * failure as success. Following is bounded (G113): a poll that fails for a moment (the server restarting, a timeout)
  * is asked again after a growing wait, up to [[Limits.failedPolls]] in a row; work still running at
  * [[Limits.timeout]], or polls that keep failing, end with "outcome unknown" (8) and the last state seen. The fetch
  * and the clock are parameters, so the rules are tested without a server.
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

  /** The longest wait between two polls after failed ones. */
  val MaxBackoff: PollMs = PollMs(30000)

  /** How long work is followed before the CLI stops waiting for it. */
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends Newtype[Long, Pure]

  /** How many polls in a row may fail before the CLI stops asking. */
  type FailedPolls = FailedPolls.T
  object FailedPolls extends Newtype[Int, Pure]

  /** How long work is followed, and how many failed polls in a row are borne. */
  final case class Limits(timeout: TimeoutMs, failedPolls: FailedPolls)

  object Limits:
    /** The setting that gives [[Limits.timeout]], in seconds, and the variable that sets it. */
    val TimeoutVariable = "cli.follow-timeout-seconds (HISTMERSION_FOLLOW_TIMEOUT)"

    /** The failed polls in a row that are borne. */
    val FailedPollsBorne: FailedPolls = FailedPolls(5)

    /** Six hours (an OCR of a long book takes one), five failed polls in a row: the setting's default. */
    val Default: Limits = Limits(TimeoutMs(6L * 3600 * 1000), FailedPollsBorne)

    /** The limits with the timeout the setting gives, in whole seconds above 0. Pure. */
    def of(seconds: CliConfig.FollowTimeoutSeconds): Either[Problem, Limits] =
      Option
        .when(seconds.value > 0)(Limits(TimeoutMs(seconds.value * 1000), FailedPollsBorne))
        .toRight(
          Problem.Unclassified(Legacy.Text(s"$TimeoutVariable must be a whole number of seconds above 0, not $seconds"))
        )

  /** The wait after `failures` failed polls in a row: the poll interval doubled for each, at most [[MaxBackoff]]. Pure.
    */
  def backoff(interval: Long, failures: Int): Long =
    (1 until failures).foldLeft(interval)((w, _) => math.min(w * 2, MaxBackoff.value)).min(MaxBackoff.value)

  /** A failed poll worth asking again: the server out of reach or busy for now. Anything else (the job is gone, the key
    * refused) ends the follow at once.
    */
  def transient(e: Cli.Error): Boolean =
    Set(ExitCode.Unreachable, ExitCode.Unavailable, ExitCode.OutcomeUnknown).contains(e.exitCode)

  /** One rewritten progress line on stderr, always the same width. */
  object Progress:

    val Width = 72
    def line(text: String)(using console: Console): Unit = Cli.err.print(s"\r${" " * Width}\r$text")
    def clear()(using console: Console): Unit = Cli.err.print("\r" + " " * Width + "\r")

  private def failed(what: String, why: Option[String]): Cli.Error =
    Cli.Error.answered(
      Problem.Unclassified(Legacy.Text(s"$what failed: ${why.filter(_.nonEmpty).getOrElse("unknown reason")}"))
    )

  private def stateOf(j: JobInfo): String =
    val detail =
      j.progress.map(p => s"$p").orElse(j.problems.map(_.sentences.mkString("; "))).map(t => s" — $t").getOrElse("")
    s"${j.status}$detail"

  /** Poll job `id` until it is done or failed; `Right(job)` only when it succeeded. `unanswered` names what no final
    * answer came for ([[Client.unanswered]]).
    */
  @annotation.tailrec
  def job(using
      console: Console
  )(
      fetch: SpatialHierarchyResult.Job.Id => Either[Cli.Error, JobInfo],
      id: SpatialHierarchyResult.Job.Id,
      unanswered: String => Problem,
      limits: Limits = Limits.Default,
      sleep: Long => Unit = Thread.sleep,
      progress: String => Unit = Progress.line,
      clear: () => Unit = () => Progress.clear(),
      waited: Long = 0,
      failures: Int = 0,
      last: Option[JobInfo] = None
  ): Either[Cli.Error, JobInfo] =
    val seen = last.fold("none yet")(stateOf)
    fetch(id) match
      case Left(e) if transient(e) =>
        val n = failures + 1
        val why = e.problems.sentences.mkString("; ")
        if n >= limits.failedPolls.value || waited >= limits.timeout.value then
          clear()
          Left(Cli.Error(unanswered(s"job $id: $n poll(s) in a row failed ($why); last known state: $seen")))
        else
          val wait = backoff(Poll.value, n)
          progress(s"job $id: poll failed ($n of ${limits.failedPolls}), asking again in ${wait / 1000}s — $why")
          sleep(wait)
          job(fetch, id, unanswered, limits, sleep, progress, clear, waited + wait, n, last)
      case Left(e)  => clear(); Left(e)
      case Right(j) =>
        progress(s"job $id: ${stateOf(j)} (${waited / 1000}s)")
        if j.isTerminal then
          clear()
          if j.succeeded then Right(j) else Left(failed(s"job $id", j.problems.map(_.sentences.mkString("; "))))
        else if waited >= limits.timeout.value then
          clear()
          Left(
            Cli.Error(
              unanswered(
                s"job $id was still ${stateOf(j)} after ${waited / 1000}s (${Limits.TimeoutVariable} sets the wait); " +
                  "`histmersion jobs` shows how it ends"
              )
            )
          )
        else
          sleep(Poll.value)
          job(fetch, id, unanswered, limits, sleep, progress, clear, waited + Poll.value, 0, Some(j))

  /** Poll a backup the server answered `running` until its archive is written; a failure, or a "done" without a
    * checksum, is refused (2); bounded like [[job]].
    */
  @annotation.tailrec
  def backup(using
      console: Console
  )(
      fetch: () => Either[Cli.Error, Backup.Status],
      s: Backup.Status,
      unanswered: String => Problem,
      limits: Limits = Limits.Default,
      pollMs: BackupPollMs = BackupPoll,
      sleep: Long => Unit = Thread.sleep,
      progress: String => Unit = l => Cli.err.println(l),
      waited: Long = 0,
      failures: Int = 0
  ): Either[Cli.Error, Backup.Result] =
    s.stateOf match
      case Some(Backup.State.Running) if waited >= limits.timeout.value =>
        Left(
          Cli.Error(
            unanswered(
              s"backup ${s.file} was still running after ${waited / 1000}s (${Limits.TimeoutVariable} sets the wait); " +
                "`histmersion backups` shows whether it was written"
            )
          )
        )
      case Some(Backup.State.Running) =>
        if failures == 0 then progress(s"backup ${s.file} running for ${s.durationMs.value / 1000} s…")
        val wait = if failures == 0 then pollMs.value else backoff(pollMs.value, failures)
        sleep(wait)
        fetch() match
          case Right(next) => backup(fetch, next, unanswered, limits, pollMs, sleep, progress, waited + wait, 0)
          case Left(e) if transient(e) && failures + 1 < limits.failedPolls.value =>
            progress(
              s"backup ${s.file}: poll failed (${failures + 1} of ${limits.failedPolls}), asking again — ${e.problems.sentences.mkString("; ")}"
            )
            backup(fetch, s, unanswered, limits, pollMs, sleep, progress, waited + wait, failures + 1)
          case Left(e) if transient(e) =>
            Left(
              Cli.Error(
                unanswered(
                  s"backup ${s.file}: ${failures + 1} poll(s) in a row failed (${e.problems.sentences.mkString("; ")}); " +
                    "last known state: running"
                )
              )
            )
          case Left(e) => Left(e)
      case Some(Backup.State.Done) =>
        s.result.toRight(
          Cli.Error.answered(Problem.Unclassified(Legacy.Text(s"backup ${s.file} finished without a checksum")))
        )
      case _ => Left(failed(s"backup ${s.file}", s.problems.map(_.sentences.mkString("; "))))
