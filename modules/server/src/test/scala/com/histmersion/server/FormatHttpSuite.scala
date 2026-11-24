package com.histmersion
package server
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.catalogue.{Lpf, Place}
import com.histmersion.api.checks.{FormatField, FormatReport, FormatRule, Lint}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The formatting lint over real HTTP and MCP: an article write answers with its formatting problems (and
  * `?strict=true` refuses one with an error), `GET …/lint` and `POST …/lint/fix` for an article and a source,
  * `POST /api/lint/markdown`, the corpus rules in `/api/lint`, the `lint` MCP tool on one article, the notice on the
  * rendered page — and `POST /api/places/{id}/rename` (#890), whose old id keeps resolving. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class FormatHttpSuite extends DbSuite with db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())
  private def mcp(url: String, tool: String, args: String): (Int, String) =
    send(
      json(s"$url/mcp")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers
            .ofString(s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$args}}""")
        )
        .build()
    )
  private def decode[A: JsonValueCodec](s: String): A = RawJson.decode[A](s).fold(e => fail(s"$e in $s"), identity)
  private def esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  private val slug = "zz_fmt_article"
  private val ck = "zz_fmt2020_source"

  /** The user's examples in one body: a table glued to its paragraph, bold with a space inside, a link with a space
    * before `(`, an escaped wikilink.
    */
  private val broken =
    """# Formatting
      |
      |The festivals [[zz_fmt2020_source]]:
      || Festival | Date |
      ||---|---|
      || Samain | 1 November |
      |
      |The ** reliefs** and [the letters] (https://example.org/x) and \[\[zz_fmt2020_source\]\].
      |""".stripMargin

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def withServer(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  test("an article write reports its formatting problems; strict refuses; lint, fix and the page agree"):
    withServer { url =>
      List(slug).foreach(s => delete(s"$url/api/articles/$s"))
      assertEquals(put(s"$url/api/sources/$ck", """{"title":"Fmt source","year":2020}""")._1, 200)

      // strict: refused, and nothing written
      val (ss, sb) = put(
        s"$url/api/articles/$slug?strict=true",
        s"""{"title":"Formatting","kind":"concept","body":"${esc(broken)}"}"""
      )
      assertEquals(ss, 400, sb)
      assert(sb.contains("refused (strict)") && sb.contains("table-not-rendered"), sb)
      assertEquals(get(s"$url/api/articles/$slug")._1, 404)

      // not strict: written, and the answer lists every problem with its position
      val (ps, pb) =
        put(s"$url/api/articles/$slug", s"""{"title":"Formatting","kind":"concept","body":"${esc(broken)}"}""")
      assertEquals(ps, 200, pb)
      val v = decode[Article.View](pb)
      assertEquals(
        v.formatting.map(_.rule).toSet,
        Set(FormatRule.TableNotRendered, FormatRule.Emphasis, FormatRule.LinkSyntax, FormatRule.Wikilink)
      )
      assertEquals(v.formatting.find(_.rule == FormatRule.TableNotRendered).map(i => (i.line, i.column)), Some((4, 1)))
      // a read says the same
      assertEquals(decode[Article.View](get(s"$url/api/articles/$slug")._2).formatting, v.formatting)
      val lint = decode[FormatReport](get(s"$url/api/articles/$slug/lint")._2)
      assertEquals(lint.issues, v.formatting)
      assertEquals(lint.subject, slug)

      // the page: a folded notice above the prose; the table does not render (yet)
      val (gs, page) = get(s"$url/article/$slug?fragment=true")
      assertEquals(gs, 200)
      assert(page.contains("""<details class="format-notice has-errors">"""), page.take(600))
      assert(page.contains("This article may not display correctly: 4 formatting problems"), page.take(600))
      assert(!page.contains("<table>"), "the glued table is still text before the fix")

      // a strict patch is judged on the merged article; a non-strict one goes through
      val (sp, spb) =
        patch(s"$url/api/articles/$slug?strict=true", """{"append":"\n\nMore ** text [[zz_fmt2020_source]].\n"}""")
      assertEquals(sp, 400, spb)

      // fix: the wording-safe repairs, written as one guarded patch; the table now renders
      val (fs, fb) = post(s"$url/api/articles/$slug/lint/fix", "")
      assertEquals(fs, 200, fb)
      val fixed = decode[FormatReport](fb)
      assertEquals(fixed.fixed.size, 4, fixed.fixed.mkString("\n"))
      assertEquals(fixed.issues, Nil)
      val body = decode[Article.View](get(s"$url/api/articles/$slug")._2).article.body.value
      assert(
        body.contains("**reliefs**") && body.contains("[the letters](https://example.org/x)") &&
          body.contains("and [[zz_fmt2020_source]].") && body.contains(":\n\n| Festival"),
        body
      )
      val after = get(s"$url/article/$slug?fragment=true")._2
      assert(after.contains("<table>") && !after.contains("format-notice"), after.take(800))
      // the history keeps the text before the repair
      assert(get(s"$url/api/articles/$slug/versions")._2.contains("\"id\""))

      // MCP: get_article carries the Formatting section; the lint tool checks one article and text
      val (_, ga) = mcp(url, "lint", s"""{"text":"${esc("A ** bad** line.")}","fix":true}""")
      assert(ga.contains("emphasis") && ga.contains("Repaired text") && ga.contains("**bad**"), ga)
      val (_, la) = mcp(url, "lint", s"""{"article":"$slug"}""")
      assert(la.contains(s"$slug: no formatting problems"), la)
      val (_, pa) = mcp(url, "patch_article", s"""{"slug":"$slug","patch":{"append":"\\n\\n##Broken heading\\n"}}""")
      assert(pa.contains("## Formatting: 1 formatting problem") && pa.contains("heading-syntax"), pa)

      // the corpus rule sees it too
      val corpus = decode[Lint.LintReport](get(s"$url/api/lint?rule=article-markdown")._2)
      assert(
        corpus.rules.head.items.exists(i => i.citekey.value == slug && i.detail.value.contains("heading-syntax")),
        corpus.toString
      )

      // a Markdown text not stored yet
      val (ts, tb) =
        post(s"$url/api/lint/markdown", s"""{"text":"${esc("Intro\n| a | b |\n|---|---|\n| 1 | 2 |\n")}","fix":true}""")
      assertEquals(ts, 200, tb)
      val tr = decode[FormatReport](tb)
      assertEquals(tr.fixed.size, 1)
      assertEquals(tr.text, Some("Intro\n\n| a | b |\n|---|---|\n| 1 | 2 |\n"))

      delete(s"$url/api/articles/$slug")
    }

  test("a source patch reports the formatting of its summary and notes; strict refuses; lint and fix"):
    withServer { url =>
      assertEquals(put(s"$url/api/sources/$ck", """{"title":"Fmt source","year":2020}""")._1, 200)
      val notes = "## Notes\n\nSee [[ zz_fmt2020_source ]] and the &amp;amp; sign.\n"
      val (rs, rb) = patch(s"$url/api/sources/$ck?strict=true", s"""{"notes":"${esc(notes)}"}""")
      assertEquals(rs, 400, rb) // the double-escaped entity is an error
      val (ps, pb) = patch(s"$url/api/sources/$ck", s"""{"notes":"${esc(notes)}"}""")
      assertEquals(ps, 200, pb)
      val sw = decode[Source.WithFindings](pb)
      assertEquals(
        sw.formatting.map(i => (i.field, i.rule)).toSet,
        Set((FormatField.Notes, FormatRule.Wikilink), (FormatField.Notes, FormatRule.HtmlEntity))
      )
      // a strict patch of something else is not refused for the notes already there
      assertEquals(patch(s"$url/api/sources/$ck?strict=true", """{"tags":{"add":["zz_fmt"]}}""")._1, 200)
      val page = get(s"$url/source/$ck?fragment=true")._2
      assert(page.contains("These notes may not display correctly: 2 formatting problems"), page.take(800))
      val fixed = decode[FormatReport](post(s"$url/api/sources/$ck/lint/fix", "")._2)
      assertEquals((fixed.fixed.size, fixed.issues), (2, Nil))
      assertEquals(decode[FormatReport](get(s"$url/api/sources/$ck/lint")._2).issues, Nil)
      val (_, ms) = mcp(url, "patch_source", s"""{"citekey":"$ck","patch":{"summary":"A ** bad summary."}}""")
      assert(ms.contains("## Formatting: 1 formatting problem") && ms.contains("summary L1:3"), ms)
    }

  test("places rename: REST and MCP, the old id keeps resolving, a taken one is refused (#890)"):
    withServer { url =>
      // a previous run's renamed places and their old ids must not be in the way
      val ds = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
      com.augustnagro.magnum.connect(ds):
        import com.augustnagro.magnum.*
        sql"DELETE FROM place_aliases WHERE alias LIKE 'zz_fmt_place%'".update.run()
        sql"DELETE FROM places WHERE id LIKE 'zz_fmt_place%'".update.run()
      val geo = Lpf.render(
        List(
          Place.Input(
            Place(
              Place.Id("zz_fmt_place_a"),
              Place.Label("Fmt A"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[30.0,39.0]}"""))
            ),
            List(Place.Name(Place.Name.Value("Fmt A")))
          ),
          Place.Input(
            Place(
              Place.Id("zz_fmt_place_other"),
              Place.Label("Fmt Other"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[31.0,39.0]}"""))
            ),
            List(Place.Name(Place.Name.Value("Fmt Other")))
          )
        )
      )
      val (is, ib) = post(s"$url/api/places/import", geo.value)
      assertEquals(is, 200, ib)
      val (rs, rb) = post(s"$url/api/places/zz_fmt_place_a/rename", """{"to":"zz_fmt_place_b"}""")
      assertEquals(rs, 200, rb)
      assertEquals(
        decode[LabelRenamed](rb),
        LabelRenamed(Bundle.Kind.Place, LabelRenamed.From("zz_fmt_place_a"), LabelRenamed.To("zz_fmt_place_b"))
      )
      val (gs, gb) = get(s"$url/api/places/zz_fmt_place_a")
      assertEquals(gs, 200, gb)
      assert(gb.contains("\"zz_fmt_place_b\""), gb)
      // a filter on the old id is answered, not refused or emptied by an unknown place
      assertEquals(get(s"$url/api/sources?place=zz_fmt_place_a")._1, 200)
      assertEquals(post(s"$url/api/places/zz_fmt_place_other/rename", """{"to":"zz_fmt_place_a"}""")._1, 409)
      assertEquals(post(s"$url/api/places/zz_fmt_place_b/rename", """{"to":"Not An Id"}""")._1, 400)
      val (_, m) = mcp(url, "rename_label", """{"kind":"place","from":"zz_fmt_place_b","to":"zz_fmt_place_c"}""")
      assert(m.contains("Place renamed"), m)
      assert(get(s"$url/api/places/zz_fmt_place_a")._2.contains("\"zz_fmt_place_c\""))
    }
