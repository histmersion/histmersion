package com.histmersion
package server
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.util.settings.ConfigSource

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{DbSuite, TestDb}
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import javax.sql.DataSource
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.config.DbConfig
import com.histmersion.util.storage.Blob
import com.histmersion.config.ServerConfig
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.Migrate

/** A config reload against a running server (docs/research-plugins.md §3.5, §3.6): the database swapped between two
  * test databases on the same Postgres, with a request and an OCR job that started on the old one finishing there and
  * new work going to the new one, the new one migrated, the old pool closed once drained; a broken config changing
  * nothing; a plugin block rebuilding that plugin only. Skipped without HISTMERSION_TEST_DB_URL.
  */
class HotReloadSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val DbA = "histmersion_hota_test"
  private val DbB = "histmersion_hotb_test"

  private lazy val base = AppConfig.load()
  private lazy val admin = testPool(base.db.modify(_.url).setTo(TestDb.dbUrl.get))
  private def urlOf(name: String) = testDb.get.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1")

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try {
      val st = c.createStatement();
      try { val _ = st.execute(stmt) }
      finally st.close()
    } finally c.close()

  private def scalar(ds: DataSource, q: String): Long =
    val c = ds.getConnection
    try
      val rs = c.createStatement().executeQuery(q)
      rs.next(); rs.getLong(1)
    finally c.close()

  /** Connections any client holds on database `name`, as the cluster sees them. */
  private def connectionsTo(name: String): Long =
    scalar(admin, s"SELECT count(*) FROM pg_stat_activity WHERE datname = '$name'")

  override def beforeAll(): Unit =
    if testDb.isDefined then
      List(DbA, DbB).foreach { n =>
        exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)")
        exec(admin, s"CREATE DATABASE $n")
      }

  override def afterSuite(): Unit =
    if testDb.isDefined then
      List(DbA, DbB, "histmersion_hotmissing_test").foreach(n =>
        exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)")
      )

  // ── the config file and the server ─────────────────────────────────────────────────────────────

  private val dir = Files.createTempDirectory("histmersion-hot")
  private val file = dir.resolve("histmersion.conf")
  private val spool = Files.createDirectories(dir.resolve("spool"))

  /** No environment: the test's settings come from the file alone (a HISTMERSION_DB_URL in the shell must not beat it).
    */
  private val env = ConfigFactory.empty

  private def write(db: String, extra: String = ""): Unit =
    val _ = Files.writeString(
      file,
      s"""histmersion {
         |  db.url = "${urlOf(db)}"
         |  converter.spool-dir = "$spool"
         |  config.drain-seconds = 10
         |  $extra
         |}
         |""".stripMargin
    )

  private def loaded: LoadedConfig =
    AppConfig.loadWithUserFile(env, Some(file)).fold(e => fail(e.sentences.mkString("; ")), identity)
  private val source =
    ConfigSource(() => AppConfig.loadWithUserFile(env, Some(file)))

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String = "") =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def upload(url: String, f: Path) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofFile(f))
        .build()
    )
  private def field(json: String, name: String): Option[String] =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))
  private def list(json: String, name: String): List[String] =
    s""""$name"\\s*:\\s*\\[([^\\]]*)\\]""".r
      .findFirstMatchIn(json)
      .toList
      .flatMap(m => """"([^"]*)"""".r.findAllMatchIn(m.group(1)).map(_.group(1)))

  /** Which database answers a query, asked through the server's read-only hatch. */
  private def database(url: String, sleep: Int = 0): String =
    val sql =
      if sleep > 0 then s"select current_database() as db from pg_sleep($sleep)" else "select current_database() as db"
    val (s, b) = post(s"$url/api/query", s"""{"sql":"$sql"}""")
    assertEquals(s, 200, b)
    List(DbA, DbB).find(b.contains).getOrElse(fail(s"no test database in $b"))

  private def await(url: String, id: String): String =
    def go(n: Int): String =
      val (_, b) = get(s"$url/api/jobs/$id")
      val st = field(b, "status").getOrElse("")
      if st == "done" || st == "failed" || n > 600 then b
      else
        Thread.sleep(100)
        go(n + 1)
    go(0)

  /** A converter that holds each conversion until the test lets it go. */
  final private class GatedConverter extends ConverterClient:
    val entered = java.util.concurrent.Semaphore(0)
    val gate = CountDownLatch(1)
    def health = Right(ConverterClient.HealthSummary("ok"))
    def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
      entered.release()
      gate.await(60, TimeUnit.SECONDS)
      Right(
        ConvertedDoc(
          markdown = ConvertedDoc.Markdown(s"# Converted\n\n$filename\n"),
          pages = ConvertedDoc.Pages(1),
          images = Nil,
          filename = Some(ConvertedDoc.Filename(filename.value))
        )
      )

  private def pdf(marker: String): Path =
    val p = Files.createTempFile(dir, "hot-", ".pdf")
    Files.write(p, s"%PDF-1.4 $marker".getBytes("UTF-8"))

  /** One query on database `db` over a connection of its own (no pool left behind on it). */
  private def on(db: String, q: String): Long =
    val c = java.sql.DriverManager.getConnection(urlOf(db), base.db.user.value, base.db.password.value)
    try
      val rs = c.createStatement().executeQuery(q)
      rs.next(); rs.getLong(1)
    finally c.close()

  private def sourceIn(db: String, citekey: String): Long =
    on(db, s"SELECT count(*) FROM sources WHERE citekey = '$citekey'")

  test("swap the database: in-flight work finishes on the old one, new work goes to the new one, the old pool closes"):
    write(DbA)
    val conv = GatedConverter()
    val l = loaded
    supervised {
      val b = Composition
        .server(l.config, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), conv, source = Some((l, source)))
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(database(url), DbA)

      // an OCR job queued on A, held in its conversion across the swap
      val (us, ub) = upload(s"$url/api/ingest?citekey=zz_hot_a&title=Hot+A&force=true", pdf("a"))
      assertEquals(us, 202, ub)
      val jobA = field(ub, "jobId").getOrElse(fail(ub))
      assert(conv.entered.tryAcquire(30, TimeUnit.SECONDS), "the job reached the converter")

      // a request in flight on A while the reload happens
      val inFlight = CompletableFuture.supplyAsync(() => database(url, sleep = 3))
      Thread.sleep(500)

      write(DbB)
      val reloadStart = System.nanoTime()
      val (rs, rb) = post(s"$url/api/config/reload")
      assertEquals(rs, 200, rb)
      assert(rb.contains("\"published\":true"), rb)
      assertEquals(list(rb, "applied"), List("histmersion.db.url"))
      // the instance lock is taken on the new database before it is migrated (one server per database)
      assertEquals(list(rb, "rebuilt"), List("pool", "instance-lock", "migrations", "query-pool", "leases"))
      assertEquals(list(rb, "errors"), Nil)
      // the reload waited for the in-flight request (bounded by drain-seconds) and then reported the
      // job's generation as still draining
      assert(list(rb, "draining").exists(_.startsWith("pool (generation 0")), rb)
      assert((System.nanoTime() - reloadStart) / 1000000L >= 1500L, "the reload waited for the request")

      // the request that started on A answered from A; a new one goes to B
      assertEquals(inFlight.get(30, TimeUnit.SECONDS), DbA)
      assertEquals(database(url), DbB)
      assert(
        on(DbB, "SELECT count(*) FROM flyway_schema_history WHERE success") == Migrate.migrationNames.size
      )

      // the held job finishes on A — its row and its source are there, not in B
      conv.gate.countDown()
      val doneOnA = s"SELECT count(*) FROM jobs WHERE id = '$jobA' AND status = 'done'"
      val deadline = System.nanoTime() + 30L * 1000000000L
      while on(DbA, doneOnA) == 0 && System.nanoTime() < deadline do Thread.sleep(100)
      assertEquals(on(DbA, doneOnA), 1L)
      assertEquals(get(s"$url/api/jobs/$jobA")._1, 404, "A's job is not in B")
      assertEquals(sourceIn(DbA, "zz_hot_a"), 1L)
      assertEquals(sourceIn(DbB, "zz_hot_a"), 0L)

      // the job pool keeps working: the next job runs on B
      val (us2, ub2) = upload(s"$url/api/ingest?citekey=zz_hot_b&title=Hot+B&force=true", pdf("b"))
      assertEquals(us2, 202, ub2)
      val doneB = await(url, field(ub2, "jobId").getOrElse(fail(ub2)))
      assertEquals(field(doneB, "status"), Some("done"), doneB)
      assertEquals(sourceIn(DbB, "zz_hot_b"), 1L)

      // the old generation drained: the server holds no connection to A any more
      val drained = System.nanoTime() + 30L * 1000000000L
      while connectionsTo(DbA) > 0 && System.nanoTime() < drained do Thread.sleep(100)
      assertEquals(connectionsTo(DbA), 0L, "the old pools are closed")
      b.stop()
    }

  test("a broken config changes nothing: a file that does not parse, a database that is not there"):
    write(DbB)
    val l = loaded
    supervised {
      val b = Composition
        .server(
          l.config,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          OfflineConverter(),
          source = Some((l, source))
        )
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(database(url), DbB)

      Files.writeString(file, "histmersion { db.url = ")
      val (s1, b1) = post(s"$url/api/config/reload")
      assertEquals(s1, 200, b1)
      assert(b1.contains("\"published\":false") && list(b1, "errors").nonEmpty, b1)
      assertEquals(database(url), DbB)

      write("histmersion_hotmissing_test")
      val (_, b2) = post(s"$url/api/config/reload")
      assert(b2.contains("\"published\":false"), b2)
      assert(list(b2, "errors").exists(e => e.startsWith("pool:") && e.contains("histmersion_hotmissing_test")), b2)
      assertEquals(database(url), DbB)

      // a type error in the file: refused before anything is built
      write(DbB, "limits.search = lots")
      val (_, b3) = post(s"$url/api/config/reload")
      assert(list(b3, "errors").exists(_.contains("limits.search")), b3)

      // nothing changed: nothing to do
      write(DbB)
      val (_, b4) = post(s"$url/api/config/reload")
      assert(b4.contains("\"published\":false") && list(b4, "errors").isEmpty && list(b4, "applied").isEmpty, b4)
      b.stop()
    }

  test("a plugin block rebuilds that plugin only; a hot key applies; a restart-only key is reported, not applied"):
    write(DbB)
    val l = loaded
    val gen = ServerPlugins.load(l, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
    supervised {
      val b =
        Composition
          .server(
            l.config,
            ServerConfig.Host("127.0.0.1"),
            ServerConfig.Port(0),
            OfflineConverter(),
            plugins = gen,
            source = Some((l, source))
          )
          .start()
      val url = s"http://127.0.0.1:${b.port}"
      write(DbB, "plugins.pmc.enabled = false\n  limits.search = 7\n  server.port = 1")
      val (s, r) = post(s"$url/api/config/reload")
      assertEquals(s, 200, r)
      assert(r.contains("\"published\":true"), r)
      assertEquals(list(r, "plugins"), List("pmc"))
      assertEquals(list(r, "rebuilt"), List("plugins", "acquisition"))
      assertEquals(list(r, "applied"), List("histmersion.limits.search", "histmersion.plugins.pmc.enabled"))
      assertEquals(list(r, "restartOnly"), List("histmersion.server.port"))
      // the plugins as the server reports them now
      val (_, about) = get(s"$url/api/about")
      assert(about.matches("(?s).*\"id\"\\s*:\\s*\"pmc\".*?\"state\"\\s*:\\s*\"disabled\".*"), about.take(2000))
      // the same reload over MCP's surface is a no-op now (nothing differs)
      val (_, again) = post(s"$url/api/config/reload")
      assert(
        again.contains("\"published\":false") && list(again, "restartOnly") == List("histmersion.server.port"),
        again
      )
      b.stop()
    }

  test("an AI provider switched off by a reload is out of every chain at once; switched on again, it is back"):
    write(DbB, "agents.roles.extractor = \"local:zzhot\"\n  agents.prices = \"zzhot=0/0\"")
    val l = loaded
    val gen = ServerPlugins.load(l, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
    supervised {
      val b =
        Composition
          .server(
            l.config,
            ServerConfig.Host("127.0.0.1"),
            ServerConfig.Port(0),
            OfflineConverter(),
            plugins = gen,
            source = Some((l, source))
          )
          .start()
      val url = s"http://127.0.0.1:${b.port}"
      val Off = "switched off (histmersion.plugins.ai-local.enabled = false)"
      val (s0, m0) = get(s"$url/api/agents/models")
      assertEquals(s0, 200, m0)
      assert(m0.contains("local:zzhot") && !m0.contains(Off), m0.take(2000))
      write(
        DbB,
        "agents.roles.extractor = \"local:zzhot\"\n  agents.prices = \"zzhot=0/0\"\n  plugins.ai-local.enabled = false"
      )
      val (_, r) = post(s"$url/api/config/reload")
      assert(r.contains("\"published\":true"), r)
      assertEquals(list(r, "plugins"), List("ai-local"), "the one plugin whose block changed")
      val (_, m1) = get(s"$url/api/agents/models")
      assert(m1.contains(Off), m1.take(2000))
      write(DbB, "agents.roles.extractor = \"local:zzhot\"\n  agents.prices = \"zzhot=0/0\"")
      val (_, r2) = post(s"$url/api/config/reload")
      assertEquals(list(r2, "plugins"), List("ai-local"))
      val (_, m2) = get(s"$url/api/agents/models")
      assert(!m2.contains(Off), m2.take(2000))
      b.stop()
    }
