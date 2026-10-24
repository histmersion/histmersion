package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** [[JobFollow]] (#1798 item 14): a followed job or backup that ends badly exits 2 (the server said
  * no), never 0 or 1; one that succeeds is `Right`. No server, no clock: the fetch and sleep are fakes. */
class JobFollowSuite extends munit.FunSuite:

  private def job(status: String, error: Option[String] = None) =
    JobInfo(id = "j1", kind = "ingest", status = status, error = error)

  /** A fake server answering `answers` in order (the last one repeats). */
  private def server[A](answers: A*): () => A =
    val it = answers.iterator
    // `var`: the answer repeated once the list runs out
    var last = answers.head
    () => { if it.hasNext then last = it.next(); last }

  private val quiet = (_: String) => ()

  test("a job followed to done is Right; queued and running are polled through"):
    val next = server(job("queued"), job("running"), job("done"))
    var slept = 0 // `var`: how often the fake clock was asked to wait
    val r = JobFollow.job(_ => Right(next()), "j1", sleep = _ => slept += 1, progress = quiet, clear = () => ())
    assertEquals(r.map(_.status), Right("done"))
    assertEquals(slept, 2)

  test("a job that fails exits 2 with its error"):
    val r = JobFollow.job(_ => Right(job("failed", Some("pdf is encrypted"))), "j1", sleep = _ => (), progress = quiet, clear = () => ())
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.message.contains("pdf is encrypted")))

  test("an unreadable job state counts as failed, not as success"):
    val r = JobFollow.job(_ => Right(job("interrupted")), "j1", sleep = _ => (), progress = quiet, clear = () => ())
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))

  test("an unreachable server keeps its own exit code"):
    val r = JobFollow.job(_ => Left(CliError("cannot reach")), "j1", sleep = _ => (), progress = quiet, clear = () => ())
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Unreachable))

  private def backup(state: String, error: Option[String] = None, sha: Option[String] = None) =
    BackupStatus("histmersion-1.dump", state, 1000, bytes = sha.map(_ => 10L), sha256 = sha, error = error)

  test("a backup that fails exits 2 — it used to exit 1, the usage code"):
    val next = server(backup("failed", Some("pg_dump: permission denied")))
    val r = JobFollow.backup(() => Right(next()), backup("running"), pollMs = 0, sleep = _ => (), progress = quiet)
    assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
    assert(r.left.exists(_.message.contains("permission denied")))

  test("a backup done without a checksum exits 2; done with one is Right"):
    val noSum = JobFollow.backup(() => Right(backup("done")), backup("done"), pollMs = 0, sleep = _ => (), progress = quiet)
    assertEquals(noSum.left.map(_.exitCode), Left(ExitCode.Refused))
    val ok = JobFollow.backup(() => Right(backup("done", sha = Some("ab"))), backup("running"), pollMs = 0,
      sleep = _ => (), progress = quiet)
    assertEquals(ok.map(_.sha256), Right("ab"))
