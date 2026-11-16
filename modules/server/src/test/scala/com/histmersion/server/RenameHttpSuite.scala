package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.AppConfig
import com.histmersion.util.database.{Db, Migrate}
import com.augustnagro.magnum.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.*
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.AuthConfig
import com.histmersion.config.ServerConfig

/** `POST /api/sources/{ck}/rename` over real HTTP, with a write key configured: the write gate, the answer, and the old
  * citekey still answering every read — the JSON routes, the resolver, the export, the article page's links, the MCP
  * tool, and `/source/{old}` redirecting to the new URL. Skipped without HISTMERSION_TEST_DB_URL.
  */
class RenameHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key = "k3y"
  private val Old = "zzhttp2025_book"
  private val New = "zzhttp2011_book"
  private val Other = "zzhttpb2020_other"
  private val Slug = "zzhttp_article"

  // the JDK client does NOT follow redirects by default — which is what lets the 301 be asserted
  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): HttpResponse[String] = http.send(req, HttpResponse.BodyHandlers.ofString())
  private def req(url: String, key: Option[String]) =
    val b = HttpRequest.newBuilder(URI.create(url))
    key.fold(b)(k => b.header("Authorization", s"Bearer $k"))
  private def get(url: String) = send(req(url, None).build())
  private def json(method: String, url: String, body: String, key: Option[String] = Some(Key)) =
    send(
      req(url, key)
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private lazy val cfg =
    val base = AppConfig.load()
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.auth)
      .setTo(base.auth.modify(_.apiKey).setTo(AuthConfig.ApiKey(Key)))

  private def clean(): Unit =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zzhttp%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzhttp%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzhttp%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()

  private def withServer(body: String => Unit): Unit =
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private def seed(url: String): Unit =
    assertEquals(
      json(
        "PUT",
        s"$url/api/sources/$Old",
        s"""{"title":"The Ahhiyawa Texts","year":2011,"notes":"Self [[$Old]]."}"""
      ).statusCode,
      200
    )
    assertEquals(
      json(
        "PUT",
        s"$url/api/sources/$Other",
        s"""{"title":"Other","edges":[{"src":"$Other","dst":"$Old","kind":"references"}]}"""
      ).statusCode,
      200
    )
    assertEquals(
      json(
        "PUT",
        s"$url/api/articles/$Slug",
        s"""{"title":"Zz","kind":"concept","body":"# Zz\\n\\nSee [[$Old|the texts]]."}"""
      ).statusCode,
      200
    )

  test("the rename is a gated write, answers the renamed source with counts, and old ids keep resolving"):
    withServer { url =>
      seed(url)
      val renameUrl = s"$url/api/sources/$Old/rename"
      val body = s"""{"newCitekey":"$New"}"""
      assertEquals(json("POST", renameUrl, body, None).statusCode, 401)
      assertEquals(json("POST", renameUrl, body, Some("wrong")).statusCode, 401)

      val r = json("POST", renameUrl, body)
      assertEquals(r.statusCode, 200, r.body)
      List(
        s""""oldCitekey":"$Old"""",
        s""""newCitekey":"$New"""",
        s""""citekey":"$New"""",
        s""""articles":["$Slug"]""",
        s""""sources":["$New"]""",
        "\"links\":3"
      ) // the article body, its summary (the lede, derived) and the source note
        .foreach(s => assert(r.body.contains(s), s"missing $s in ${r.body}"))

      // JSON reads by the OLD citekey answer with the source under its NEW one
      val g = get(s"$url/api/sources/$Old")
      assertEquals(g.statusCode, 200, g.body)
      assert(g.body.contains(s""""citekey":"$New""""), g.body)
      assert(get(s"$url/api/resolve/$Old").body.contains(s""""citekey":"$New""""))
      assert(get(s"$url/api/neighbors/$Old").body.contains(s""""citekey":"$Other""""))
      assert(get(s"$url/api/graph/$Old").body.contains(s""""root":"$New""""))
      assert(get(s"$url/api/sources/$Old/export").body.contains(s"citekey: $New"))
      assert(get(s"$url/api/sources/$Old/view").body.contains("The Ahhiyawa Texts"))
      assert(get(s"$url/api/sources/$Old/articles").body.contains(Slug))
      assert(get(s"$url/api/source-aliases").body.contains(s"""{"alias":"$Old","citekey":"$New"}"""))

      // the page: the old URL is a permanent redirect to the canonical one, query kept
      val p = get(s"$url/source/$Old?full=true&q=hittite")
      assertEquals(p.statusCode, 301, p.body)
      assertEquals(p.headers.firstValue("location").orElse(""), s"/source/$New?full=true&q=hittite")
      assertEquals(get(s"$url/source/$New").statusCode, 200)
      assertEquals(get(s"$url/source/zzhttp1900_none").statusCode, 404)
      // the article was rewritten and links the new page; its previous text is a version
      val art = get(s"$url/article/$Slug")
      assert(art.body.contains(s"/source/$New"), art.body)
      assert(get(s"$url/api/articles/$Slug/versions").body.contains(Slug))

      // #1799: a write naming the OLD key follows the alias — a rename by it renames the source under
      // its current key, a put by it replaces that source (never a second one shadowing the alias)
      val again = json("POST", renameUrl, s"""{"newCitekey":"zzhttp1999_book"}""")
      assertEquals(again.statusCode, 200, again.body)
      assert(again.body.contains(s""""oldCitekey":"$New"""") && again.body.contains("zzhttp1999_book"), again.body)
      assertEquals(
        json("POST", s"$url/api/sources/zzhttp1999_book/rename", s"""{"newCitekey":"$New"}""").statusCode,
        200
      )
      val shadow = json("PUT", s"$url/api/sources/$Old", """{"title":"shadow"}""")
      assertEquals(shadow.statusCode, 200, shadow.body)
      assert(shadow.body.contains(s""""citekey":"$New"""") && shadow.body.contains("shadow"), shadow.body)
      assertEquals(get(s"$url/api/sources/$Old").statusCode, 200, "the old key still resolves: no source shadows it")
      assertEquals(json("POST", s"$url/api/sources/$New/rename", s"""{"newCitekey":"$Other"}""").statusCode, 409)
      assertEquals(json("POST", s"$url/api/sources/zzhttp1900_none/rename", body).statusCode, 404)
      // a PATCH by the old key patches the renamed source
      val patched = json("PATCH", s"$url/api/sources/$Old", """{"summary":"via the alias"}""")
      assertEquals(patched.statusCode, 200, patched.body)
      assert(patched.body.contains(s""""citekey":"$New""""), patched.body)
    }

  test("the MCP rename_source tool renames through the same handler and needs the key too"):
    withServer { url =>
      seed(url)
      val call =
        s"""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"rename_source",
           |"arguments":{"citekey":"$Old","newCitekey":"$New"}}}""".stripMargin.replace("\n", "")
      assertEquals(json("POST", s"$url/mcp", call, None).statusCode, 401)
      val r = json("POST", s"$url/mcp", call)
      assertEquals(r.statusCode, 200, r.body)
      assert(r.body.contains(s"Renamed `$Old`"), r.body)
      assertEquals(get(s"$url/api/sources/$New").statusCode, 200)
      val list = json("POST", s"$url/mcp", """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
      assert(list.body.contains("\"rename_source\""), list.body.take(300))
    }
