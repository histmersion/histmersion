package com.histmersion
package server
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import ox.supervised
import com.histmersion.config.AppConfig
import com.histmersion.domain.PassageFinder
import com.histmersion.api.library.PassageResult
import com.histmersion.util.database.{Db, Migrate}
import com.augustnagro.magnum.*
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.db.*
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.domain.library.Citekey
import com.histmersion.config.DbConfig
import com.histmersion.config.AuthConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** `passages` (#954) over real HTTP and MCP against a seeded multi-heading body: hits, the offset round trip through
  * `get_source`'s body slice, headings, limit, errors, and an old (renamed) citekey resolving. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class PassagesHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key = "k3y"
  private val Ck = Citekey("zzpass2020_theseus")
  private val New = "zzpass2021_theseus"

  private val Body =
    """|Front matter that names no hero.
       |
       |# Theseus
       |
       |## Burial
       |
       |Theseus was buried on Skyros, where Lycomedes had pushed him off a cliff.
       |Cimon brought the bones back to Athens in 476/5 BC.
       |
       |## Cult
       |
       |The Theseia honoured him each year. Plutarch tells the Skyros story too.
       |
       |# Hittite parallels
       |
       |At Ḫattuša nothing like it is known; Güterbock said so in 1946.
       |""".stripMargin

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): HttpResponse[String] = http.send(req, HttpResponse.BodyHandlers.ofString())
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def json(method: String, url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("Authorization", s"Bearer $Key")
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def enc(s: String) = URLEncoder.encode(s, UTF_8)

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
      sql"DELETE FROM source_aliases WHERE alias LIKE 'zzpass%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzpass%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()

  private def withServer(body: String => Unit): Unit =
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private def seed(url: String): Unit =
    val put = json(
      "PUT",
      s"$url/api/sources/$Ck",
      Json
        .obj("title" -> Json.fromString("Theseus"), "body" -> Json.fromString(Body))
        .noSpaces
    )
    assertEquals(put.statusCode, 200, put.body)

  private def passages(url: String, ck: Citekey, q: String, extra: String = ""): PassageResult =
    val r = get(s"$url/api/sources/$ck/passages?q=${enc(q)}$extra")
    assertEquals(r.statusCode, 200, r.body)
    RawJson.decode[PassageResult](r.body).fold(e => fail(s"$e: ${r.body}"), identity)

  test("the body passages searches is the body get_source slices"):
    withServer { url =>
      seed(url)
      val ds = testPool(cfg.db)
      assertEquals(
        PassageFinder.body(SourceFixtures.bodyChunks(ds, Ck)).value: String,
        SourceFixtures.bodyText(ds, Ck).value: String
      )
    }

  test("hits with headings, and each offset reads on through get_source's body slice"):
    withServer { url =>
      seed(url)
      val r = passages(url, Ck, "skyros", "&context=40")
      assertEquals((r.citekey.value: String, r.matches, r.total), (Ck, 2, 2))
      assertEquals(r.passages.map(_.heading), List(Some("Theseus > Burial"), Some("Theseus > Cult")))
      r.passages.foreach { p =>
        val view = get(s"$url/api/sources/$Ck/view?section=body&offset=${p.offset}&chars=${p.text.value.length}")
        assertEquals(view.statusCode, 200, view.body)
        assert(view.body.startsWith(p.text.value), s"offset ${p.offset}: ${view.body.take(200)} vs ${p.text}")
        assert(p.matches.forall(m => p.text.value.substring(m.start.value, m.end.value).equalsIgnoreCase("skyros")), p)
      }
      // accents optional, and the heading of the second top-level section
      val h = passages(url, Ck, "Hattusa")
      assertEquals(h.passages.map(_.heading), List(Some("Hittite parallels")))
      // regex, and limit truncating what is shown but not what is counted
      val lim = passages(url, Ck, "\\b(theseus|theseia)\\b", "&regex=true&limit=1&context=0")
      assertEquals((lim.matches, lim.total, lim.passages.size), (2, 2, 1))
    }

  test("errors: unknown citekey 404, bad regex and empty query 400 with a readable message"):
    withServer { url =>
      seed(url)
      assertEquals(get(s"$url/api/sources/zzpass1900_none/passages?q=x").statusCode, 404)
      val bad = get(s"$url/api/sources/$Ck/passages?q=${enc("(unclosed")}&regex=true")
      assertEquals(bad.statusCode, 400, bad.body)
      assert(bad.body.contains("not a valid regex"), bad.body)
      assertEquals(get(s"$url/api/sources/$Ck/passages?q=${enc("  ")}").statusCode, 400)
    }

  test("an old citekey resolves to the renamed source"):
    withServer { url =>
      seed(url)
      assertEquals(json("POST", s"$url/api/sources/$Ck/rename", s"""{"newCitekey":"$New"}""").statusCode, 200)
      val r = passages(url, Ck, "Cimon")
      assertEquals((r.citekey, r.matches), (New, 1))
    }

  test("the MCP passages tool renders Markdown and answers an unknown citekey like get_source"):
    withServer { url =>
      seed(url)
      def call(args: String) =
        json(
          "POST",
          s"$url/mcp",
          s"""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"passages","arguments":$args}}"""
        )
      val r = call(s"""{"citekey":"$Ck","query":"bones back"}""")
      assertEquals(r.statusCode, 200, r.body)
      assert(r.body.contains(s"## Passages: \\\"bones back\\\" in `$Ck`"), r.body)
      assert(r.body.contains("**bones back**"), r.body)
      assert(r.body.contains("§ Theseus > Burial"), r.body)
      val none = call("""{"citekey":"zzpass1900_none","query":"x"}""")
      assert(none.body.contains("No source 'zzpass1900_none'"), none.body)
      assert(none.body.contains("Find a citekey with search."), none.body)
      assert(none.body.contains("\"isError\":true"), none.body)
      val bad = call(s"""{"citekey":"$Ck","query":"[","regex":true}""")
      assert(bad.body.contains("not a valid regex"), bad.body)
      val list = json("POST", s"$url/mcp", """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
      assert(list.body.contains("\"passages\""), list.body.take(300))
    }
