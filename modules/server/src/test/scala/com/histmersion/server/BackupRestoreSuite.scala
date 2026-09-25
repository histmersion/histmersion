package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{Db, Migrate}
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}

/** The acceptance line of plan §4 wave 2.5: `backup` then `restore --force` reproduces `search`
  * results. Runs `pg_dump`/`pg_restore` for real, over the real HTTP endpoints, against a SCRATCH
  * database created next to `HISTMERSION_TEST_DB_URL` — a restore drops every table, so it must never run
  * against the database the other suites are using.
  *
  * Skipped, with a reason, when there is no test database or no PostgreSQL client tools. On macOS the
  * client tools come from homebrew's keg-only libpq: `brew install libpq` puts them in
  * `/opt/homebrew/opt/libpq/bin` (checked below), and the server finds them via
  * `HISTMERSION_PG_BIN_DIR=/opt/homebrew/opt/libpq/bin`. */
class BackupRestoreSuite extends munit.FunSuite:

  private val testDb = com.histmersion.db.TestDb.url

  /** "" = the binaries are on PATH (they are, inside the server image); otherwise the
    * directory holding them, from the same env var the server reads, then homebrew's libpq. */
  private val pgBinDir: Option[String] =
    def has(dir: String) = List("pg_dump", "pg_restore").forall(b => Files.isExecutable(Path.of(dir).resolve(b)))
    val onPath = Option(System.getenv("PATH")).toList
      .flatMap(_.split(java.io.File.pathSeparatorChar).toList)
      .find(d => d.nonEmpty && has(d))
    // an explicit HISTMERSION_PG_BIN_DIR wins over PATH, as in the server: a CI runner's PATH carries the
    // distro's pg_dump 16, which refuses to dump the PostgreSQL 18 the bundle ships
    sys.env.get("HISTMERSION_PG_BIN_DIR").filter(d => d.nonEmpty && has(d))
      .orElse(onPath.map(_ => ""))
      .orElse(List("/opt/homebrew/opt/libpq/bin").find(has))

  private val skipReason: Option[String] =
    if testDb.isEmpty then Some("HISTMERSION_TEST_DB_URL is not set")
    else if pgBinDir.isEmpty then
      Some("pg_dump/pg_restore not found on PATH, in $HISTMERSION_PG_BIN_DIR, or in /opt/homebrew/opt/libpq/bin " +
        "(macOS: brew install libpq)")
    else None

  override def munitIgnore: Boolean = skipReason.isDefined
  skipReason.foreach(r => println(s"[skipped] BackupRestoreSuite: $r"))

  // ── a scratch database, so a restore cannot touch the other suites' data ──────────────────────

  private lazy val base = AppConfig.load()

  /** `…/histmersion_w25` → (`…/postgres`, `histmersion_w25_backup_test`). */
  private lazy val (adminUrl, scratchDb, scratchUrl) =
    val url    = testDb.get.takeWhile(_ != '?')
    val prefix = url.take(url.lastIndexOf('/') + 1)
    val name   = url.drop(url.lastIndexOf('/') + 1) + "_backup_test"
    (prefix + "postgres", name, prefix + name)

  private def admin(sql: String): Unit =
    val c = Db.dataSource(base.db.copy(url = adminUrl)).getConnection
    try { val s = c.createStatement(); try { val _ = s.execute(sql) } finally s.close() }
    finally c.close()

  private lazy val cfg: AppConfig =
    base.copy(
      db = base.db.copy(url = scratchUrl),
      backups = base.backups.copy(dir = backupDir.toString, pgBinDir = pgBinDir.get))

  private lazy val backupDir: Path = Files.createTempDirectory("histmersion-backup-e2e")
  private lazy val ds              = Db.dataSource(cfg.db)

  override def beforeAll(): Unit =
    if skipReason.isEmpty then
      admin(s"DROP DATABASE IF EXISTS $scratchDb WITH (FORCE)")
      admin(s"CREATE DATABASE $scratchDb")
      val _ = Migrate.run(ds)

  override def afterAll(): Unit =
    if skipReason.isEmpty then
      scala.util.Try(admin(s"DROP DATABASE IF EXISTS $scratchDb WITH (FORCE)"))
      scala.util.Try {
        Files.list(backupDir).forEach(p => { val _ = Files.deleteIfExists(p) })
        val _ = Files.deleteIfExists(backupDir)
      }
      ()

  // ── HTTP helpers (no JSON dependency in the tests, as in SmokeSuite) ──────────────────────────

  private val http = HttpClient.newHttpClient()

  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def get(url: String): (Int, String)  = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String): (Int, String) =
    send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build())

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))
      .getOrElse(fail(s"no string field '$name' in $json"))

  private def number(json: String, name: String): Long =
    s""""$name"\\s*:\\s*(-?\\d+)""".r.findFirstMatchIn(json).map(_.group(1).toLong)
      .getOrElse(fail(s"no numeric field '$name' in $json"))

  private def sha256(bytes: Array[Byte]): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"$b%02x").mkString

  private def fixture(ck: String, title: String) =
    SourcePut(title = title, authors = List("Tester"), year = Some(2026), status = Some(SourceStatus.Current),
      topics = List("backup"), regions = List("Anatolia"), tags = List("zz-backup-test"),
      periodStart = Some(-1300), periodEnd = Some(-1100), summary = Some(s"fixture $ck"),
      findings = List(FindingInput(claim = s"$ck says something", region = Some("Anatolia"),
        lon = Some(32.0), lat = Some(39.0), periodStart = Some(-1250), periodEnd = Some(-1150))))

  private def sql(statement: String): Unit =
    val c = ds.getConnection
    try { val s = c.createStatement(); try { val _ = s.execute(statement) } finally s.close() }
    finally c.close()

  // ── the acceptance line ──────────────────────────────────────────────────────────────────────

  test("backup → download → drop a source → restore --force → search finds it again"):
    val h = Handlers(Deps(cfg, ds))
    assert(h.putSource("zz_backup_a", fixture("zz_backup_a", "The Kept One")).isRight)
    assert(h.putSource("zz_backup_b", fixture("zz_backup_b", "The Other One")).isRight)
    val before = h.sources(Filter(tags = List("zz-backup-test"), limit = 10)).total
    assertEquals(before, 2)

    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        // 1. POST /api/backup writes an archive and reports its size + checksum
        val (bs, first) = post(s"$url/api/backup", "")
        assertEquals(bs, 200, first)
        // the dump is a job: answered `done` inside the request wait, or `running` and then polled
        // (exactly what the CLI does) — a slow CI runner takes the second path
        val deadline = System.nanoTime() + 300_000_000_000L
        var bb = first
        while field(bb, "state") == "running" && System.nanoTime() < deadline do
          Thread.sleep(500)
          bb = get(s"$url/api/backup/status")._2
        assertEquals(field(bb, "state"), "done", bb)
        val file  = field(bb, "file")
        val bytes = number(bb, "bytes")
        val hash  = field(bb, "sha256")
        assert(Backup.safeName(file).isRight, file)
        assert(bytes > 0, bb)
        assertEquals(hash.length, 64, bb)
        assertEquals(Files.size(backupDir.resolve(file)), bytes)
        assert(Files.list(backupDir).count() == 1L, "the .part file should have been moved into place")

        // 2. GET /api/backups lists it
        val (ls, lb) = get(s"$url/api/backups")
        assertEquals(ls, 200, lb)
        assert(lb.contains(file), lb)

        // 3. GET /api/backups/{file} streams the very same bytes (this is `backup --download`)
        val dl = http.send(HttpRequest.newBuilder(URI.create(s"$url/api/backups/$file")).build(),
          HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(dl.statusCode, 200)
        assertEquals(dl.body.length.toLong, bytes)
        assertEquals(sha256(dl.body), hash)

        // 4. a name that is not in the listing — or tries to leave the directory — is refused
        assertEquals(get(s"$url/api/backups/histmersion-20200101T000000Z.dump")._1, 404)
        assert(get(s"$url/api/backups/..%2F..%2Fetc%2Fpasswd.dump")._1 != 200)
        assert(get(s"$url/api/backups/not-a-dump.txt")._1 != 200)

        // 5. restoring over a populated database needs `force`
        val (rs, rb) = post(s"$url/api/restore", s"""{"file":"$file","force":false}""")
        assertEquals(rs, 400, rb)
        assert(rb.contains("force"), rb)

        // 6. lose a source
        sql("DELETE FROM sources WHERE citekey = 'zz_backup_a'")
        assert(h.getSource("zz_backup_a").isLeft)
        assertEquals(h.sources(Filter(tags = List("zz-backup-test"), limit = 10)).total, 1)

        // 7. restore it
        val (fs, fb) = post(s"$url/api/restore", s"""{"file":"$file","force":true}""")
        assertEquals(fs, 200, fb)
        assertEquals(number(fb, "sourcesBefore"), 1L)
        assertEquals(number(fb, "sourcesAfter"), 2L)
        assertEquals(field(fb, "schemaVersion"), Migrate.current(ds).getOrElse("?"))

        // 8. …and search finds it again, findings and all
        val after = h.sources(Filter(tags = List("zz-backup-test"), limit = 10))
        assertEquals(after.total, 2)
        assertEquals(after.hits.map(_.source.citekey).sorted, List("zz_backup_a", "zz_backup_b"))
        val got = h.getSource("zz_backup_a").toOption.get
        assertEquals(got.source.title, "The Kept One")
        assertEquals(got.findings.map(_.claim), List("zz_backup_a says something"))
        assertEquals(got.source.periodStart, Some(-1300L))

        // 9. the maintenance lock was released — writes work again
        assert(h.putSource("zz_backup_c", fixture("zz_backup_c", "After The Restore")).isRight)
      finally b.stop()
    }

  private def takeBackup(h: Handlers) =
    val started = h.backup().toOption.getOrElse(fail("backup failed"))
    val deadline = System.nanoTime() + 300_000_000_000L
    var st = started
    while st.stateOf.contains(BackupState.Running) && System.nanoTime() < deadline do
      Thread.sleep(500)
      st = h.backupStatus.toOption.getOrElse(st)
    st.result.getOrElse(fail(s"backup did not finish: $st"))

  private def scalar(d: javax.sql.DataSource, q: String): String =
    val c = d.getConnection
    try
      val rs = c.createStatement().executeQuery(q)
      try { assert(rs.next(), q); rs.getString(1) } finally rs.close()
    finally c.close()

  // A dump from another installation, whose roles are named differently, must still come back.
  test("a dump owned by and granted to roles this cluster no longer has restores cleanly"):
    val h = Handlers(Deps(cfg, ds))
    assert(h.putSource("zz_backup_roles", fixture("zz_backup_roles", "Under Old Names")).isRight)
    admin("DROP ROLE IF EXISTS zz_gone_owner"); admin("DROP ROLE IF EXISTS zz_gone_reader")
    admin("CREATE ROLE zz_gone_owner"); admin("CREATE ROLE zz_gone_reader")
    sql("ALTER TABLE articles OWNER TO zz_gone_owner")
    sql("GRANT SELECT ON sources TO zz_gone_reader")
    val dump = takeBackup(h)
    // the cluster the dump is restored into never heard of either role
    sql("REASSIGN OWNED BY zz_gone_owner TO CURRENT_USER")
    sql("DROP OWNED BY zz_gone_owner"); sql("DROP OWNED BY zz_gone_reader")
    admin("DROP ROLE zz_gone_owner"); admin("DROP ROLE zz_gone_reader")

    val r = h.restore(RestoreRequest(dump.file, force = true))
    assert(r.isRight, r.toString)
    assert(!r.toOption.get.warnings.exists(_.contains("does not exist")), r.toString)
    assertEquals(scalar(ds, "SELECT tableowner FROM pg_tables WHERE tablename = 'articles'"),
      scalar(ds, "SELECT current_user"))
    // the read-only role was re-granted after the restore (the dump's GRANTs are skipped)
    val q = Db.queryDataSource(cfg.db, cfg.query)
    assert(scalar(q, "SELECT count(*) FROM sources WHERE citekey = 'zz_backup_roles'") == "1")

  test("an empty database restores WITHOUT force"):
    val h = Handlers(Deps(cfg, ds))
    val dump = takeBackup(h)
    sql("TRUNCATE sources CASCADE")
    assertEquals(h.sources(Filter(limit = 10)).total, 0)
    val r = h.restore(RestoreRequest(dump.file, force = false))
    assert(r.isRight, r.toString)
    assert(r.toOption.get.sourcesAfter > 0, r.toString)
