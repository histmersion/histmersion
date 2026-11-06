package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import java.time.Instant
import java.util.Locale
import com.histmersion.api.backup.{Backup, RestoreResult}

/** Pure half of backup/restore (plan §1.10): the file naming, the path-traversal gate and the JDBC-URL → `pg_dump`
  * argument translation. No database, no `pg_dump`, runs in CI in milliseconds.
  */
class BackupSuite extends munit.FunSuite:

  // ── the timestamp format ─────────────────────────────────────────────────────────────────────

  test("file name is histmersion-<UTC compact timestamp>.dump"):
    val at = Instant.parse("2026-08-25T14:51:12Z")
    assertEquals(Backup.timestamp(at), "20260825T145112Z")
    assertEquals(Backup.fileName(at), "histmersion-20260825T145112Z.dump")

  test("the timestamp is UTC and second-resolution, whatever the instant carries"):
    // a non-UTC-looking instant with sub-second precision still renders as whole UTC seconds
    assertEquals(Backup.fileName(Instant.parse("2026-01-02T03:04:05.987654Z")), "histmersion-20260102T030405Z.dump")

  test("names sort chronologically as plain text (that is why the listing can sort by name)"):
    val a = Backup.fileName(Instant.parse("2026-08-25T09:00:00Z"))
    val b = Backup.fileName(Instant.parse("2026-08-25T10:00:00Z"))
    val c = Backup.fileName(Instant.parse("2026-09-01T00:00:00Z"))
    assertEquals(List(c, a, b).sortBy(_.value), List(a, b, c))

  // #772: the listing sorted by NAME, so `keep-before-…` was "the newest backup" for ever
  test("backups list newest first by when they were taken, not by name"):
    val mtime = Instant.parse("2026-10-02T12:00:00Z")
    val keep = Backup.Result.File("keep-before-schema-024-20260925T085045Z.dump")
    assertEquals(Backup.takenAt(keep, mtime), Instant.parse("2026-09-25T08:50:45Z"))
    assertEquals(Backup.takenAt(com.histmersion.api.backup.Backup.Result.File("my-own.dump"), mtime), mtime) // no stamp in the name: its mtime
    def info(n: Backup.Result.File, at: Backup.Info.CreatedAt) = Backup.Info(n, com.histmersion.api.ByteCount(1), at)
    val rows = List(
      info(keep, Backup.Info.CreatedAt("2026-09-25T08:50:45Z")),
      info(Backup.Result.File("histmersion-20261001T080000Z.dump"), Backup.Info.CreatedAt("2026-10-01T08:00:00Z")),
      info(Backup.Result.File("histmersion-20261002T090000Z.dump"), Backup.Info.CreatedAt("2026-10-02T09:00:00Z")),
      info(Backup.Result.File("histmersion-20261002T090000Z-2.dump"), Backup.Info.CreatedAt("2026-10-02T09:00:00Z")),
      info(Backup.Result.File("my-own.dump"), Backup.Info.CreatedAt("2026-09-30T00:00:00Z"))
    )
    assertEquals(
      Backup.newestFirst(rows).map(_.file),
      List(
        "histmersion-20261002T090000Z-2.dump",
        "histmersion-20261002T090000Z.dump",
        "histmersion-20261001T080000Z.dump",
        "my-own.dump",
        keep
      )
    )

  test("a generated name passes the validator (round-trip)"):
    assertEquals(Backup.safeName(Backup.Name(Backup.fileName(Instant.now()).value)).map(_ => "ok"), Right("ok"))

  test("uniqueName only kicks in on a collision, and its result is still a safe name"):
    val at = Instant.parse("2026-08-25T14:51:12Z")
    val name = Backup.fileName(at)
    val asName = Backup.Name(name.value)
    assertEquals(Backup.uniqueName(asName, _ => false), name)
    assertEquals(Backup.uniqueName(asName, Set(name)), "histmersion-20260825T145112Z-2.dump")
    assertEquals(
      Backup.uniqueName(asName, Set(name, Backup.Result.File("histmersion-20260825T145112Z-2.dump"))),
      "histmersion-20260825T145112Z-3.dump"
    )
    assert(Backup.safeName(Backup.Name(Backup.uniqueName(asName, Set(name)).value)).isRight)

  // ── the path-traversal gate ──────────────────────────────────────────────────────────────────

  test("safeName accepts a plain dump file name"):
    assertEquals(Backup.safeName(com.histmersion.api.backup.Backup.Name("histmersion-20260825T145112Z.dump")), Right("histmersion-20260825T145112Z.dump"))
    assertEquals(Backup.safeName(com.histmersion.api.backup.Backup.Name("  histmersion-20260825T145112Z.dump  ")), Right("histmersion-20260825T145112Z.dump"))
    assertEquals(Backup.safeName(com.histmersion.api.backup.Backup.Name("my_copy-2.dump")), Right("my_copy-2.dump"))

  test("safeName rejects every path component"):
    val bad = List(
      "../../etc/passwd.dump", // traversal
      "..", // traversal, no extension
      "../histmersion.dump",
      "sub/histmersion.dump", // separator
      "sub\\histmersion.dump", // windows separator
      "/absolute/histmersion.dump",
      "/etc/passwd",
      ".hidden.dump", // must start alphanumeric, so no dotfiles
      "histmersion .dump", // space
      "histmersion\u0000.dump", // NUL byte
      "histmersion-$(whoami).dump", // shell metacharacters (it reaches a command line)
      "histmersion-`id`.dump",
      "histmersion;rm -rf.dump"
    )
    bad.foreach(n => assert(Backup.safeName(Backup.Name(n)).isLeft, s"should have been rejected: '$n'"))

  test("safeName rejects a missing name, a wrong extension and an over-long name"):
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name("")).isLeft)
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name("   ")).isLeft)
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name("histmersion.sql")).isLeft)
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name(".dump")).isLeft) // extension only
    assert(Backup.safeName(Backup.Name("a" * 200 + ".dump")).isLeft)

  test("safeName's message says what is wrong"):
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name("../x.dump")).left.exists(_.value.contains("no directories")))
    assert(Backup.safeName(com.histmersion.api.backup.Backup.Name("x.sql")).left.exists(_.value.contains(".dump")))

  // ── JDBC URL → pg_dump arguments ─────────────────────────────────────────────────────────────

  test("parseJdbc: host, port and database"):
    val t = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://db.internal:6000/histmersion")).toOption.get
    assertEquals(t.host, Some("db.internal"))
    assertEquals(t.port, Some(6000))
    assertEquals(t.database, "histmersion")
    assertEquals(t.args, List("-h", "db.internal", "-p", "6000", "-d", "histmersion", "-w"))

  test("parseJdbc: query parameters are not part of the database name"):
    val t = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://h:6000/histmersion?ssl=true&ApplicationName=x")).toOption.get
    assertEquals(t.database, "histmersion")

  test("parseJdbc: a missing port is left to libpq (no -p, and no port literal in the code)"):
    val t = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://h/histmersion")).toOption.get
    assertEquals(t.port, None)
    assertEquals(t.args, List("-h", "h", "-d", "histmersion", "-w"))

  test("parseJdbc: the host-less form"):
    val t = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql:histmersion")).toOption.get
    assertEquals(t.host, None)
    assertEquals(t.port, None)
    assertEquals(t.args, List("-d", "histmersion", "-w"))

  test("parseJdbc: bracketed IPv6, with and without a port"):
    val a = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://[::1]:6000/histmersion")).toOption.get
    assertEquals((a.host, a.port), (Some("[::1]"), Some(6000)))
    val b = Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://[::1]/histmersion")).toOption.get
    assertEquals((b.host, b.port), (Some("[::1]"), None))

  test("parseJdbc: rejects a non-postgres URL, a missing database and a non-numeric port"):
    assert(Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:mysql://h:3306/histmersion")).isLeft)
    assert(Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://h:6000/")).isLeft)
    assert(Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://h:6000")).isLeft)
    assert(Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("jdbc:postgresql://h:not-a-port/histmersion")).isLeft)
    assert(Backup.parseJdbc(com.histmersion.api.backup.Backup.JdbcUrl("")).isLeft)

  test("the configured default URL parses (reference.conf and the parser agree)"):
    assert(Backup.parseJdbc(Backup.JdbcUrl(AppConfig.load().db.url.value)).isRight)

  // ── how pg_restore's exit code is read ───────────────────────────────────────────────────────

  private val skew = List(
    Backup.ToolOutput("""pg_restore: error: could not execute query: ERROR:  unrecognized configuration parameter "transaction_timeout""""),
    Backup.ToolOutput("Command was: SET transaction_timeout = 0;"),
    Backup.ToolOutput("pg_restore: warning: errors ignored on restore: 1")
  )

  test("restoreOutcome: a clean run has no warnings"):
    assertEquals(Backup.restoreOutcome(com.histmersion.api.backup.Backup.ExitCode(0), Nil), Right(Nil))

  test("restoreOutcome: exit 1 WITH the 'errors ignored' summary is success with warnings"):
    // this is the pg_dump-18-into-Postgres-16 case: the data restores, one SET is skipped
    val out = Backup.restoreOutcome(com.histmersion.api.backup.Backup.ExitCode(1), skew)
    assert(out.isRight, out.toString)
    assertEquals(out.toOption.get.size, 2) // the error line and the summary; not the bare "Command was:"
    assert(out.toOption.get.exists(_.value.contains("transaction_timeout")), out.toString)

  test("restoreOutcome: exit 1 WITHOUT that summary is a failure, quoting the output"):
    val out = Backup.restoreOutcome(com.histmersion.api.backup.Backup.ExitCode(1), List(com.histmersion.api.backup.Backup.ToolOutput("pg_restore: error: could not open input file: No such file")))
    assert(out.isLeft, out.toString)
    assert(out.left.exists(_.value.contains("could not open input file")), out.toString)
    assert(out.left.exists(_.value.contains("exit 1")), out.toString)

  test("restoreOutcome: a failure with no output at all still says something"):
    assert(Backup.restoreOutcome(com.histmersion.api.backup.Backup.ExitCode(2), Nil).left.exists(_.value.contains("exit 2")))

  test("restoreOutcome: warnings keep only the notable lines, trimmed"):
    val out = Backup.restoreOutcome(com.histmersion.api.backup.Backup.ExitCode(0), List(com.histmersion.api.backup.Backup.ToolOutput(""), com.histmersion.api.backup.Backup.ToolOutput("  pg_restore: warning: x  "), com.histmersion.api.backup.Backup.ToolOutput("chatter")))
    assertEquals(out, Right(List("pg_restore: warning: x")))

  // ── rendering ────────────────────────────────────────────────────────────────────────────────

  test("humanBytes uses decimal units"):
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(0)), "0 B")
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(999)), "999 B")
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(1000)), "1.0 kB")
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(1500)), "1.5 kB")
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(2500000)), "2.5 MB")
    assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(3200000000L)), "3.2 GB")

  // The size lands in a Markdown API response, so it must read the same on every machine. JDK 25's
  // CLDR gives `en_PL` a decimal COMMA, which silently turned "2.5 MB" into "2,5 MB" on the JDK bump.
  test("humanBytes does not follow the default locale (it is wire data, not UI)"):
    val saved = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("pl-PL"))
      assertEquals(Backup.humanBytes(com.histmersion.api.ByteCount(2500000)), "2.5 MB")
    finally Locale.setDefault(saved)

  test("Markdown.backup names the file, the size and the checksum"):
    val md = Markdown.backup(Backup.Result(com.histmersion.api.backup.Backup.Result.File("histmersion-20260825T145112Z.dump"), com.histmersion.api.ByteCount(2500000), com.histmersion.api.Sha256("abc123"), com.histmersion.api.backup.Backup.Result.DurationMs(812)))
    assert(md.contains("histmersion-20260825T145112Z.dump"), md)
    assert(md.contains("2.5 MB"), md)
    assert(md.contains("abc123"), md)
    assert(md.contains("812 ms"), md)

  test("Markdown.backups lists newest first and says so when empty"):
    assertEquals(Markdown.backups(Vector.empty), "_No backups on the server yet._")
    val md = Markdown.backups(
      Vector(
        Backup.Info(com.histmersion.api.backup.Backup.Info.File("histmersion-20260825T145112Z.dump"), com.histmersion.api.ByteCount(1000), com.histmersion.api.backup.Backup.Info.CreatedAt("2026-08-25T14:51:12Z")),
        Backup.Info(com.histmersion.api.backup.Backup.Info.File("histmersion-20260824T145112Z.dump"), com.histmersion.api.ByteCount(2000), com.histmersion.api.backup.Backup.Info.CreatedAt("2026-08-24T14:51:12Z"))
      )
    )
    assert(md.startsWith("### 2 backup(s)"), md)
    assert(md.indexOf("20260825") < md.indexOf("20260824"), md)

  test("Markdown.restored reports the schema version, the counts and any ignored errors"):
    val r = RestoreResult(com.histmersion.api.backup.RestoreResult.File("histmersion-20260825T145112Z.dump"), com.histmersion.api.backup.RestoreResult.SchemaVersion("4"), com.histmersion.api.backup.RestoreResult.SourcesBefore(0), com.histmersion.api.backup.RestoreResult.SourcesAfter(137), com.histmersion.api.backup.RestoreResult.DurationMs(4200), List(com.histmersion.api.backup.RestoreResult.Warnings("pg_restore: warning: x")))
    val md = Markdown.restored(r)
    assert(md.contains("schema version:** 4"), md)
    assert(md.contains("0 → 137"), md)
    assert(md.contains("1 ignored error(s)"), md)
    assert(!Markdown.restored(r.modify(_.warnings).setTo(Nil)).contains("ignored error"), md)

  test("toPrune: the oldest beyond `keep`, ordered by stamp then same-second suffix, ours only"):
    val names = List(
      Backup.Result.File("histmersion-20260103T000000Z-2.dump"),
      Backup.Result.File("histmersion-20260101T000000Z.dump"),
      Backup.Result.File("notes.dump"),
      Backup.Result.File("histmersion-20260103T000000Z.dump"),
      Backup.Result.File("histmersion-20260102T000000Z.dump")
    )
    assertEquals(
      Backup.toPrune(names, com.histmersion.api.backup.Backup.Retention(2)),
      List("histmersion-20260101T000000Z.dump", "histmersion-20260102T000000Z.dump")
    )
    // the -2 file is the NEWER of the same-second pair, although it sorts first as text
    assertEquals(
      Backup.toPrune(names, com.histmersion.api.backup.Backup.Retention(1)),
      List(
        "histmersion-20260101T000000Z.dump",
        "histmersion-20260102T000000Z.dump",
        "histmersion-20260103T000000Z.dump"
      )
    )
    assertEquals(Backup.toPrune(names, com.histmersion.api.backup.Backup.Retention(0)), Nil)
    assertEquals(Backup.toPrune(names, com.histmersion.api.backup.Backup.Retention(10)), Nil)

  test("the default keeps five archives"):
    assertEquals(AppConfig.load().backups.keep, 5)

  test("maintenance and missing carry the codes the endpoints map to 503 / 404"):
    assertEquals(Backup.maintenance("seed").code, Some(Backup.CodeMaintenance))
    assertEquals(Backup.missing(com.histmersion.api.backup.Backup.Result.File("x.dump")).code, Some(Write.CodeNotFound))
