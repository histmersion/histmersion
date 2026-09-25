package histdb.server

import ox.supervised
import histdb.core.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The article surface over real HTTP (plan §5.2 stream 2.7): the REST contract including its status
  * codes, and the rendered `/article/{slug}` page — whose whole point is that a `[[citekey]]` and a
  * `[[slug]]` resolve to DIFFERENT places. Skipped without HISTDB_TEST_DB_URL. */
class ArticlesHttpSuite extends munit.FunSuite:
  private val testDb                = histdb.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String)  = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String)   = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def post(url: String, body: String)  = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())

  private val slug = "zz_http_article"
  private val ck   = "zz_http_cited"

  test("put → get → patch → page → export → delete over HTTP"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"

      // a previous (failed) run may have left the fixtures behind, and the first assertion below is
      // about a slug that does NOT exist
      List(slug, "zz_http_other", "bronze_age_collapse").foreach(s => delete(s"$url/api/articles/$s"))

      // a source to cite, so the page has one link of each kind to resolve
      val (ss, sb) = put(s"$url/api/sources/$ck",
        """{"title":"The Cited Paper","year":2014,"status":"foundational"}""")
      assertEquals(ss, 200, sb)

      // ── put: an article citing that source and pointing at another article ──────────────────
      val (ps, pb) = put(s"$url/api/articles/$slug",
        s"""{"title":"HTTP article","kind":"concept","summary":"a lede",
            |"tags":["sea-peoples"],"regions":["Levant"],"periodStart":-1220,"periodEnd":-1100,
            |"body":"# HTTP article\\n\\nMedinet Habu [[$ck]] and [[bronze_age_collapse]]."}""".stripMargin)
      assertEquals(ps, 200, pb)
      assert(pb.contains("\"sourcesCited\""), pb)
      assert(pb.contains(ck), pb)

      val (gs, gb) = get(s"$url/api/articles/$slug")
      assertEquals(gs, 200)
      val token = """"updatedAt":"([^"]+)"""".r.findFirstMatchIn(gb).map(_.group(1)).get

      // ── list: by kind, by tag, by text ──────────────────────────────────────────────────────
      val (ls, lb) = get(s"$url/api/articles?kind=concept&tag=sea-peoples")
      assertEquals(ls, 200, lb)
      assert(lb.contains(slug), lb.take(400))
      assert(get(s"$url/api/articles?q=Medinet")._2.contains(slug))
      assert(!get(s"$url/api/articles?q=zzznothingmatchesthis")._2.contains(slug))

      // ── patch: append extends, nothing else moves ───────────────────────────────────────────
      val (as, ab) = patch(s"$url/api/articles/$slug", """{"append":"## Later\n\nmore prose"}""")
      assertEquals(as, 200, ab)
      assert(ab.contains("Medinet Habu"), "the original prose survives an append: " + ab)
      assert(ab.contains("## Later"), ab)

      // 412: the token captured before the patch is now stale
      val (fs, fb) = patch(s"$url/api/articles/$slug", s"""{"summary":"x","ifUpdatedAt":"$token"}""")
      assertEquals(fs, 412, fb)

      // 404 for an unknown slug, on every verb that needs one
      assertEquals(patch(s"$url/api/articles/zz_nope", """{"summary":"x"}""")._1, 404)
      assertEquals(get(s"$url/api/articles/zz_nope")._1, 404)
      assertEquals(get(s"$url/api/articles/zz_nope/export")._1, 404)
      assertEquals(delete(s"$url/api/articles/zz_nope")._1, 404)
      // 400 for an invalid write
      assertEquals(put(s"$url/api/articles/$slug", """{"title":"T","kind":"concept","body":"b",
                                                     |"periodStart":-100,"periodEnd":-900}""".stripMargin)._1, 400)

      // ── the rendered page: a citekey links to /source, an EXISTING slug to /article, and a
      //    slug nothing answers to is plain text rather than a link to a 404 (item 5) ──────────
      val (hs0, hb0) = get(s"$url/article/$slug")
      assertEquals(hs0, 200)
      assert(hb0.contains("""<span class="dangling" title="unresolved link">bronze_age_collapse</span>"""),
        "an article that does not exist is not a link: " + hb0)

      val (cs, cb) = put(s"$url/api/articles/bronze_age_collapse",
        """{"title":"The Bronze Age Collapse","kind":"concept","body":"# The Bronze Age Collapse\n\nprose"}""")
      assertEquals(cs, 200, cb)

      val (hs, hb) = get(s"$url/article/$slug")
      assertEquals(hs, 200)
      assert(hb.contains(s"""<a href="/source/$ck">"""), hb)
      assert(hb.contains("""<a href="/article/bronze_age_collapse">"""), hb)
      assert(hb.contains("Sources cited"), hb)
      assert(hb.contains("The Cited Paper"), "the footer carries title · year · status: " + hb)
      assert(hb.contains("2014"), hb)
      assert(hb.contains("<html"), "the standalone page is a whole document")

      // the modal fragment is the same HTML without the document chrome
      val (frs, frb) = get(s"$url/article/$slug?fragment=true")
      assertEquals(frs, 200)
      assert(!frb.contains("<html"), frb.take(200))
      assert(frb.contains(s"""<a href="/source/$ck">"""), frb)

      // ?q= highlights, exactly as a source page does
      val (qs2, qb) = get(s"$url/article/$slug?q=Medinet")
      assertEquals(qs2, 200)
      assert(qb.contains("<mark"), qb)
      assert(qb.contains("match-nav"), qb)

      // an unknown slug renders a readable page — under a 404, not a 200 (item 21)
      val (ns2, nb2) = get(s"$url/article/zz_nope")
      assertEquals(ns2, 404)
      assert(nb2.contains("No article"), nb2.take(300))

      // ── backlinks: another article that points here shows up on this page ───────────────────
      val (bs, bb) = put(s"$url/api/articles/zz_http_other",
        s"""{"title":"Other","kind":"concept","body":"# Other\\n\\nsee [[$slug]]"}""")
      assertEquals(bs, 200, bb)
      val (h2s, h2b) = get(s"$url/article/$slug")
      assertEquals(h2s, 200)
      assert(h2b.contains("Backlinks"), h2b)
      assert(h2b.contains("""<a href="/article/zz_http_other">"""), h2b)

      // ── export / import round trip, and the slug listing ────────────────────────────────────
      val (es, eb) = get(s"$url/api/articles/$slug/export")
      assertEquals(es, 200)
      assert(eb.startsWith("---\ntype: concept\n"), eb.take(80))
      assert(eb.contains(s"slug: $slug"), eb.take(200))

      val (is, ib) = post(s"$url/api/articles/import", s"""{"text":${quote(eb)},"force":true}""")
      assertEquals(is, 200, ib)
      assert(ib.contains("\"imported\":1"), ib)
      assertEquals(get(s"$url/api/articles/$slug/export")._2, eb, "import → export is byte-stable")

      // unforced, with an mtime older than the row: skipped, so a reseed cannot wipe enrichment
      val (ks, kb) = post(s"$url/api/articles/import", s"""{"text":${quote(eb)},"mtime":1}""")
      assertEquals(ks, 200, kb)
      assert(kb.contains("\"imported\":0") && kb.contains(slug), kb)

      assert(get(s"$url/api/article-slugs")._2.contains(slug))

      // ── delete ──────────────────────────────────────────────────────────────────────────────
      assertEquals(delete(s"$url/api/articles/zz_http_other")._1, 200)
      assertEquals(delete(s"$url/api/articles/bronze_age_collapse")._1, 200)
      assertEquals(delete(s"$url/api/articles/$slug")._1, 200)
      assertEquals(get(s"$url/api/articles/$slug")._1, 404)

      b.stop()
    }

  /** Minimal JSON string literal (the note text carries newlines). */
  private def quote(s: String): String =
    "\"" + s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case c    => c.toString
    } + "\""
