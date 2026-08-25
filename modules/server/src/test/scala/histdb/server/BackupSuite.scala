package histdb.server

import histdb.core.*
import java.nio.file.{Files, Path}
import java.time.Instant

/** The parts of the server-side backup machinery that need no database and no `pg_dump`: the
  * maintenance lock, and the listing/resolution that keeps a client-supplied name inside
  * `backups.dir`. */
class BackupSuite extends munit.FunSuite:

  private def cfgWith(dir: Path): AppConfig =
    val base = AppConfig.load()
    base.copy(backups = base.backups.copy(dir = dir.toString))

  private val tmp = FunFixture[Path](
    setup = _ => Files.createTempDirectory("histdb-backups-test"),
    teardown = d =>
      Files.list(d).forEach(p => { val _ = Files.deleteIfExists(p) })
      val _ = Files.deleteIfExists(d)
  )

  // ── the maintenance lock ─────────────────────────────────────────────────────────────────────

  test("guard runs the operation when no restore is in progress"):
    val m = Maintenance()
    assertEquals(m.guard("seed")(Right(42)), Right(42))
    assertEquals(m.active, false)

  test("while a restore holds the lock, other writes are refused with the 503 code"):
    val m = Maintenance()
    val seen = m.exclusively("restore") {
      assertEquals(m.active, true)
      Right(m.guard("seed")(Right(42)))
    }
    assertEquals(seen, Right(Left(Backup.maintenance("seed"))))
    assertEquals(seen.toOption.flatMap(_.left.toOption).flatMap(_.code), Some(Backup.CodeMaintenance))

  test("one server's restore does not freeze another server's writes (locks are per instance)"):
    val a, b = Maintenance()
    assertEquals(a.exclusively("restore")(Right(b.guard("seed")(Right(42)))), Right(Right(42)))

  test("a second concurrent restore is refused, and the lock is released afterwards"):
    val m = Maintenance()
    // the inner attempt cannot take the flag the outer one holds, so its Left is what comes back
    val nested: Either[ApiError, String] = m.exclusively("restore")(m.exclusively("restore")(Right("inner")))
    assertEquals(nested, Left(Backup.maintenance("restore")))
    assertEquals(m.active, false)
    assertEquals(m.exclusively("restore")(Right("again")), Right("again"))

  test("the lock is released even when the operation throws"):
    val m = Maintenance()
    intercept[RuntimeException](m.exclusively("restore")(throw new RuntimeException("boom")))
    assertEquals(m.active, false)

  // ── listing and name resolution ──────────────────────────────────────────────────────────────

  tmp.test("an empty (or absent) backups directory lists nothing rather than failing") { d =>
    assertEquals(BackupService(cfgWith(d)).list, Right(Nil))
    assertEquals(BackupService(cfgWith(d.resolve("not-created-yet"))).list, Right(Nil))
  }

  tmp.test("the listing is newest first, and ignores everything that is not a dump") { d =>
    val names = List("histdb-20260824T101112Z.dump", "histdb-20260825T101112Z.dump")
    names.foreach(n => Files.writeString(d.resolve(n), "x"))
    Files.writeString(d.resolve("histdb-20260826T101112Z.dump.part"), "half-written") // in flight
    Files.writeString(d.resolve("notes.txt"), "unrelated")
    val listed = BackupService(cfgWith(d)).list.toOption.get
    assertEquals(listed.map(_.file), List("histdb-20260825T101112Z.dump", "histdb-20260824T101112Z.dump"))
    assertEquals(listed.map(_.bytes), List(1L, 1L))
    assert(listed.forall(b => Instant.parse(b.createdAt).toEpochMilli > 0))
  }

  tmp.test("resolve only accepts a name that is BOTH syntactically safe and in the listing") { d =>
    val name = Backup.fileName(Instant.parse("2026-08-25T14:51:12Z"))
    Files.writeString(d.resolve(name), "x")
    val svc = BackupService(cfgWith(d))
    assertEquals(svc.resolve(name), Right(d.toAbsolutePath.normalize.resolve(name)))
    // present on disk but never listed → refused
    assertEquals(svc.resolve("histdb-20260101T000000Z.dump").left.map(_.code), Left(Some(Write.CodeNotFound)))
  }

  tmp.test("resolve refuses to escape the backups directory") { d =>
    val outside = Files.createTempFile("histdb-outside", ".dump")
    try
      val svc = BackupService(cfgWith(d))
      val escapes = List(
        s"../${outside.getFileName}",
        "../../etc/passwd.dump",
        outside.toAbsolutePath.toString,
        "sub/histdb.dump")
      escapes.foreach(n => assert(svc.resolve(n).isLeft, s"should have been refused: '$n'"))
      // and nothing it returned can point outside the directory
      assert(svc.resolve(s"../${outside.getFileName}").isLeft)
    finally { val _ = Files.deleteIfExists(outside) }
  }

  tmp.test("a missing pg_dump is a clear, actionable error rather than an exception") { d =>
    val base = cfgWith(d)
    val svc  = BackupService(base.copy(backups = base.backups.copy(pgBinDir = d.resolve("nowhere").toString)))
    val err  = svc.create(Instant.now())
    assert(err.isLeft, err.toString)
    assert(err.left.exists(_.message.contains("pg_dump")), err.toString)
    assert(err.left.exists(_.message.contains("HISTDB_PG_BIN_DIR")), err.toString)
  }
