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

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import com.histmersion.lint.{tolerated, LintRule}

object JobFollow:

  /** How often a job is asked for. This process's own cadence, not a server tunable. */
  val Poll: FiniteDuration = 1.second

  /** How often a backup is asked for: a dump runs for minutes, and each answer prints a line. */
  val BackupPoll: FiniteDuration = 5.seconds

  val MaxBackoff: FiniteDuration = 30.seconds

  type FailedPolls = FailedPolls.T
  object FailedPolls extends Newtype[Int, Pure]:
    extension (borne: FailedPolls) def reachedBy(inARow: Int): Boolean = inARow >= borne.value

  final case class Limits(timeout: FiniteDuration, failedPolls: FailedPolls)

  object Limits:
    val TimeoutVariable = "cli.follow-timeout-seconds (HISTMERSION_FOLLOW_TIMEOUT)"

    val FailedPollsBorne: FailedPolls = FailedPolls(5)

    val Default: Limits = Limits(6.hours, FailedPollsBorne)

    def of(seconds: CliConfig.FollowTimeoutSeconds): Either[Problem, Limits] =
      seconds.duration
        .map(Limits(_, FailedPollsBorne))
        .toRight(
          Problem.Config.Setting(
            Problem.SettingKey("cli.follow-timeout-seconds"),
            Problem.Config.Fault
              .NotPositiveSeconds(Problem.Raw.of(seconds), Problem.SettingKey("HISTMERSION_FOLLOW_TIMEOUT"))
          )
        )

  def backoff(interval: FiniteDuration, failures: Int): FiniteDuration =
    (1 until failures).foldLeft(interval)((w, _) => (w * 2).min(MaxBackoff)).min(MaxBackoff)

  /** One rewritten progress line on stderr, always the same width. */
  object Progress:

    val Width = 72
    def line(text: String)(using console: Console): Unit = Cli.err.print(s"\r${" " * Width}\r$text")
    def clear()(using console: Console): Unit = Cli.err.print("\r" + " " * Width + "\r")

  private def pause(d: FiniteDuration): Unit = Thread.sleep(d.toMillis)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def failed(what: String, why: Option[String]): Cli.Error =
    Cli.Error.answered(
      Problem.Unclassified(Legacy.Text(s"$what failed: ${why.filter(_.nonEmpty).getOrElse("unknown reason")}"))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @annotation.tailrec
  def job(using
      console: Console
  )(
      fetch: SpatialHierarchyResult.Job.Id => Either[Cli.Error, JobInfo],
      id: SpatialHierarchyResult.Job.Id,
      unanswered: String => Problem,
      limits: Limits = Limits.Default,
      sleep: FiniteDuration => Unit = pause,
      progress: String => Unit = Progress.line,
      clear: () => Unit = () => Progress.clear(),
      waited: FiniteDuration = Duration.Zero,
      failures: Int = 0,
      last: Option[JobInfo] = None
  ): Either[Cli.Error, JobInfo] =
    val seen = last.fold("none yet")(_.stateLine)
    fetch(id) match
      case Left(e) if e.transient =>
        val n = failures + 1
        val why = e.problems.sentences.mkString("; ")
        if limits.failedPolls.reachedBy(n) || waited >= limits.timeout then
          clear()
          Left(Cli.Error(unanswered(s"job $id: $n poll(s) in a row failed ($why); last known state: $seen")))
        else
          val wait = backoff(Poll, n)
          progress(s"job $id: poll failed ($n of ${limits.failedPolls}), asking again in ${wait.toSeconds}s — $why")
          sleep(wait)
          job(fetch, id, unanswered, limits, sleep, progress, clear, waited + wait, n, last)
      case Left(e)  => clear(); Left(e)
      case Right(j) =>
        progress(s"job $id: ${j.stateLine} (${waited.toSeconds}s)")
        if j.isTerminal then
          clear()
          if j.succeeded then Right(j) else Left(failed(s"job $id", j.problems.map(_.sentences.mkString("; "))))
        else if waited >= limits.timeout then
          clear()
          Left(
            Cli.Error(
              unanswered(
                s"job $id was still ${j.stateLine} after ${waited.toSeconds}s (${Limits.TimeoutVariable} sets the wait); " +
                  "`histmersion jobs` shows how it ends"
              )
            )
          )
        else
          sleep(Poll)
          job(fetch, id, unanswered, limits, sleep, progress, clear, waited + Poll, 0, Some(j))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  @annotation.tailrec
  def backup(using
      console: Console
  )(
      fetch: () => Either[Cli.Error, Backup.Status],
      s: Backup.Status,
      unanswered: String => Problem,
      limits: Limits = Limits.Default,
      interval: FiniteDuration = BackupPoll,
      sleep: FiniteDuration => Unit = pause,
      progress: String => Unit = l => Cli.err.println(l),
      waited: FiniteDuration = Duration.Zero,
      failures: Int = 0
  ): Either[Cli.Error, Backup.Result] =
    s.stateOf match
      case Some(Backup.State.Running) if waited >= limits.timeout =>
        Left(
          Cli.Error(
            unanswered(
              s"backup ${s.file} was still running after ${waited.toSeconds}s (${Limits.TimeoutVariable} sets the wait); " +
                "`histmersion backups` shows whether it was written"
            )
          )
        )
      case Some(Backup.State.Running) =>
        if failures == 0 then progress(s"backup ${s.file} running for ${s.durationMs.value / 1000} s…")
        val wait = if failures == 0 then interval else backoff(interval, failures)
        sleep(wait)
        fetch() match
          case Right(next) => backup(fetch, next, unanswered, limits, interval, sleep, progress, waited + wait, 0)
          case Left(e) if e.transient && !limits.failedPolls.reachedBy(failures + 1) =>
            progress(
              s"backup ${s.file}: poll failed (${failures + 1} of ${limits.failedPolls}), asking again — ${e.problems.sentences.mkString("; ")}"
            )
            backup(fetch, s, unanswered, limits, interval, sleep, progress, waited + wait, failures + 1)
          case Left(e) if e.transient =>
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
          Cli.Error.answered(Problem.Unclassified(Legacy.Text(shown"backup ${s.file} finished without a checksum")))
        )
      case _ => Left(failed(shown"backup ${s.file}", s.problems.map(_.sentences.mkString("; "))))
