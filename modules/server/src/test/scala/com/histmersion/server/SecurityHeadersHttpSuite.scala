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

/** Every page this server serves, over real HTTP: the Content-Security-Policy (no script but the server's own files, no
  * framing), `nosniff` and `X-Frame-Options` on each, the scripts the shells load served as JavaScript (nosniff refuses
  * any other type), and a stored text's hostile markup never reaching a page as markup. Skipped without
  * HISTMERSION_TEST_DB_URL.
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

  private def headersOf(r: HttpResponse[String]): Map[String, String] =
    SecurityHeaders.All.map((name, _) => name -> r.headers().firstValue(name).orElse("")).toMap

  private def assertSafe(r: HttpResponse[String], what: String): Unit =
    val got = headersOf(r)
    SecurityHeaders.All.foreach((name, value) => assertEquals(got(name), value, s"$what: $name"))
    val csp = got("Content-Security-Policy")
    List("script-src 'self'", "frame-ancestors 'none'", "object-src 'none'").foreach(p =>
      assert(csp.contains(p), s"$what: $p")
    )
    assert(!csp.contains("unsafe-eval") && !csp.matches(".*script-src[^;]*unsafe-inline.*"), csp)

  test("every HTML route, error and redirect carries the CSP, nosniff and the framing policy"):
    val base = AppConfig.load()
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
