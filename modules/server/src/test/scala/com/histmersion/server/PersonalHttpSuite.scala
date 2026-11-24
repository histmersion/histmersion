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
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** Bookmarks, notes and read marks over REST, and the read-only MCP tools: a bookmark and a note made, listed, changed
  * and removed; a stale note edit answered 412; the tools read them and no tool writes them. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class PersonalHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) = send(
    json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build()
  )
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())
  private def mcp(url: String, body: String) =
    send(
      json(s"$url/mcp")
        .header("accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private val Id = "\"id\":\"([0-9a-f-]{36})\"".r
  private val Updated = "\"updatedAt\":\"([^\"]+)\"".r

  test("bookmarks, notes and read marks over REST; the tools only read them"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter()).start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        val (s1, b1) = post(
          s"$url/api/bookmarks",
          """{"target":{"kind":"point","lat":37.73,"lon":22.75},"label":"zz http bridge"}"""
        )
        assertEquals(s1, 200, b1)
        val bid = Id.findFirstMatchIn(b1).get.group(1)
        val (s2, b2) = get(s"$url/api/bookmarks?kind=point&q=bridge")
        assertEquals(s2, 200, b2)
        assert(b2.contains("zz http bridge"), b2)
        assertEquals(post(s"$url/api/bookmarks", """{"target":{"kind":"source"}}""")._1, 400)

        val (s3, b3) = post(
          s"$url/api/notes",
          """{"target":{"kind":"source","id":"zz_http_note"},"text":"Zz **noted**","title":"Zz http source"}"""
        )
        assertEquals(s3, 200, b3)
        val nid = Id.findFirstMatchIn(b3).get.group(1)
        val seen = Updated.findFirstMatchIn(b3).get.group(1)
        val (s4, b4) = patch(s"$url/api/notes/$nid", s"""{"text":"Zz changed","ifUpdatedAt":"$seen"}""")
        assertEquals(s4, 200, b4)
        assertEquals(patch(s"$url/api/notes/$nid", s"""{"text":"Zz stale","ifUpdatedAt":"$seen"}""")._1, 412)
        assertEquals(get(s"$url/api/notes/00000000-0000-0000-0000-000000000000")._1, 404)

        val (s5, b5) = put(s"$url/api/reading", """{"kind":"source","id":"zz_http_note","read":true}""")
        assertEquals(s5, 200, b5)
        assert(b5.contains("\"read\":true"), b5)
        val (s6, b6) = get(s"$url/api/reading/unread?kind=source&limit=5")
        assertEquals(s6, 200, b6)
        assertEquals(post(s"$url/api/reading/opened", """{"kind":"article","id":"zz_http_art"}""")._1, 200)
        assert(get(s"$url/api/reading?show=opened")._2.contains("zz_http_art"))

        val (ms, mb) = mcp(
          url,
          """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"notes","arguments":{"query":"changed"}}}"""
        )
        assertEquals(ms, 200, mb)
        assert(mb.contains("Zz changed") && mb.contains("Zz http source"), mb)
        val (_, lb) =
          mcp(url, """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"bookmarks","arguments":{}}}""")
        assert(lb.contains("zz http bridge"), lb)
        val (_, tb) = mcp(url, """{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}""")
        assert(tb.contains("\"bookmarks\"") && tb.contains("\"notes\""), tb.take(400))
        assert(
          !"\"name\":\"[a-z_]*(bookmark|note)[a-z_]*\"".r
            .findAllIn(tb)
            .exists(n => n != "\"name\":\"bookmarks\"" && n != "\"name\":\"notes\""),
          "no tool writes a bookmark or a note"
        )

        assertEquals(delete(s"$url/api/notes/$nid")._1, 200)
        assertEquals(delete(s"$url/api/bookmarks/$bid")._1, 200)
        assertEquals(delete(s"$url/api/bookmarks/$bid")._1, 404)
      finally b.stop()
    }
