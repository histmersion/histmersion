package com.histmersion
package server

import com.histmersion.util.settings.ConfigSource

import com.histmersion.config.*
import com.histmersion.db.{DbSuite, TestDb}
import org.ekrich.config.ConfigFactory
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files

/** Settings over HTTP against a running server and a settings file of its own: `GET /api/settings`, a change `POST`ed,
  * written into the file with its comments and reloaded into the server; a refused change leaving the file as it was;
  * the first-run setup due on `/api/config` until it is marked done; the restart refused where no service would start
  * the server again. Skipped without HISTMERSION_TEST_DB_URL.
  */
class SettingsHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val dir = Files.createTempDirectory("histmersion-settings-http")
  private val file = dir.resolve("histmersion.conf")
  private val spool = Files.createDirectories(dir.resolve("spool"))
  private val env = ConfigFactory.empty

  private def fileText(extra: String = "") =
    s"""# the test's settings — this line stays
       |histmersion {
       |  db.url = "${testDb.getOrElse("")}"
       |  converter.spool-dir = "$spool"   # where uploads wait
       |  $extra
       |}
       |""".stripMargin

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private def withServer(text: String, env: org.ekrich.config.Config = env)(run: String => Unit): Unit =
    Files.writeString(file, text)
    val l = AppConfig.loadWithUserFile(env, Some(file)).fold(e => fail(e.value), identity)
    supervised {
      val b = Composition
        .server(
          l.config,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          OfflineConverter(),
          source = Some((l, ConfigSource(() => AppConfig.loadWithUserFile(env, Some(file)).left.map(e => ConfigSource.Problem(e.value)))))
        )
        .start()
      try run(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  test("a change is written into the settings file, comments kept, and the running server takes it on"):
    withServer(fileText("limits.search = 5")) { url =>
      val (s0, page) = get(s"$url/api/settings?prefix=limits")
      assertEquals(s0, 200, page)
      assert(page.contains("\"key\":\"histmersion.limits.search\"") && page.contains("\"value\":\"5\""), page.take(600))
      val version =
        """"version":"([0-9a-f]{64})"""".r.findFirstMatchIn(page).map(_.group(1)).getOrElse(fail(page.take(300)))
      val (s1, w) =
        post(s"$url/api/settings", s"""{"changes":[{"key":"limits.search","value":"7"}],"version":"$version"}""")
      assertEquals(s1, 200, w)
      assert(w.contains("\"changed\":[\"histmersion.limits.search\"]") && w.contains("\"published\":true"), w)
      val text = Files.readString(file)
      assert(
        text.startsWith("# the test's settings — this line stays\n") && text.contains("# where uploads wait"),
        text
      )
      assert(text.contains("limits.search = 7"), text)
      assert(Files.readString(dir.resolve("histmersion.conf.bak")).contains("limits.search = 5"))
      val (_, after) = get(s"$url/api/settings?prefix=limits.search")
      assert(after.contains("\"effective\":\"7\""), after.take(600))
      // the old version is gone: a change made against it is refused, not lost over the new text
      val (s2, stale) =
        post(s"$url/api/settings", s"""{"changes":[{"key":"limits.search","value":"9"}],"version":"$version"}""")
      assertEquals(s2, 409, stale)
    }

  test("a refused change leaves the file as it was and says why"):
    withServer(fileText()) { url =>
      val before = Files.readString(file)
      val (s, b) = post(s"$url/api/settings", """{"changes":[{"key":"backups.keep","value":"lots"}]}""")
      assertEquals(s, 400, b)
      assert(b.contains("backups.keep") && b.contains("settings_refused"), b)
      val (s2, b2) = post(s"$url/api/settings", """{"changes":[{"key":"auth.api-key","value":"s3cret"}]}""")
      assertEquals(s2, 400, b2)
      assert(b2.contains("HISTMERSION_API_KEY") && !b2.contains("s3cret"), b2)
      assertEquals(Files.readString(file), before)
    }

  test("the first-run setup is due for a file that sets nothing of its own, until it is marked done"):
    // the database URL comes from the environment layer here: the file itself is only comments
    val fromEnv = ConfigFactory.parseMap(
      java.util.Map.of("HISTMERSION_DB_URL", testDb.getOrElse(""), "HISTMERSION_SPOOL_DIR", spool.toString)
    )
    withServer("# nothing set yet\n", fromEnv) { url =>
      val (_, c0) = get(s"$url/api/config")
      assert(c0.contains("\"setupDue\":true"), c0.takeRight(300))
      assert(c0.contains("\"mapStart\":{"), "where the map opens is in the page's settings")
      val (s, w) = post(s"$url/api/settings", """{"changes":[{"key":"setup.completed","value":"true"}]}""")
      assertEquals(s, 200, w)
      val (_, c1) = get(s"$url/api/config")
      assert(c1.contains("\"setupDue\":false"), c1.takeRight(300))
      assert(Files.readString(file).startsWith("# nothing set yet\n"))
    }

  test("the restart is refused where no service starts the server again"):
    withServer(fileText()) { url =>
      val (s, b) = post(s"$url/api/server/restart", "")
      assertEquals(s, 503, b)
      assert(b.contains("cannot restart itself"), b)
    }
