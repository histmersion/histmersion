package histdb.core

import java.time.Instant

/** Pure half of backup/restore (plan §1.10): the file naming, the path-traversal gate and the
  * JDBC-URL → `pg_dump` argument translation. No database, no `pg_dump`, runs in CI in milliseconds. */
class BackupSuite extends munit.FunSuite:

  // ── the timestamp format ─────────────────────────────────────────────────────────────────────

  test("file name is histdb-<UTC compact timestamp>.dump"):
    val at = Instant.parse("2026-08-25T14:51:12Z")
    assertEquals(Backup.timestamp(at), "20260825T145112Z")
    assertEquals(Backup.fileName(at), "histdb-20260825T145112Z.dump")

  test("the timestamp is UTC and second-resolution, whatever the instant carries"):
    // a non-UTC-looking instant with sub-second precision still renders as whole UTC seconds
    assertEquals(Backup.fileName(Instant.parse("2026-01-02T03:04:05.987654Z")),
      "histdb-20260102T030405Z.dump")

  test("names sort chronologically as plain text (that is why the listing can sort by name)"):
    val a = Backup.fileName(Instant.parse("2026-08-25T09:00:00Z"))
    val b = Backup.fileName(Instant.parse("2026-08-25T10:00:00Z"))
    val c = Backup.fileName(Instant.parse("2026-09-01T00:00:00Z"))
    assertEquals(List(c, a, b).sorted, List(a, b, c))

  test("a generated name passes the validator (round-trip)"):
    assertEquals(Backup.safeName(Backup.fileName(Instant.now())).map(_ => "ok"), Right("ok"))

  // ── the path-traversal gate ──────────────────────────────────────────────────────────────────

  test("safeName accepts a plain dump file name"):
    assertEquals(Backup.safeName("histdb-20260825T145112Z.dump"), Right("histdb-20260825T145112Z.dump"))
    assertEquals(Backup.safeName("  histdb-20260825T145112Z.dump  "), Right("histdb-20260825T145112Z.dump"))
    assertEquals(Backup.safeName("my_copy-2.dump"), Right("my_copy-2.dump"))

  test("safeName rejects every path component"):
    val bad = List(
      "../../etc/passwd.dump",       // traversal
      "..",                          // traversal, no extension
      "../histdb.dump",
      "sub/histdb.dump",             // separator
      "sub\\histdb.dump",            // windows separator
      "/absolute/histdb.dump",
      "/etc/passwd",
      ".hidden.dump",                // must start alphanumeric, so no dotfiles
      "histdb .dump",                // space
      "histdb\u0000.dump",          // NUL byte
      "histdb-$(whoami).dump",       // shell metacharacters (it reaches a command line)
      "histdb-`id`.dump",
      "histdb;rm -rf.dump"
    )
    bad.foreach(n => assert(Backup.safeName(n).isLeft, s"should have been rejected: '$n'"))

  test("safeName rejects a missing name, a wrong extension and an over-long name"):
    assert(Backup.safeName("").isLeft)
    assert(Backup.safeName("   ").isLeft)
    assert(Backup.safeName("histdb.sql").isLeft)
    assert(Backup.safeName(".dump").isLeft)          // extension only
    assert(Backup.safeName("a" * 200 + ".dump").isLeft)

  test("safeName's message says what is wrong"):
    assert(Backup.safeName("../x.dump").left.exists(_.contains("no directories")))
    assert(Backup.safeName("x.sql").left.exists(_.contains(".dump")))

  // ── JDBC URL → pg_dump arguments ─────────────────────────────────────────────────────────────

  test("parseJdbc: host, port and database"):
    val t = Backup.parseJdbc("jdbc:postgresql://db.internal:6000/histdb").toOption.get
    assertEquals(t.host, Some("db.internal"))
    assertEquals(t.port, Some(6000))
    assertEquals(t.database, "histdb")
    assertEquals(t.args, List("-h", "db.internal", "-p", "6000", "-d", "histdb", "-w"))

  test("parseJdbc: query parameters are not part of the database name"):
    val t = Backup.parseJdbc("jdbc:postgresql://h:6000/histdb?ssl=true&ApplicationName=x").toOption.get
    assertEquals(t.database, "histdb")

  test("parseJdbc: a missing port is left to libpq (no -p, and no port literal in the code)"):
    val t = Backup.parseJdbc("jdbc:postgresql://h/histdb").toOption.get
    assertEquals(t.port, None)
    assertEquals(t.args, List("-h", "h", "-d", "histdb", "-w"))

  test("parseJdbc: the host-less form"):
    val t = Backup.parseJdbc("jdbc:postgresql:histdb").toOption.get
    assertEquals(t.host, None)
    assertEquals(t.port, None)
    assertEquals(t.args, List("-d", "histdb", "-w"))

  test("parseJdbc: bracketed IPv6, with and without a port"):
    val a = Backup.parseJdbc("jdbc:postgresql://[::1]:6000/histdb").toOption.get
    assertEquals((a.host, a.port), (Some("[::1]"), Some(6000)))
    val b = Backup.parseJdbc("jdbc:postgresql://[::1]/histdb").toOption.get
    assertEquals((b.host, b.port), (Some("[::1]"), None))

  test("parseJdbc: rejects a non-postgres URL, a missing database and a non-numeric port"):
    assert(Backup.parseJdbc("jdbc:mysql://h:3306/histdb").isLeft)
    assert(Backup.parseJdbc("jdbc:postgresql://h:6000/").isLeft)
    assert(Backup.parseJdbc("jdbc:postgresql://h:6000").isLeft)
    assert(Backup.parseJdbc("jdbc:postgresql://h:not-a-port/histdb").isLeft)
    assert(Backup.parseJdbc("").isLeft)

  test("the configured default URL parses (reference.conf and the parser agree)"):
    assert(Backup.parseJdbc(AppConfig.load().db.url).isRight)

  // ── how pg_restore's exit code is read ───────────────────────────────────────────────────────

  private val skew = List(
    """pg_restore: error: could not execute query: ERROR:  unrecognized configuration parameter "transaction_timeout"""",
    "Command was: SET transaction_timeout = 0;",
    "pg_restore: warning: errors ignored on restore: 1")

  test("restoreOutcome: a clean run has no warnings"):
    assertEquals(Backup.restoreOutcome(0, Nil), Right(Nil))

  test("restoreOutcome: exit 1 WITH the 'errors ignored' summary is success with warnings"):
    // this is the pg_dump-18-into-Postgres-16 case: the data restores, one SET is skipped
    val out = Backup.restoreOutcome(1, skew)
    assert(out.isRight, out.toString)
    assertEquals(out.toOption.get.size, 2) // the error line and the summary; not the bare "Command was:"
    assert(out.toOption.get.exists(_.contains("transaction_timeout")), out.toString)

  test("restoreOutcome: exit 1 WITHOUT that summary is a failure, quoting the output"):
    val out = Backup.restoreOutcome(1, List("pg_restore: error: could not open input file: No such file"))
    assert(out.isLeft, out.toString)
    assert(out.left.exists(_.contains("could not open input file")), out.toString)
    assert(out.left.exists(_.contains("exit 1")), out.toString)

  test("restoreOutcome: a failure with no output at all still says something"):
    assert(Backup.restoreOutcome(2, Nil).left.exists(_.contains("exit 2")))

  test("restoreOutcome: warnings keep only the notable lines, trimmed"):
    val out = Backup.restoreOutcome(0, List("", "  pg_restore: warning: x  ", "chatter"))
    assertEquals(out, Right(List("pg_restore: warning: x")))

  // ── rendering ────────────────────────────────────────────────────────────────────────────────

  test("humanBytes uses decimal units"):
    assertEquals(Backup.humanBytes(0), "0 B")
    assertEquals(Backup.humanBytes(999), "999 B")
    assertEquals(Backup.humanBytes(1000), "1.0 kB")
    assertEquals(Backup.humanBytes(1500), "1.5 kB")
    assertEquals(Backup.humanBytes(2500000), "2.5 MB")
    assertEquals(Backup.humanBytes(3200000000L), "3.2 GB")

  test("Markdown.backup names the file, the size and the checksum"):
    val md = Markdown.backup(BackupResult("histdb-20260825T145112Z.dump", 2500000, "abc123", 812))
    assert(md.contains("histdb-20260825T145112Z.dump"), md)
    assert(md.contains("2.5 MB"), md)
    assert(md.contains("abc123"), md)
    assert(md.contains("812 ms"), md)

  test("Markdown.backups lists newest first and says so when empty"):
    assertEquals(Markdown.backups(Vector.empty), "_No backups on the server yet._")
    val md = Markdown.backups(Vector(
      BackupInfo("histdb-20260825T145112Z.dump", 1000, "2026-08-25T14:51:12Z"),
      BackupInfo("histdb-20260824T145112Z.dump", 2000, "2026-08-24T14:51:12Z")))
    assert(md.startsWith("### 2 backup(s)"), md)
    assert(md.indexOf("20260825") < md.indexOf("20260824"), md)

  test("Markdown.restored reports the schema version, the counts and any ignored errors"):
    val r  = RestoreResult("histdb-20260825T145112Z.dump", "4", 0, 137, 4200, List("pg_restore: warning: x"))
    val md = Markdown.restored(r)
    assert(md.contains("schema version:** 4"), md)
    assert(md.contains("0 → 137"), md)
    assert(md.contains("1 ignored error(s)"), md)
    assert(!Markdown.restored(r.copy(warnings = Nil)).contains("ignored error"), md)

  test("maintenance and missing carry the codes the endpoints map to 503 / 404"):
    assertEquals(Backup.maintenance("seed").code, Some(Backup.CodeMaintenance))
    assertEquals(Backup.missing("x.dump").code, Some(Write.CodeNotFound))
