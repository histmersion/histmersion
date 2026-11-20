package com.histmersion
package cli
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{External, Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.config.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.backup.Backup
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.Sha256
import com.histmersion.api.ByteCount

/** [[JobFollow]] (#1798 item 14, G113): a followed job or backup that ends badly exits with its problem's code, never
  * 0; one that succeeds is `Right`; a poll that fails for a moment is asked again, and following is bounded, ending
  * "outcome unknown" with the last state seen. No server, no clock: the fetch and sleep are fakes.
  */
class JobFollowSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  private val id = SpatialHierarchyResult.Job.Id("j1")

  private def job(status: JobInfo.Status, error: Option[Problem] = None) =
    JobInfo(
      id = id,
      kind = JobKinds.Wire("ingest"),
      status = status,
      problems = error.map(NonEmptyVector.of(_))
    )

  /** A fake server answering `answers` in order (the last one repeats). */
  private def server[A](answers: A*): () => A =
    val it = answers.iterator
    // `var`: the answer repeated once the list runs out
    var last = answers.head
    () => { if it.hasNext then last = it.next(); last }

  private val quiet = (_: String) => ()

  /** What no final answer came for, as the client names it. */
  private val unanswered: String => Problem = what =>
    Problem.Upstream.Unanswered(Client.Histmersion, Problem.Upstream.Url("http://h:8000"), External.Reported(what))

  /** The server out of reach, for one poll. */
  private val unreachable: Cli.Error =
    Cli.Error(
      Problem.Upstream.Unreachable.here(
        Client.Histmersion,
        Problem.Upstream.Url("http://h:8000"),
        External.Reported("ConnectException: refused")
      )
    )

  private def follow(
      fetch: SpatialHierarchyResult.Job.Id => Either[Cli.Error, JobInfo],
      limits: JobFollow.Limits = JobFollow.Limits.Default,
      sleep: Long => Unit = _ => ()
  ) = JobFollow.job(fetch, id, unanswered, limits, sleep = sleep, progress = quiet, clear = () => ())

  test("a job followed to done is Right; queued and running are polled through"):
    val next = server(job(JobInfo.Status("queued")), job(JobInfo.Status("running")), job(JobInfo.Status("done")))
    var slept = 0 // `var`: how often the fake clock was asked to wait
    val r = follow(_ => Right(next()), sleep = _ => slept += 1)
    assertEquals(r.map(_.status), Right(JobInfo.Status("done")))
    assertEquals(slept, 2)

  test("a job that fails exits 2 with its error"):
    val r =
      follow(_ => Right(job(JobInfo.Status("failed"), Some(Problem.Unclassified(Legacy.Text("pdf is encrypted"))))))
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.problems.sentences.mkString("; ").contains("pdf is encrypted")))

  test("a job that failed with several problems names every one"):
    val r = follow(_ =>
      Right(
        job(JobInfo.Status("failed")).copy(problems =
          Some(
            NonEmptyVector.of[Problem](
              Problem.Run.Interrupted,
              Problem.Conflict.Busy(Problem.Conflict.Activity.Backup)
            )
          )
        )
      )
    )
    assertEquals(
      r.left.map(_.problems.sentences),
      Left(
        Vector(
          s"job j1 failed: interrupted by a restart; ${(Problem.Conflict.Busy(Problem.Conflict.Activity.Backup): Problem).sentence}"
        )
      )
    )

  test("an unreadable job state counts as failed, not as success"):
    assertEquals(follow(_ => Right(job(JobInfo.Status("interrupted")))).left.map(_.exitCode), Left(ExitCode.Refused))

  test("one poll fails, the next succeeds: the follow goes on to the job's end"):
    val next = server[Either[Cli.Error, JobInfo]](
      Right(job(JobInfo.Status("running"))),
      Left(unreachable),
      Right(job(JobInfo.Status("done")))
    )
    assertEquals(follow(_ => next()).map(_.status), Right(JobInfo.Status("done")))

  test("polls that keep failing end 'outcome unknown' with the last state seen, after waits that grow"):
    val next = server[Either[Cli.Error, JobInfo]](Right(job(JobInfo.Status("running"))), Left(unreachable))
    val waits = scala.collection.mutable.ListBuffer.empty[Long]
    val r = follow(_ => next(), sleep = waits += _)
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))
    val said = r.left.toOption.map(_.problems.sentences.mkString("; ")).getOrElse("")
    assert(said.contains("5 poll(s) in a row failed") && said.contains("last known state: running"), said)
    // one poll interval after the answer, then 1 s, 2 s, 4 s, 8 s between the failed polls
    assertEquals(waits.toList, List(1000L, 1000L, 2000L, 4000L, 8000L))

  test("the wait between failed polls doubles up to its ceiling"):
    assertEquals(
      (1 to 8).map(JobFollow.backoff(1000, _)).toList,
      List(1000L, 2000, 4000, 8000, 16000, 30000, 30000, 30000)
    )

  test("a job still running at the deadline ends 'outcome unknown', naming how to see its end"):
    val limits = JobFollow.Limits(JobFollow.TimeoutMs(3000), JobFollow.FailedPolls(5))
    var polls = 0 // `var`: how often the fake server was asked
    val r = follow(_ => { polls += 1; Right(job(JobInfo.Status("running"))) }, limits)
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))
    assertEquals(polls, 4)
    assert(r.left.exists(_.problems.sentences.mkString.contains("histmersion jobs")))

  test("a failed poll that is not for a moment (the job is gone) ends the follow at once with its own code"):
    val gone = Cli.Error.answered(Problem.NotFound.Entity(Problem.TypeName("Job"), Problem.Raw("j1")))
    var polls = 0 // `var`: how often the fake server was asked
    val r = follow { _ => polls += 1; Left(gone) }
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.NotFound))
    assertEquals(polls, 1)

  test("the timeout comes from HISTMERSION_FOLLOW_TIMEOUT in seconds; a bad value is a usage error"):
    assertEquals(JobFollow.Limits.of(None), Right(JobFollow.Limits.Default))
    assertEquals(JobFollow.Limits.of(Some(" 90 ")).map(_.timeout), Right(JobFollow.TimeoutMs(90000)))
    assertEquals(JobFollow.Limits.of(Some("0")).left.map(Cli.Error(_).exitCode), Left(ExitCode.Usage))
    assertEquals(JobFollow.Limits.of(Some("soon")).left.map(Cli.Error(_).exitCode), Left(ExitCode.Usage))

  private def backup(
      state: Backup.State.Wire,
      problems: Option[NonEmptyVector[Problem]] = None,
      sha: Option[Sha256] = None
  ) =
    Backup.Status(
      Backup.Status.File("histmersion-1.dump"),
      state,
      Backup.Status.DurationMs(1000),
      bytes = sha.map(_ => ByteCount(10L)),
      sha256 = sha,
      problems = problems
    )

  private def followBackup(
      fetch: () => Either[Cli.Error, Backup.Status],
      first: Backup.Status,
      limits: JobFollow.Limits = JobFollow.Limits.Default
  ) =
    JobFollow.backup(fetch, first, unanswered, limits, JobFollow.BackupPollMs(0), sleep = _ => (), progress = quiet)

  test("a backup that fails exits 2 — it used to exit 1, the usage code"):
    val next =
      server(
        backup(
          Backup.State.Wire("failed"),
          Some(NonEmptyVector.of[Problem](Problem.Unclassified(Legacy.Text("pg_dump: permission denied"))))
        )
      )
    val r = followBackup(() => Right(next()), backup(Backup.State.Wire("running")))
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.problems.sentences.mkString("; ").contains("permission denied")))

  test("a backup done without a checksum exits 2; done with one is Right"):
    val noSum = followBackup(() => Right(backup(Backup.State.Wire("done"))), backup(Backup.State.Wire("done")))
    assertEquals(noSum.left.map(_.exitCode), Left(ExitCode.Refused))
    val ok = followBackup(
      () => Right(backup(Backup.State.Wire("done"), sha = Some(Sha256("ab")))),
      backup(Backup.State.Wire("running"))
    )
    assertEquals(ok.map(_.sha256), Right(Sha256("ab")))

  test("a backup poll that fails once is asked again; polls that keep failing end 'outcome unknown'"):
    val once = server[Either[Cli.Error, Backup.Status]](
      Left(unreachable),
      Right(backup(Backup.State.Wire("done"), sha = Some(Sha256("ab"))))
    )
    assertEquals(followBackup(() => once(), backup(Backup.State.Wire("running"))).map(_.sha256), Right(Sha256("ab")))
    val never = followBackup(() => Left(unreachable), backup(Backup.State.Wire("running")))
    assertEquals(never.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))

  test("a backup still running at the deadline ends 'outcome unknown'"):
    val limits = JobFollow.Limits(JobFollow.TimeoutMs(0), JobFollow.FailedPolls(5))
    val r =
      followBackup(() => Right(backup(Backup.State.Wire("running"))), backup(Backup.State.Wire("running")), limits)
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))
