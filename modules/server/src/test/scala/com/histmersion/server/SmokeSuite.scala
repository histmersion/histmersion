package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.AppConfig
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.api.instance.BuildId
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** Boots the whole server on an ephemeral port against a real Postgres (HISTMERSION_TEST_DB_URL) and hits every
  * surface. Skipped when no test DB is configured.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SmokeSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private def get(url: String): (Int, String) =
    val c = java.net.http.HttpClient.newHttpClient()
    val r = c.send(
      java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
      java.net.http.HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  test("server migrates, binds port 0, answers health / index / api / mcp"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition
        .server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter())
        .start() // never the live sidecar
      val url = s"http://127.0.0.1:${b.port}"
      val (hs, hb) = get(s"$url/api/health"); assertEquals(hs, 200); assert(hb.contains("\"ok\""), hb)
      val (is, ib) = get(url); assertEquals(is, 200); assert(ib.contains("<div id=\"app\">"))
      val (cs, _) = get(s"$url/web/app.bundle.js"); assertEquals(cs, 200)
      val build = Static.read(Static.ReadPath("web/build-id.txt")).value.trim
      assert(ib.contains(s"/web/app.bundle.js?v=$build") && ib.contains(s"/web/boot.js?v=$build"), ib)
      val (us, ub) = get(s"$url/api/config"); assertEquals(us, 200)
      assert(ub.contains(s""""buildId":"$build""""), ub.take(400))
      val answer = java.net.http.HttpClient
        .newHttpClient()
        .send(
          java.net.http.HttpRequest.newBuilder(java.net.URI.create(s"$url/api/health")).build(),
          java.net.http.HttpResponse.BodyHandlers.discarding()
        )
      assertEquals(answer.headers().firstValue(BuildId.Header).orElse(""), build)
      // the bookkeeping lists' addresses serve the page (it opens their window); anything else is a 404
      List("acquire", "tasks", "log").foreach { p =>
        val (ps, pb) = get(s"$url/$p"); assertEquals(ps, 200, p); assert(pb.contains("<div id=\"app\">"), p)
      }
      assertEquals(get(s"$url/logs")._1, 404)
      val (fs, fb) = get(s"$url/api/findings?from=-3000&to=-1000"); assertEquals(fs, 200); assert(fb.startsWith("["))
      val (ns, _) = get(s"$url/api/sources/does_not_exist"); assertEquals(ns, 404)
      // item 21: the HTML page for an unknown citekey is a 404 with a readable body, not a 200
      val (ss, sb) = get(s"$url/source/does_not_exist")
      assertEquals(ss, 404)
      assert(sb.contains("No source"), sb.take(300))
      b.stop()
    }
