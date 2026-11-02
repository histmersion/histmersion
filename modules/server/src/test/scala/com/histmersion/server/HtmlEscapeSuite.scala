package com.histmersion.server

import com.histmersion.config.AppConfig
import ox.supervised

/** The HTML pages are the only place this service emits markup, and both places it interpolated a caller- or
  * agent-supplied string into it were unescaped. These are the regression tests.
  */
class HtmlEscapeSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val payload = """<script>alert(1)</script>"""

  test("ArticleHtml.page escapes the title into <title> (pure)"):
    val html = ArticleHtml.page(payload, "<p>body</p>", PageKind.Source)
    // the page's own <head> script (the reader's theme) is not the payload
    assert(!html.contains(payload) && !html.contains("<script>alert"), html.take(300))
    assert(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; · Histmersion"), html.take(300))
    assert(html.contains("<p>body</p>"), "the body is already HTML and stays as it is")

  test("a page title with quotes and ampersands survives escaping"):
    val html = ArticleHtml.page("""Sea "Peoples" & Co""", "", PageKind.Article)
    assert(html.contains("Sea &quot;Peoples&quot; &amp; Co"), html.take(300))

  test("GET /source/<script> reflects nothing executable, and /api/junk is a JSON 404"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      def get(u: String): (Int, String) =
        val c = java.net.http.HttpClient.newHttpClient()
        val r = c.send(
          java.net.http.HttpRequest.newBuilder(java.net.URI.create(u)).build(),
          java.net.http.HttpResponse.BodyHandlers.ofString()
        )
        (r.statusCode, r.body)

      // the "No source <code>…</code>" page reflected the raw path segment.
      // (Item 21 gave that page a 404: no such source really is not found. The body is unchanged,
      // which is what this suite is about.)
      val enc = java.net.URLEncoder.encode(payload, "UTF-8")
      val (s1, b1) = get(s"$url/source/$enc")
      assertEquals(s1, 404)
      assert(!b1.contains("<script>alert(1)</script>"), b1.take(400))
      assert(b1.contains("&lt;script&gt;"), b1.take(400))

      // …and the fragment form, which skips ArticleHtml.page entirely
      val (s2, b2) = get(s"$url/source/$enc?fragment=true")
      assertEquals(s2, 404)
      assert(!b2.contains("<script>alert(1)</script>"), b2)

      // 13: an unmatched /api/* path is a JSON 404, not the web UI with a 200
      val (s3, b3) = get(s"$url/api/no_such_thing")
      assertEquals(s3, 404)
      assert(b3.contains("no such endpoint"), b3)
      assert(!b3.contains("<!doctype html>"), b3.take(200))

      // …and an unmatched non-api GET is a 404 too, while the root still serves the web UI
      val (s4, _) = get(s"$url/definitely/not/a/page")
      assertEquals(s4, 404)
      val (s5, b5) = get(url)
      assertEquals(s5, 200)
      assert(b5.contains("<div id=\"app\">"), b5.take(200))

      b.stop()
    }
