package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.api.StatusCount
import com.histmersion.config.*
import com.histmersion.domain.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.api.research.{ArticleMarkdown, Coverage, CritiqueState, DriftKind}
import com.histmersion.api.library.{Citekey, Source}
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig

/** #958 — an article's coverage computed from its recorded filter against seeded sources, over every surface:
  * `GET /api/articles/{slug}/coverage`, the generated sections in `GET /api/articles/{slug}` (and so `article get`),
  * the `/article/{slug}` page, the export (and the import that drops them again), the `article_coverage` MCP tool, and
  * the 404 of an article that records no filter. Skipped without HISTMERSION_TEST_DB_URL.
  */
class ArticleCoverageHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) = send(
    json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build()
  )
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())

  private val Slug = "zzcov_tin"
  private val Plain = "zzcov_plain"
  private val Topic = "zzcov_tin_trade"
  // in the filter: two cited, three not (one with a reason, one without, one contested without)
  private val Cited1 = Citekey("zzcov2016_tin")
  private val Cited2 = Citekey("zzcov2019_wreck")
  private val Why = Citekey("zzcov2001_egypt")
  private val NoWhy = Citekey("zzcov2010_copper")
  private val Dispute = Citekey("zzcov2024_iso")
  // outside the filter: a critic of a cited source, and a source the body cites from elsewhere
  private val Critic = Citekey("zzcov2022_critic")
  private val Outside = Citekey("zzcov2020_synoikism")

  private def source(
      url: String,
      ck: Citekey,
      topics: List[String],
      status: String = "current",
      edges: String = "",
      body: String = ""
  ) =
    val (s, b) = put(
      s"$url/api/sources/$ck",
      s"""{"title":"Title of $ck","year":2000,"status":"$status",
      |"topics":[${topics.map(t => s"\"$t\"").mkString(",")}],"edges":[$edges]
      |${if body.isEmpty then "" else s""","body":"$body""""}}""".stripMargin
    )
    assertEquals(s, 200, b)

  private def clean(url: String, cfg: AppConfig): Unit =
    List(Slug, Plain).foreach(s => delete(s"$url/api/articles/$s"))
    import com.augustnagro.magnum.*
    connect(testPool(cfg.db)):
      sql"DELETE FROM sources WHERE citekey LIKE 'zzcov%'".update.run()

  private val Body =
    s"""# Tin
       |
       |Tin reached the Aegean by sea [[$Cited1]].
       |
       |## Disagreements
       |
       |The wreck's date is argued [[$Cited2]]; synoikism is another matter [[$Outside]].
       |
       |## Open questions
       |
       |Where the tin was mined is open [[$Cited1]].""".stripMargin

  test("coverage over REST, get_article, the page, the export, MCP; drift; no filter = 404"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        clean(url, cfg)
        List(Cited1, Cited2, Why, NoWhy).foreach(ck => source(url, ck, List(Topic)))
        source(url, Dispute, List(Topic), status = "contested")
        source(url, Outside, List("zzcov_other"), body = "Synoikism joined the villages of Attica.")
        source(url, Critic, List("zzcov_other"), edges = s"""{"dst":"$Cited2","kind":"challenges"}""")

        // ── an article that records its filter and one reason (PATCH JSON) ─────────────────────
        val (ps, pb) = put(
          s"$url/api/articles/$Slug",
          io.circe.Json
            .obj(
              "title" -> io.circe.Json.fromString("Tin"),
              "kind" -> io.circe.Json.fromString("concept"),
              "body" -> io.circe.Json.fromString(Body)
            )
            .noSpaces
        )
        assertEquals(ps, 200, pb)
        assertEquals(get(s"$url/api/articles/$Slug/coverage")._1, 404, "no filter recorded yet")
        val (cs, cb) = patch(
          s"$url/api/articles/$Slug",
          s"""{"filter":{"topics":["$Topic"],"extraQueries":["synoikism"]},
             |"reasons":[{"citekey":"$Why","reason":"About Egypt, not the Aegean"},{"citekey":"$Cited1","reason":"stale"}]}""".stripMargin
        )
        assertEquals(cs, 200, cb)
        assert(
          patch(s"$url/api/articles/$Slug", """{"filter":{"topic":["x"]}}""")._1 == 400,
          "an unknown filter key is refused"
        )
        assertEquals(patch(s"$url/api/articles/$Slug", """{"filter":{"status":["famous"]}}""")._1, 400)

        // ── GET …/coverage: computed against the live corpus ──────────────────────────────────
        val (gs, gb) = get(s"$url/api/articles/$Slug/coverage")
        assertEquals(gs, 200, gb)
        val c = io.circe.parser.decode[Coverage](gb).fold(e => fail(e.getMessage), identity)
        assertEquals((c.total, c.checked), (5, 5))
        assertEquals(c.cited.map(_.citekey), List(Cited1, Cited2).sortBy[String](_.value))
        assertEquals(
          c.notUsed.map(e => e.source.citekey -> e.reason),
          List(Why -> Some("About Egypt, not the Aegean"), NoWhy -> None, Dispute -> None).sortBy[String](_._1.value)
        )
        assertEquals(c.citedOutside, List(Outside))
        assertEquals(
          c.statuses,
          List(
            StatusCount(Source.Status.Current, StatusCount.Count(4)),
            StatusCount(Source.Status.Contested, StatusCount.Count(1))
          )
        )
        assertEquals(c.edges, List(Coverage.Edge(Critic, Coverage.Edge.Kind("challenges"), Cited2)))
        assertEquals(c.searches.map(s => (s.query, s.cited)), List(("synoikism", List(Outside))))
        assertEquals(
          c.drift,
          List(
            Coverage.Drift(DriftKind.Unplaced, List(NoWhy, Dispute).sortBy[String](_.value)),
            Coverage.Drift(DriftKind.ReasonForCited, List(Cited1))
          )
        )

        // ── the article as every reader sees it: the generated sections after the body ────────
        val view = io.circe.parser.decode[Article.View](get(s"$url/api/articles/$Slug")._2).toOption.get
        assertEquals(view.coverage, Some(c))
        assertEquals(view.article.body.value.trim, Body.trim, "the body never holds the generated sections")
        val md = ArticleMarkdown.article(view)
        val (notUsedAt, methodAt, citedAt) =
          (md.indexOf("## Not used"), md.indexOf("_Method: "), md.indexOf("## Sources cited"))
        assert(notUsedAt > md.indexOf("Where the tin was mined") && methodAt > notUsedAt && citedAt > methodAt, md)
        assert(md.contains(s"- [[$Why]] — About Egypt, not the Aegean."), md)
        assert(md.contains(s"- [[$NoWhy]], [[$Dispute]] (contested) — not yet assessed"), md)
        assert(md.contains("The filter returns 5 sources. 2 of them are cited above; the other 3 are listed"), md)
        assert(md.contains(s"[[$Critic]] challenges [[$Cited2]]"), md)
        assertEquals(ArticleMarkdown.article(view, List(Article.Part("coverage"))), Article.Coverage.sections(c))
        // the "Sources cited" footer lists what the body cites — not the generated list
        assert(!view.sourcesCited.exists(_.citekey == NoWhy), view.sourcesCited)

        // ── brief (the link preview's read): the same article, without the per-read work ─────
        val brief = io.circe.parser.decode[Article.View](get(s"$url/api/articles/$Slug?brief=true")._2).toOption.get
        assertEquals(brief.coverage, None)
        assertEquals(brief.formatting, Nil)
        assertEquals(brief.modify(_.formatting).setTo(view.formatting).modify(_.coverage).setTo(view.coverage), view)

        // ── the page ──────────────────────────────────────────────────────────────────────────
        val (hs, html) = get(s"$url/article/$Slug?fragment=true")
        assertEquals(hs, 200)
        assert(html.contains("<h2>Not used</h2>") && html.contains("Method:"), html.takeRight(3000))
        assert(html.contains(s"/source/$NoWhy"), "the generated list links its sources")

        // ── the export carries it between markers, and an import drops it again ───────────────
        val (es, exported) = get(s"$url/api/articles/$Slug/export")
        assertEquals(es, 200)
        assert(exported.contains(Article.Coverage.GeneratedStart) && exported.contains("## Not used"), exported)
        assert(exported.contains(s"filter:\n  topics:\n  - $Topic\n  extra_queries:\n  - synoikism\n"), exported)
        assert(exported.contains(s"reasons:\n  $Why: About Egypt, not the Aegean\n  $Cited1: stale\n"), exported)
        val imp =
          io.circe.Json.obj("text" -> io.circe.Json.fromString(exported), "force" -> io.circe.Json.True).noSpaces
        assertEquals(post(s"$url/api/articles/import", imp)._1, 200)
        val again = io.circe.parser.decode[Article.View](get(s"$url/api/articles/$Slug")._2).toOption.get
        assertEquals(again.article.body.value.trim, Body.trim, "the import kept the body, not the generated block")
        assertEquals(again.coverage.map(_.reasons), Some(c.reasons), "and the reasons came back from the frontmatter")

        // ── MCP ───────────────────────────────────────────────────────────────────────────────
        val call =
          s"""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"article_coverage","arguments":{"slug":"$Slug"}}}"""
        val (ms, mb) = send(
          json(s"$url/mcp")
            .header("accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(call))
            .build()
        )
        assertEquals(ms, 200, mb)
        assert(mb.contains(s"Coverage of [[$Slug]]") && mb.contains("neither cited nor given a reason"), mb)

        // ── §6.8: the critique checklist — in the coverage, the Method note and over PATCH ─────
        assertEquals(
          c.critiques.map(p => (p.ref.wire, p.state, p.src.cited, p.dst.cited)),
          List((s"$Critic>$Cited2", CritiqueState.Open, false, true))
        )
        assert(md.contains("disagreements checked: 1 pair — 0 addressed, 0 reasoned, 1 open"), md)
        assert(mb.contains("Disagreements checked") && mb.contains(s"$Critic>$Cited2"), mb)
        val token = again.article.updatedAt
        assertEquals(
          patch(
            s"$url/api/articles/$Slug",
            s"""{"critiqueReasons":[{"src":"$Outside","dst":"$Cited2","reason":"x"}]}"""
          )._1,
          400,
          "no disagreement edge joins them"
        )
        val (rs, rb) = patch(
          s"$url/api/articles/$Slug",
          s"""{"critiqueReasons":[{"src":"$Cited2","dst":"$Critic","reason":"The article cites it for the wreck's date only."}]}"""
        )
        assertEquals(rs, 200, rb)
        val reasoned = io.circe.parser.decode[Article.View](get(s"$url/api/articles/$Slug")._2).toOption.get
        assertEquals(
          reasoned.coverage.toList.flatMap(_.critiques).map(p => (p.ref.wire, p.state, p.reason, p.reasonBy)),
          List(
            (
              s"$Critic>$Cited2",
              CritiqueState.Reasoned,
              Some("The article cites it for the wreck's date only."),
              Some("person")
            )
          )
        )
        assertEquals(reasoned.article.updatedAt, token, "a critique reason alone does not move the article's token")
        assert(
          ArticleMarkdown.article(reasoned).contains("disagreements checked: 1 pair — 0 addressed, 1 reasoned, 0 open")
        )
        assertEquals(
          patch(
            s"$url/api/articles/$Slug",
            s"""{"critiqueReasons":[{"src":"$Critic","dst":"$Cited2","reason":""}]}"""
          )._1,
          200
        )
        assertEquals(
          io.circe.parser
            .decode[Coverage](get(s"$url/api/articles/$Slug/coverage")._2)
            .toOption
            .get
            .critiques
            .map(_.state),
          List(CritiqueState.Open),
          "an empty reason removes it"
        )

        // ── an article with no filter: 404 that says how to set one, and its page has no list ─
        assertEquals(
          put(
            s"$url/api/articles/$Plain",
            """{"title":"Plain","kind":"concept","body":"Plain [[zzcov2016_tin]]."}"""
          )._1,
          200
        )
        val (ns, nb) = get(s"$url/api/articles/$Plain/coverage")
        assertEquals(ns, 404, nb)
        assert(nb.contains("records no filter") && nb.contains("--filter-query"), nb)
        assert(!get(s"$url/article/$Plain?fragment=true")._2.contains("Not used"))
        // clearing the filter stops the generation
        assertEquals(patch(s"$url/api/articles/$Slug", """{"filter":{}}""")._1, 200)
        assertEquals(get(s"$url/api/articles/$Slug/coverage")._1, 404)
      finally
        clean(url, cfg)
        b.stop()
    }
