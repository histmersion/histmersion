package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import io.circe.parser
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.api.library.GeoHitPage
import com.histmersion.api.automation.Goal
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig

/** Offset paging over real HTTP and MCP (#1798 item 8): a cut list's JSON carries `offset` and `next`, its Markdown
  * says `next: offset=N`, and `geo_when` pages (`GET /api/findings/page`). Skipped without HISTMERSION_TEST_DB_URL.
  */
class PagingHttpSuite extends munit.FunSuite with com.histmersion.db.Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
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
  private def decode[A: io.circe.Decoder](s: String): A = parser.decode[A](s).fold(e => fail(s"$e in $s"), identity)

  private def withServer(body: String => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  test("goals: the JSON says where the page starts and where the next one does; the Markdown too"):
    withServer { url =>
      (1 to 3).foreach(i =>
        assertEquals(put(s"$url/api/goals/zz-page-goal-$i", s"""{"title":"Paged $i","status":"planned"}""")._1, 200)
      )
      val (s, b) = get(s"$url/api/goals?status=planned&limit=2")
      assertEquals(s, 200, b)
      val l = decode[Goal.List](b)
      assert((l.total.value: Int) >= 3, b)
      assertEquals(((l.offset.value: Int), l.next.map(n => (n.value: Int))), (0, Some(2)))
      val last = decode[Goal.List](get(s"$url/api/goals?status=planned&limit=2&offset=${(l.total.value: Int) - 1}")._2)
      assertEquals(((last.offset.value: Int), last.next.map(n => (n.value: Int))), ((l.total.value: Int) - 1, None))
      val (_, md) = mcp(url, "goals", """{"status":"planned","limit":2}""")
      assert(md.contains("next: offset=2"), md)
    }

  test("geo_when pages: /api/findings/page and the MCP footer"):
    withServer { url =>
      val src =
        """{"title":"Paging fixture","authors":["P"],"year":2021,"status":"current","tags":["zz-page-geo"],
          | "findings":[{"claim":"First","lat":38.0,"lon":23.0,"periodStart":-1400,"periodEnd":-1300},
          |             {"claim":"Second","lat":38.1,"lon":23.1,"periodStart":-1350,"periodEnd":-1300},
          |             {"claim":"Third","lat":38.2,"lon":23.2,"periodStart":-1300,"periodEnd":-1250}]}""".stripMargin
      assertEquals(put(s"$url/api/sources/zz_page2021_geo", src)._1, 200)
      val p0 = decode[GeoHitPage](get(s"$url/api/findings/page?tag=zz-page-geo&limit=2")._2)
      assertEquals(((p0.total.value: Int), p0.hits.map(_.claim), p0.next.map(n => (n.value: Int))), (3, List("First", "Second"), Some(2)))
      val p1 = decode[GeoHitPage](get(s"$url/api/findings/page?tag=zz-page-geo&limit=2&offset=2")._2)
      assertEquals((p1.hits.map(_.claim), (p1.offset.value: Int), p1.next.map(n => (n.value: Int))), (List("Third"), 2, None))
      // the map feed honours the offset too, and stays a plain array
      assert(get(s"$url/api/findings?tag=zz-page-geo&limit=2&offset=2")._2.startsWith("["))
      val (_, md) = mcp(url, "geo_when", """{"tags":["zz-page-geo"],"limit":2}""")
      assert(md.contains("### 2 of 3 finding(s)") && md.contains("next: offset=2"), md)
      val (_, md2) = mcp(url, "geo_when", """{"tags":["zz-page-geo"],"limit":2,"offset":2}""")
      assert(md2.contains("showing 3–3 of 3") && !md2.contains("next:"), md2)
    }
