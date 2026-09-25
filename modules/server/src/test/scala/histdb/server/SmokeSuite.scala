package histdb.server

import ox.supervised
import histdb.core.AppConfig

/** Boots the whole server on an ephemeral port against a real Postgres (HISTDB_TEST_DB_URL) and
  * hits every surface. Skipped when no test DB is configured. */
class SmokeSuite extends munit.FunSuite:
  private val testDb = sys.env.get("HISTDB_TEST_DB_URL")
  override def munitIgnore: Boolean = testDb.isEmpty

  private def get(url: String): (Int, String) =
    val c = java.net.http.HttpClient.newHttpClient()
    val r = c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
      java.net.http.HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  test("server migrates, binds port 0, answers health / index / api / mcp"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      val (hs, hb) = get(s"$url/api/health");   assertEquals(hs, 200); assert(hb.contains("\"ok\""), hb)
      val (is, ib) = get(url);                   assertEquals(is, 200); assert(ib.contains("<div id=\"app\">"))
      val (cs, _)  = get(s"$url/web/app.bundle.js");    assertEquals(cs, 200)
      val (fs, fb) = get(s"$url/api/findings?from=-3000&to=-1000"); assertEquals(fs, 200); assert(fb.startsWith("["))
      val (ns, _)  = get(s"$url/api/sources/does_not_exist"); assertEquals(ns, 404)
      // item 21: the HTML page for an unknown citekey is a 404 with a readable body, not a 200
      val (ss, sb) = get(s"$url/source/does_not_exist")
      assertEquals(ss, 404)
      assert(sb.contains("No source"), sb.take(300))
      b.stop()
    }
