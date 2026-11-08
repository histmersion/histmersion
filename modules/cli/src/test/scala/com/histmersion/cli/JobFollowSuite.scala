package com.histmersion.cli

import com.histmersion.config.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.backup.Backup

/** [[JobFollow]] (#1798 item 14): a followed job or backup that ends badly exits 2 (the server said no), never 0 or 1;
  * one that succeeds is `Right`. No server, no clock: the fetch and sleep are fakes.
  */
class JobFollowSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private def job(status: JobInfo.Status, error: Option[JobInfo.Error] = None) =
    JobInfo(id = com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), kind = com.histmersion.api.jobs.JobKinds.Wire("ingest"), status = status, error = error)

  /** A fake server answering `answers` in order (the last one repeats). */
  private def server[A](answers: A*): () => A =
    val it = answers.iterator
    // `var`: the answer repeated once the list runs out
    var last = answers.head
    () => { if it.hasNext then last = it.next(); last }

  private val quiet = (_: String) => ()

  test("a job followed to done is Right; queued and running are polled through"):
    val next = server(job(JobInfo.Status("queued")), job(JobInfo.Status("running")), job(JobInfo.Status("done")))
    var slept = 0 // `var`: how often the fake clock was asked to wait
    val r = JobFollow.job(_ => Right(next()), com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), sleep = _ => slept += 1, progress = quiet, clear = () => ())
    assertEquals(r.map(_.status), Right(JobInfo.Status("done")))
    assertEquals(slept, 2)

  test("a job that fails exits 2 with its error"):
    val r = JobFollow.job(
      _ => Right(job(JobInfo.Status("failed"), Some(JobInfo.Error("pdf is encrypted")))),
      com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"),
      sleep = _ => (),
      progress = quiet,
      clear = () => ()
    )
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.message.value.contains("pdf is encrypted")))

  test("an unreadable job state counts as failed, not as success"):
    val r = JobFollow.job(_ => Right(job(JobInfo.Status("interrupted"))), com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), sleep = _ => (), progress = quiet, clear = () => ())
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))

  test("an unreachable server keeps its own exit code"):
    val r =
      JobFollow.job(_ => Left(Cli.Error(com.histmersion.cli.Cli.Error.Message("cannot reach"))), com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), sleep = _ => (), progress = quiet, clear = () => ())
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Unreachable))

  private def backup(
      state: Backup.State.Wire,
      error: Option[Backup.Status.Error] = None,
      sha: Option[com.histmersion.api.Sha256] = None
  ) =
    Backup.Status(com.histmersion.api.backup.Backup.Status.File("histmersion-1.dump"), state, com.histmersion.api.backup.Backup.Status.DurationMs(1000), bytes = sha.map(_ => com.histmersion.api.ByteCount(10L)), sha256 = sha, error = error)

  test("a backup that fails exits 2 — it used to exit 1, the usage code"):
    val next = server(backup(Backup.State.Wire("failed"), Some(Backup.Status.Error("pg_dump: permission denied"))))
    val r = JobFollow.backup(() => Right(next()), backup(Backup.State.Wire("running")), pollMs = com.histmersion.cli.JobFollow.BackupPollMs(0), sleep = _ => (), progress = quiet)
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.message.value.contains("permission denied")))

  test("a backup done without a checksum exits 2; done with one is Right"):
    val noSum =
      JobFollow.backup(() => Right(backup(Backup.State.Wire("done"))), backup(Backup.State.Wire("done")), pollMs = com.histmersion.cli.JobFollow.BackupPollMs(0), sleep = _ => (), progress = quiet)
    assertEquals(noSum.left.map(_.exitCode), Left(ExitCode.Refused))
    val ok = JobFollow.backup(
      () => Right(backup(Backup.State.Wire("done"), sha = Some(com.histmersion.api.Sha256("ab")))),
      backup(Backup.State.Wire("running")),
      pollMs = com.histmersion.cli.JobFollow.BackupPollMs(0),
      sleep = _ => (),
      progress = quiet
    )
    assertEquals(ok.map(_.sha256), Right(com.histmersion.api.Sha256("ab")))
