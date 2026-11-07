package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.data.backup.BackupJobs
import com.histmersion.util.data.maintenance.WriteGate

import com.histmersion.api.{Markdown, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import com.histmersion.api.backup.Backup
import com.histmersion.api.Api

/** The parts of the server-side backup machinery that need no database and no `pg_dump`: the maintenance lock, and the
  * listing/resolution that keeps a client-supplied name inside `backups.dir`.
  */
class BackupSuite extends munit.FunSuite:

  private def cfgWith(dir: Path): AppConfig =
    val base = AppConfig.load()
    base.modify(_.backups).setTo(base.backups.modify(_.dir).setTo(dir.toString))

  private val tmp = FunFixture[Path](
    setup = _ => Files.createTempDirectory("histmersion-backups-test"),
    teardown = d =>
      Files.list(d).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }
      val _ = Files.deleteIfExists(d)
  )

  // ── the maintenance lock ─────────────────────────────────────────────────────────────────────

  test("guard runs the operation when no restore is in progress"):
    val m = WriteGate()
    assertEquals(m.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("seed"))(Right(42)), Right(42))
    assertEquals(m.active, false)

  test("while a restore holds the lock, other writes are refused with the 503 code"):
    val m = WriteGate()
    val seen = m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore")) {
      assertEquals(m.active, true)
      Right(m.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("seed"))(Right(42)))
    }
    assertEquals(seen, Right(Left(Backup.maintenance("seed"))))
    assertEquals(seen.toOption.flatMap(_.left.toOption).flatMap(_.code), Some(Backup.CodeMaintenance))

  test("one server's restore does not freeze another server's writes (locks are per instance)"):
    val a, b = WriteGate()
    assertEquals(a.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(Right(b.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("seed"))(Right(42)))), Right(Right(42)))

  test("a second concurrent restore is refused, and the lock is released afterwards"):
    val m = WriteGate()
    // the inner attempt cannot take the flag the outer one holds, so its Left is what comes back
    val nested: Either[Api.Error, String] = m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(Right("inner")))
    assertEquals(nested, Left(Backup.maintenance("restore")))
    assertEquals(m.active, false)
    assertEquals(m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(Right("again")), Right("again"))

  test("the lock is released even when the operation throws"):
    val m = WriteGate()
    intercept[RuntimeException](m.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(throw new RuntimeException("boom")))
    assertEquals(m.active, false)

  // ── listing and name resolution ──────────────────────────────────────────────────────────────

  tmp.test("an empty (or absent) backups directory lists nothing rather than failing") { d =>
    assertEquals(BackupFixtures.service(cfgWith(d)).list, Right(Nil))
    assertEquals(BackupFixtures.service(cfgWith(d.resolve("not-created-yet"))).list, Right(Nil))
  }

  tmp.test("the listing is newest first, and ignores everything that is not a dump") { d =>
    val names = List("histmersion-20260824T101112Z.dump", "histmersion-20260825T101112Z.dump")
    names.foreach(n => Files.writeString(d.resolve(n), "x"))
    Files.writeString(d.resolve("histmersion-20260826T101112Z.dump.part"), "half-written") // in flight
    Files.writeString(d.resolve("notes.txt"), "unrelated")
    val listed = BackupFixtures.service(cfgWith(d)).list.toOption.get
    assertEquals(listed.map(_.file), List("histmersion-20260825T101112Z.dump", "histmersion-20260824T101112Z.dump"))
    assertEquals(listed.map(_.bytes), List(1L, 1L))
    assert(listed.forall(b => Instant.parse(b.createdAt).toEpochMilli > 0))
  }

  tmp.test("resolve only accepts a name that is BOTH syntactically safe and in the listing") { d =>
    val name = Backup.fileName(Instant.parse("2026-08-25T14:51:12Z"))
    Files.writeString(d.resolve(name), "x")
    val svc = BackupFixtures.service(cfgWith(d))
    assertEquals(svc.archive(name).map(_.toPath), Right(d.toAbsolutePath.normalize.resolve(name)))
    // present on disk but never listed → refused
    assertEquals(svc.resolve(com.histmersion.util.data.backup.PreparedBackup.File("histmersion-20260101T000000Z.dump")).left.map(_.code), Left(Some(Write.CodeNotFound)))
  }

  tmp.test("resolve refuses to escape the backups directory") { d =>
    val outside = Files.createTempFile("histmersion-outside", ".dump")
    try
      val svc = BackupFixtures.service(cfgWith(d))
      val escapes = List(
        s"../${outside.getFileName}",
        "../../etc/passwd.dump",
        outside.toAbsolutePath.toString,
        "sub/histmersion.dump"
      )
      escapes.foreach(n => assert(svc.resolve(n).isLeft, s"should have been refused: '$n'"))
      // and nothing it returned can point outside the directory
      assert(svc.resolve(s"../${outside.getFileName}").isLeft)
    finally { val _ = Files.deleteIfExists(outside) }
  }

  tmp.test("a missing pg_dump is a clear, actionable error rather than an exception") { d =>
    val base = cfgWith(d)
    val svc = BackupFixtures.service(base.modify(_.backups).setTo(base.backups.modify(_.pgBinDir).setTo(d.resolve("nowhere").toString)))
    val err = svc.create(Instant.now())
    assert(err.isLeft, err.toString)
    assert(err.left.exists(_.message.contains("pg_dump")), err.toString)
    assert(err.left.exists(_.message.contains("HISTMERSION_PG_BIN_DIR")), err.toString)
  }

  // ── the timeout that could never fire ────────────────────────────────────────────────────────

  tmp.test("a hung pg_dump is KILLED at the timeout instead of hanging the request") { d =>
    // The old `run` read the merged output stream to EOF and only then called `waitFor(timeout)`.
    // A pipe reaches EOF when the child closes stdout, i.e. when it exits — so `waitFor` was always
    // handed an already-dead process and the timeout was decorative. This stands in a `pg_dump`
    // that lives far longer than the configured budget.
    val bin = Files.createDirectory(d.resolve("bin"))
    val fake = bin.resolve("pg_dump")
    Files.writeString(fake, "#!/bin/sh\necho starting\nsleep 30\n")
    val _ = fake.toFile.setExecutable(true)

    val base = cfgWith(d)
    val svc =
      BackupFixtures.service(base.modify(_.backups).setTo(base.backups.modify(_.pgBinDir).setTo(bin.toString).modify(_.timeoutSeconds).setTo(1)))

    val started = System.nanoTime()
    val r = svc.create(Instant.now())
    val elapsed = (System.nanoTime() - started) / 1000000L

    assert(r.isLeft, r.toString)
    assert(r.left.exists(_.message.contains("timed out")), r.toString)
    assert(elapsed < 15000, s"the timeout took ${elapsed}ms to fire — it never fired before")
    // …and the half-written archive is not left behind in the listing
    assertEquals(svc.list, Right(Nil))
    Files.deleteIfExists(fake)
    Files.deleteIfExists(bin)
  }

  tmp.test("a fast command's output still comes back, in order") { d =>
    // the output now goes to a file rather than a pipe; it must still be readable afterwards
    val bin = Files.createDirectory(d.resolve("bin"))
    val fake = bin.resolve("pg_dump")
    Files.writeString(fake, "#!/bin/sh\necho one\necho two >&2\nexit 3\n")
    val _ = fake.toFile.setExecutable(true)
    val base = cfgWith(d)
    val svc = BackupFixtures.service(base.modify(_.backups).setTo(base.backups.modify(_.pgBinDir).setTo(bin.toString)))
    val r = svc.create(Instant.now())
    assert(r.isLeft, r.toString)
    val msg = r.swap.toOption.get.message
    assert(msg.contains("exit 3"), msg)
    assert(msg.contains("one") && msg.contains("two"), s"stderr is merged into stdout: $msg")
    Files.deleteIfExists(fake)
    Files.deleteIfExists(bin)
  }

  // ── backups as jobs: a dump outlives the request that started it ────────────────────────────

  /** A stand-in `pg_dump` that sleeps, then writes the `-f` target (or fails when `fail` is set). */
  private def fakeDump(d: Path, sleepS: Int, fail: Boolean = false): Path =
    val bin = Files.createDirectories(d.resolve("bin"))
    val fake = bin.resolve("pg_dump")
    val body =
      if fail then s"sleep $sleepS\necho 'pg_dump: error: connection refused' >&2\nexit 1\n"
      else s"""while [ "$$1" != "-f" ]; do shift; done\nsleep $sleepS\necho dump > "$$2"\n"""
    Files.writeString(fake, "#!/bin/sh\n" + body)
    val _ = fake.toFile.setExecutable(true)
    bin

  private def jobs(d: Path, bin: Path, waitS: Int): BackupJobs =
    val base = cfgWith(d)
    BackupJobs(BackupFixtures.service(base.modify(_.backups).setTo(base.backups.modify(_.pgBinDir).setTo(bin.toString))), waitS)

  private def cleanBin(bin: Path): Unit =
    Files.list(bin).forEach { p =>
      val _ = Files.deleteIfExists(p)
    }
    val _ = Files.deleteIfExists(bin)

  tmp.test("a finished dump prunes the oldest archives down to backups.keep, and nothing else") { d =>
    val bin = fakeDump(d, sleepS = 0)
    val old = List(
      "histmersion-20260101T000000Z.dump",
      "histmersion-20260102T000000Z.dump",
      "histmersion-20260103T000000Z.dump",
      "histmersion-20260103T000000Z-2.dump"
    )
    old.foreach(n => Files.writeString(d.resolve(n), "old"))
    Files.writeString(d.resolve("my-own-copy.dump"), "the user's") // not ours: never pruned
    val base = cfgWith(d)
    val svc = BackupFixtures.service(base.modify(_.backups).setTo(base.backups.modify(_.pgBinDir).setTo(bin.toString).modify(_.keep).setTo(3)))
    val r = svc.create(Instant.parse("2026-09-26T12:00:00Z")).fold(e => fail(e.message), identity)
    assertEquals(r.pruned, List("histmersion-20260101T000000Z.dump", "histmersion-20260102T000000Z.dump"))
    val left = Files.list(d).map(_.getFileName.toString).toList.asScala.toSet - "bin"
    assertEquals(
      left,
      Set(r.file, "histmersion-20260103T000000Z.dump", "histmersion-20260103T000000Z-2.dump", "my-own-copy.dump")
    )
    assert(Markdown.backup(r).contains("histmersion-20260101T000000Z.dump"), "the output says what went")
    cleanBin(bin)
  }

  tmp.test("keep = 0 keeps every archive") { d =>
    val bin = fakeDump(d, sleepS = 0)
    (1 to 7).foreach(i => Files.writeString(d.resolve(f"histmersion-202601$i%02dT000000Z.dump"), "old"))
    val base = cfgWith(d)
    val r = BackupFixtures
      .service(base.copy(backups = base.backups.copy(pgBinDir = bin.toString, keep = 0)))
      .create(Instant.now())
      .fold(e => fail(e.message), identity)
    assertEquals(r.pruned, Nil)
    assertEquals(Files.list(d).filter(_.toString.endsWith(".dump")).count(), 8L)
    cleanBin(bin)
  }

  tmp.test("a dump that outlasts the request wait is reported running, then done") { d =>
    val bin = fakeDump(d, sleepS = 2)
    val j = jobs(d, bin, waitS = 0)
    val first = j.start(Instant.now())
    assertEquals(first.map(_.stateOf), Right(Some(Backup.State.Running)), first.toString)
    assert(j.running)
    // a second request while it runs joins the same job instead of dumping twice
    assertEquals(j.start(Instant.now()).map(_.file), first.map(_.file))
    val deadline = System.nanoTime() + 15_000_000_000L
    while j.running && System.nanoTime() < deadline do Thread.sleep(100)
    val done = j.status
    assertEquals(done.flatMap(_.stateOf), Some(Backup.State.Done), done.toString)
    assert(done.flatMap(_.result).exists(r => r.bytes > 0 && r.sha256.length == 64), done.toString)
    assert(Files.exists(d.resolve(first.toOption.get.file)), "the archive is in place")
    cleanBin(bin)
  }

  tmp.test("a dump that fits in the wait is answered done in the same request") { d =>
    val bin = fakeDump(d, sleepS = 0)
    val r = jobs(d, bin, waitS = 10).start(Instant.now())
    assertEquals(r.map(_.stateOf), Right(Some(Backup.State.Done)), r.toString)
    cleanBin(bin)
  }

  tmp.test("a failed dump is reported failed, with pg_dump's own message") { d =>
    val bin = fakeDump(d, sleepS = 0, fail = true)
    val j = jobs(d, bin, waitS = 10)
    val r = j.start(Instant.now())
    assertEquals(r.map(_.stateOf), Right(Some(Backup.State.Failed)), r.toString)
    assert(r.toOption.flatMap(_.error).exists(_.contains("connection refused")), r.toString)
    assertEquals(j.status.flatMap(_.stateOf), Some(Backup.State.Failed))
    cleanBin(bin)
  }

  test("no status before the first backup"):
    val base = AppConfig.load()
    assertEquals(BackupJobs(BackupFixtures.service(base), 1).status, None)
