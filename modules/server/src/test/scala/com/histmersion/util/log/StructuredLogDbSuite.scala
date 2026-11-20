package com.histmersion
package util.log

import com.histmersion.testsupport.ComparedByValue.given
import com.augustnagro.magnum.*
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.api.log.LogEvent.What
import com.histmersion.config.{AppConfig, DbConfig, LogConfig}
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.server.Deps
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.database.{Migrate, Transactor, WritePermit}
import hearth.kindlings.optics.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.{Clock, Instant}
import scala.jdk.CollectionConverters.*

/** The structured log on a migrated database: every table's writes recorded by the triggers (one row per statement,
  * with the connection's trace and label), every event in the table AND the file under one id, the retention on both,
  * the file read when the database cannot answer, and the log kept out of backups. Gated on HISTMERSION_TEST_DB_URL.
  */
class StructuredLogDbSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val base = AppConfig.load()
  private lazy val ds = testPool(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private lazy val migrated = Migrate.run(ds)

  private def settings(dir: Path): LogConfig =
    base.log.modify(_.dir).setTo(LogConfig.Dir(dir.toString))

  private def tempDir(): Path = Files.createTempDirectory("hm-structured-log")

  test("every table but the log's own, the text chunks and the caches has its four write triggers; those have none"):
    val _ = migrated
    val (tables, triggers) = connect(ds) {
      val tables = sql"""SELECT table_name::text FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'""".query[String].run().toList
      val triggers = sql"""SELECT c.relname::text, t.tgname::text FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                          WHERE n.nspname = 'public' AND NOT t.tgisinternal
                            AND t.tgname ~ '_log_(insert|update|delete|truncate)$$'"""
        .query[(String, String)]
        .run()
        .toList
      (tables, triggers)
    }
    val unrecorded: Set[String] = LogData.unrecorded.map(t => s"$t").toSet
    val ops = List("insert", "update", "delete", "truncate")
    val missing = for
      t <- tables.filterNot(unrecorded.contains)
      op <- ops
      if !triggers.contains((t, s"${t}_log_$op"))
    yield s"${t}_log_$op"
    assertEquals(missing, Nil, "tables whose writes the database would not record")
    assertEquals(triggers.filter((t, _) => unrecorded.contains(t)), Nil, "triggers on tables left out")

  test(
    "a write statement is recorded once with the connection's trace and label; the writer moves it to table and file"
  ):
    val _ = migrated
    val dir = tempDir()
    val writer =
      LogWriter(() => settings(dir), LogFiles(() => Some(dir)), Transactor(ds), WritePermit.Gate(), Clock.systemUTC())
    writer.flush() // whatever earlier suites left staged
    val trace = writer.mintTrace()
    val traced = TracedDataSource(ds)
    val scope = TraceScope(trace, Some(LogEvent.Label("suite-label")), LogEvent.Actor.Client, writer)
    Tracing.within(scope) {
      Transactor(traced).transact {
        sql"""INSERT INTO jobs (id, kind, status, citekey) VALUES ('log-suite-1', 'ocr', 'done', 'logsuite2026'),
              ('log-suite-2', 'ocr', 'done', NULL)""".update.run(): Unit
        sql"UPDATE jobs SET status = 'failed' WHERE id = 'no-such-job'".update.run(): Unit
      }
    }
    val jobTrace = connect(ds)(sql"SELECT trace_id::text FROM jobs WHERE id = 'log-suite-1'".query[String].run().head)
    assertEquals(jobTrace, trace.value, "the job keeps the trace it was created under")
    writer.flush()
    val page = LogService(Transactor(ds), LogFiles(() => Some(dir)))
      .page(LogQuery.ofTrace(trace, 100))
    val written = page.events.collect { case e @ LogEvent(_, _, _, _, _, _, w: What.Written) => (e, w) }
    assertEquals(
      written.map(_._2.table),
      Vector("jobs"),
      "one event for the insert; none for the update that changed nothing"
    )
    val (event, w) = written.head
    assertEquals(w.rows, Some(2L))
    assertEquals(w.keys.map(k => s"$k").toSet, Set("log-suite-1", "log-suite-2"))
    assertEquals(event.label, Some("suite-label"))
    assert(event.subjects.map(_.ref).contains("source:logsuite2026"), event.subjects.toString)
    assert(event.subjects.map(_.ref).contains("job:log-suite-1"), event.subjects.toString)
    // the same event, under the same id, in the file
    val lines = Files.list(dir).iterator().asScala.toList.flatMap(f => Files.readAllLines(f, UTF_8).asScala)
    assert(lines.exists(_.contains(event.id.value)), s"the file holds ${lines.size} line(s), none with ${event.id}")
    val fromFile = LogService(Transactor(ds), LogFiles(() => Some(dir)))
      .page(
        LogQuery
          .parse(LogPage.Filter(trace = Some(trace), from = Some(LogPage.From("file"))), Instant.now())
          .toOption
          .get
      )
    assertEquals(fromFile.origin, LogPage.Origin.File)
    assertEquals(fromFile.events.map(_.id), page.events.map(_.id))
    connect(ds)(sql"DELETE FROM jobs WHERE id LIKE 'log-suite-%'".update.run(): Unit)

  test("an event recorded by the server reaches both stores; while the database is down the file answers alone"):
    val _ = migrated
    val dir = tempDir()
    val writer =
      LogWriter(() => settings(dir), LogFiles(() => Some(dir)), Transactor(ds), WritePermit.Gate(), Clock.systemUTC())
    val trace = writer.mintTrace()
    Tracing.within(TraceScope(trace, None, LogEvent.Actor.Worker, writer)) {
      Tracing.record(
        What.ProblemRaised(Some(LogEvent.ProblemId("cafe0001")), LogEvent.ProblemCase("Internal.Unexpected"))
      )
    }
    writer.flush()
    val inTable = connect(ds)(
      sql"SELECT id::text, problem FROM log_events WHERE trace = ${s"$trace"}::uuid".query[(String, Boolean)].run()
    )
    assertEquals(inTable.map(_._2), Vector(true))
    val down = new javax.sql.DataSource:
      def getConnection() = throw java.sql.SQLException("the database is down")
      def getConnection(u: String, p: String) = getConnection()
      def getLogWriter = null
      def setLogWriter(out: java.io.PrintWriter) = ()
      def setLoginTimeout(seconds: Int) = ()
      def getLoginTimeout = 0
      def getParentLogger = java.util.logging.Logger.getGlobal
      def unwrap[T](iface: Class[T]): T = throw java.sql.SQLException("no")
      def isWrapperFor(iface: Class[?]) = false
    val service = LogService(Transactor(down), LogFiles(() => Some(dir)))
    val page = service.page(LogQuery.ofTrace(trace, 10))
    assertEquals(page.origin, LogPage.Origin.File)
    assertEquals(page.events.map(e => s"${e.id}"), inTable.map(_._1))
    val around = service.problem(LogEvent.ProblemId("cafe0001"), LogQuery.Limit(10))
    assertEquals(around.map(_.events.map(_.trace)), Some(Vector(trace)), "the problem's trace, found in the file")
    // the writer itself, with the database down: the file still gets every event, the table catches up later
    val offline =
      LogWriter(() => settings(dir), LogFiles(() => Some(dir)), Transactor(down), WritePermit.Gate(), Clock.systemUTC())
    val later = offline.mintTrace()
    offline.record(
      TraceScope(later, None, LogEvent.Actor.Server, offline),
      Vector.empty,
      What.Lost(LogEvent.Count(1), LogEvent.Loss.QueueFull)
    )
    offline.flush()
    assertEquals(service.page(LogQuery.ofTrace(later, 10)).events.size, 1)

  test("retention: ordinary events past keep-days and problems past keep-problem-days leave the table"):
    val _ = migrated
    val dir = tempDir()
    val now = Instant.parse("2026-11-20T12:00:00Z")
    val trace = LogEvent.Trace(LogStamp.uuidV7(now.toEpochMilli, 1L, 1L))
    def at(days: Long) = LogEvent.At(LogStamp.iso(now.minusSeconds(days * 86400L)))
    def ev(n: Int, days: Long, what: What) =
      LogEvent(
        LogEvent.Id(LogStamp.uuidV7(now.toEpochMilli, n.toLong, n.toLong)),
        at(days),
        trace,
        None,
        LogEvent.Actor.Server,
        Vector.empty,
        what
      )
    val ok = What.RequestsCounted(LogEvent.Operation("GET /api/health"), LogEvent.Count(1), LogEvent.Count(0))
    val bad = What.ProblemRaised(None, LogEvent.ProblemCase("Io.Unreadable"))
    connect(ds)(LogRepo.insert(Vector(ev(1, 40, ok), ev(2, 40, bad), ev(3, 100, bad), ev(4, 1, ok))))
    LogRetention.run(settings(dir), LogFiles(() => Some(dir)), Transactor(ds), WritePermit.Gate(), now)
    val left = connect(ds)(
      sql"SELECT event::text FROM log_events WHERE trace = ${s"$trace"}::uuid"
        .query[String]
        .run()
        .flatMap(LogRepo.decode)
    )
    assertEquals(left.map(_.id).toSet, Set(ev(2, 40, bad).id, ev(4, 1, ok).id))

  test("backups leave the log's rows out; no bundle carries it"):
    assertEquals(
      BackupService.rowsLeftOut(Deps.data),
      List("--exclude-table-data=public.log_events", "--exclude-table-data=public.log_writes")
    )
    assertEquals(LogData.carried, Nil)

  test("no code but the log's repository writes the log's tables (the triggers' function aside)"):
    val allowed = Set(
      "modules/server/src/main/scala/com/histmersion/util/log/LogRepo.scala",
      "modules/server/src/main/scala/com/histmersion/util/log/LogData.scala"
    )
    val roots = Files.list(Path.of("modules")).iterator().asScala.toList.map(_.resolve("src/main"))
    val found = roots.filter(Files.isDirectory(_)).flatMap { r =>
      val s = Files.walk(r)
      try
        s.iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
          .filter(p => Files.readString(p).matches("(?s).*\\blog_(events|writes)\\b.*"))
          .map(_.toString)
          .toList
      finally s.close()
    }
    assertEquals(found.filterNot(allowed.contains).sorted, Nil)
    val migrations = Files
      .list(Path.of("modules/db/src/main/resources/db/migration"))
      .iterator()
      .asScala
      .filter(p => Files.readString(p).matches("(?s).*INSERT INTO log_(events|writes).*"))
      .map(_.getFileName.toString)
      .toList
    assertEquals(migrations, List("V111__structured_log.sql"), "only the write trigger's function inserts")
