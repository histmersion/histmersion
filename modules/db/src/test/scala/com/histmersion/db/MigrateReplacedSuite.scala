package com.histmersion.db

import com.histmersion.config.AppConfig

import java.nio.file.{Files, Path}
import javax.sql.DataSource

/** A migration whose text was replaced after it had run ([[Migrate.Replaced]]): a database whose
  * history still carries the checksum of the text it ran with starts, migrates and validates; a fresh
  * one records the new checksum; a dump of an old database validates once restored. In databases of
  * its own beside HISTMERSION_TEST_DB_URL's, dropped afterwards; skipped without it. */
class MigrateReplacedSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val admin            = testPool(cfg.db.copy(url = testDb.get))
  private lazy val dbs              = ScratchDatabases("histmersion_replaced", cfg.db.copy(url = testDb.get))
  private val created               = scala.collection.mutable.ListBuffer.empty[String]

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try { val st = c.createStatement(); try { val _ = st.execute(stmt) } finally st.close() }
    finally c.close()

  private def emptyDatabase(name: String): DataSource =
    exec(admin, s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
    exec(admin, s"CREATE DATABASE $name")
    created += name
    testPool(cfg.db.copy(url = dbs.urlOf(name)))

  private def checksums(ds: DataSource, version: String): List[Int] =
    val c = ds.getConnection
    try
      val ps = c.prepareStatement("SELECT checksum FROM flyway_schema_history WHERE version = ?")
      ps.setString(1, version)
      try
        val rs = ps.executeQuery()
        Iterator.continually(rs).takeWhile(_.next()).map(_.getInt(1)).toList
      finally ps.close()
    finally c.close()

  /** The history as a database migrated with the text each replaced migration ran with has it. */
  private def withShippedChecksums(ds: DataSource): Unit =
    Migrate.Replaced.foreach(m =>
      exec(ds, s"UPDATE flyway_schema_history SET checksum = ${m.ranWith} WHERE version = '${m.version}'"))

  override def afterSuite(): Unit =
    if testDb.isDefined then
      created.foreach(n => exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))
      dbs.dropAll()

  test("a fresh database migrates and records each replaced migration's new checksum"):
    val ds = emptyDatabase("histmersion_replaced_fresh_test")
    assertEquals(Migrate.run(ds), Migrate.migrationNames.last.drop(1).takeWhile(_ != '_'))
    Migrate.Replaced.foreach(m => assertEquals(checksums(ds, m.version), List(m.now), m.version))
    assertEquals(Migrate.acceptReplacedChecksums(ds), 0, "nothing to move")
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)

  test("a database migrated with the old text starts and validates, and only that row changes"):
    val ds = dbs("old")
    withShippedChecksums(ds)
    val others = "SELECT md5(string_agg(version || ':' || coalesce(checksum::text, '') || ':' || success, ',' ORDER BY installed_rank)) " +
      s"FROM flyway_schema_history WHERE version IS NULL OR version NOT IN (${Migrate.Replaced.map(m => s"'${m.version}'").mkString(", ")})"
    def othersDigest: String =
      val c = ds.getConnection
      try { val rs = c.createStatement().executeQuery(others); rs.next(); rs.getString(1) } finally c.close()
    val before = othersDigest
    assert(!Migrate.flyway(ds).validateWithResult().validationSuccessful, "Flyway alone refuses the old checksum")
    assertEquals(Migrate.run(ds), Migrate.migrationNames.last.drop(1).takeWhile(_ != '_'))
    Migrate.Replaced.foreach(m => assertEquals(checksums(ds, m.version), List(m.now), m.version))
    assertEquals(othersDigest, before, "no other history row is touched")
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)
    assertEquals(Migrate.acceptReplacedChecksums(ds), 0, "idempotent")

  /** The PostgreSQL client tools: the directory the server reads them from, else PATH. */
  private val pgBin: Option[String] =
    def has(dir: String) = List("pg_dump", "pg_restore").forall(b => Files.isExecutable(Path.of(dir).resolve(b)))
    sys.env.get("HISTMERSION_PG_BIN_DIR").filter(d => d.nonEmpty && has(d))
      .orElse(Option(System.getenv("PATH")).toList.flatMap(_.split(java.io.File.pathSeparatorChar)).find(d => d.nonEmpty && has(d)))

  private def run(cmd: String*): Unit =
    val pb = ProcessBuilder(cmd*).redirectErrorStream(true)
    pb.environment().put("PGPASSWORD", cfg.db.password)
    val p   = pb.start()
    val out = String(p.getInputStream.readAllBytes())
    assertEquals(p.waitFor(), 0, s"${cmd.mkString(" ")}\n$out")

  private def connArgs(name: String): List[String] =
    val u = java.net.URI.create(testDb.get.stripPrefix("jdbc:"))
    List("-h", u.getHost, "-p", u.getPort.toString, "-U", cfg.db.user, "-d", name)

  test("a dump of a database migrated with the old text validates once restored"):
    assume(pgBin.isDefined, "pg_dump/pg_restore not found in HISTMERSION_PG_BIN_DIR or on PATH")
    val bin  = Path.of(pgBin.get)
    val old  = dbs("dumped")
    withShippedChecksums(old)
    val dump = Files.createTempFile("histmersion-replaced", ".dump")
    try
      run((bin.resolve("pg_dump").toString :: "-Fc" :: "--no-owner" :: "--no-privileges" :: "-f" :: dump.toString ::
        connArgs("histmersion_replaced_dumped_test"))*)
      val restored = emptyDatabase("histmersion_replaced_restored_test")
      run((bin.resolve("pg_restore").toString :: "--no-owner" :: "--no-privileges" :: dump.toString ::
        connArgs("histmersion_replaced_restored_test"))*)
      Migrate.Replaced.foreach(m => assertEquals(checksums(restored, m.version), List(m.ranWith), "restored as dumped"))
      assert(Migrate.validate(restored).isRight, Migrate.validate(restored).toString)
      Migrate.Replaced.foreach(m => assertEquals(checksums(restored, m.version), List(m.now), m.version))
    finally Files.deleteIfExists(dump): Unit
