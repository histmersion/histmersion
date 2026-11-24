package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.access.SecurityHeaders
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.settings.ConfigSource
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** Every page this server serves, over real HTTP: the Content-Security-Policy (no script but the server's own files, no
  * framing, connections only to this server and the configured tile servers, rebuilt by a config reload), `nosniff` and
  * `X-Frame-Options` on each, the scripts the shells load served as JavaScript (nosniff refuses any other type), and a
  * stored text's hostile markup never reaching a page as markup. Skipped without HISTMERSION_TEST_DB_URL.
  */
class SecurityHeadersHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
  private def get(url: String): HttpResponse[String] =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def put(url: String, body: String): HttpResponse[String] =
    http.send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
  private def delete(url: String) =
    http.send(HttpRequest.newBuilder(URI.create(url)).DELETE().build(), HttpResponse.BodyHandlers.ofString())

  private val ck = "zz_csp_source"
  private val slug = "zz_csp_article"
  private val hostile = """<img src=x onerror=alert(1)> <script>alert(2)</script> [x](javascript:alert(3))"""

  private lazy val base = AppConfig.load()
  // the headers the settings make: what every response must carry
  private lazy val expected = SecurityHeaders.of(UiConfigViews.tileTemplates(base.ui))

  private def headersOf(r: HttpResponse[String]): Map[String, String] =
    expected.map((name, _) => name -> r.headers().firstValue(name).orElse("")).toMap

  private def assertSafe(r: HttpResponse[String], what: String): Unit =
    val got = headersOf(r)
    expected.foreach((name, value) => assertEquals(got(name), value, s"$what: $name"))
    val csp = got("Content-Security-Policy")
    List("script-src 'self'", "frame-ancestors 'none'", "object-src 'none'").foreach(p =>
      assert(csp.contains(p), s"$what: $p")
    )
    assert(!csp.contains("unsafe-eval") && !csp.matches(".*script-src[^;]*unsafe-inline.*"), csp)

  private def connectSrc(r: HttpResponse[String]): String =
    r.headers()
      .firstValue(SecurityHeaders.Policy)
      .orElse("")
      .split("; ")
      .find(_.startsWith("connect-src "))
      .getOrElse(fail(s"no connect-src: ${r.headers()}"))

  private def imgSrc(r: HttpResponse[String]): String =
    r.headers().firstValue(SecurityHeaders.Policy).orElse("").split("; ").find(_.startsWith("img-src ")).getOrElse("")

  test("connect-src lists the configured tile server and no other; a config reload changes the list"):
    val dir = java.nio.file.Files.createTempDirectory("histmersion-csp")
    val file = dir.resolve("histmersion.conf")
    val spool = java.nio.file.Files.createDirectories(dir.resolve("spool"))
    // no environment: the settings come from the file alone
    val env = org.ekrich.config.ConfigFactory.empty
    def write(tiles: String): Unit =
      val _ = java.nio.file.Files.writeString(
        file,
        s"""histmersion {
           |  db.url = "${testDb.get}"
           |  converter.spool-dir = "$spool"
           |  config.drain-seconds = 5
           |  ui.tiles = "$tiles"
           |}
           |""".stripMargin
      )
    def load() = AppConfig.loadWithUserFile(env, Some(file))
    write("https://{s}.tiles-one.example.test/{z}/{x}/{y}.png")
    val loaded = load().fold(e => fail(e.sentences.mkString("; ")), identity)
    val source = ConfigSource(() => load())
    supervised {
      val b = Composition
        .server(
          loaded.config,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          OfflineConverter(),
          source = Some((loaded, source))
        )
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      val muted = List("a", "b", "c").map(s => s"https://$s.tile.openstreetmap.fr")
      List("/", "/api/health").foreach { p =>
        val before = connectSrc(get(url + p))
        assertEquals(
          before,
          ("connect-src 'self'" :: List("a", "b", "c").map(s => s"https://$s.tiles-one.example.test") ++ muted)
            .mkString(" "),
          p
        )
        assert(!before.contains("tiles-two"), before)
        assertEquals(imgSrc(get(url + p)), "img-src 'self' data: blob: https: http:", p)
      }

      write("https://tiles-two.example.test:8443/{z}/{x}/{y}.png")
      val reload = http.send(
        json(s"$url/api/config/reload").POST(HttpRequest.BodyPublishers.ofString("")).build(),
        HttpResponse.BodyHandlers.ofString()
      )
      assertEquals(reload.statusCode, 200, reload.body)
      assert(reload.body.contains("\"published\":true"), reload.body)
      assert(reload.body.contains(ServerGraph.Headers), reload.body)
      List("/", "/api/health").foreach { p =>
        val after = connectSrc(get(url + p))
        assertEquals(after, ("connect-src 'self'" :: "https://tiles-two.example.test:8443" :: muted).mkString(" "), p)
        assert(!after.contains("tiles-one"), after)
        // the image directive is no setting: pinned open (thumbnails and stored images come from any host)
        assertEquals(imgSrc(get(url + p)), "img-src 'self' data: blob: https: http:", p)
      }
      b.stop()
    }

  test("every HTML route, error and redirect carries the CSP, nosniff and the framing policy"):
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      val _ = delete(s"$url/api/articles/$slug")
      val ps = put(
        s"$url/api/sources/$ck",
        s"""{"title":"CSP <b>paper</b>","year":2014,"summary":"${hostile
            .replace("\"", "\\\"")}"}"""
      )
      assertEquals(ps.statusCode, 200, ps.body)
      val pa = put(
        s"$url/api/articles/$slug",
        s"""{"title":"CSP article","kind":"concept","body":"# CSP article\\n\\n[[$ck]] $hostile"}"""
      )
      assertEquals(pa.statusCode, 200, pa.body)

      val pages = List(
        "/" -> 200,
        "/explore" -> 200,
        "/library" -> 200,
        "/research/goals/bronze-age" -> 200,
        "/settings" -> 200,
        "/setup" -> 200,
        "/place/pleiades:579885" -> 200,
        "/acquire" -> 200,
        s"/source/$ck" -> 200,
        s"/source/$ck?fragment=true" -> 200,
        s"/source/$ck?full=true&q=paper" -> 200,
        s"/article/$slug" -> 200,
        s"/article/$slug?fragment=true" -> 200,
        "/source/zz_csp_missing" -> 404,
        "/source/zz_csp_missing?fragment=true" -> 404,
        "/source/%3Cscript%3E" -> 404,
        "/article/zz_csp_missing" -> 404,
        "/articles" -> 302,
        "/definitely/not/a/page" -> 404
      )
      pages.foreach { (path, status) =>
        val r = get(url + path)
        assertEquals(r.statusCode, status, path)
        assertSafe(r, path)
        if status == 200 then
          assert(r.headers().firstValue("content-type").orElse("").startsWith("text/html"), path)
          // the hostile text may be shown (escaped); never as a tag, a handler inside a tag or a link
          assert(!"(?i)<[a-z][^<>]*\\son[a-z]+\\s*=".r.findFirstIn(r.body).isDefined, s"$path: ${r.body}")
          assert(!r.body.contains("<script>alert") && !r.body.contains("<img src=x"), s"$path: ${r.body}")
          assert(!r.body.contains("href=\"javascript:"), s"$path: ${r.body}")
      }

      // the hostile markup is still there to read, as text
      val page = get(s"$url/source/$ck").body
      assert(page.contains("&lt;img src=x onerror=alert(1)&gt;"), page)
      assert(page.contains("&lt;script&gt;alert(2)&lt;/script&gt;"), page)

      // the JSON API and the static files get the same headers; scripts are served as JavaScript
      List("/api/health", "/api/no_such_thing", "/web/style.css").foreach(p => assertSafe(get(url + p), p))
      List("/web/theme.js", "/web/boot.js").foreach { p =>
        val r = get(url + p)
        assertEquals(r.statusCode, 200, p)
        assertSafe(r, p)
        assert(r.headers().firstValue("content-type").orElse("").contains("javascript"), p)
      }
      assert(get(s"$url/web/style.css").headers().firstValue("content-type").orElse("").startsWith("text/css"))

      val _ = delete(s"$url/api/articles/$slug")
      val _ = delete(s"$url/api/sources/$ck")
      b.stop()
    }
