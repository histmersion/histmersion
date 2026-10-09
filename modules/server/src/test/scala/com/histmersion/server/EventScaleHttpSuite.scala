package com.histmersion.server

import ox.supervised
import com.histmersion.core.{AppConfig, EventDetail, EventGeoList}
import io.circe.parser.decode
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** An event's scale (#1440) over real HTTP and MCP: PUT and PATCH take it and refuse a value that is
  * none, `auto` forgets it, `GET /api/events/geo` answers (and is not read as the event `geo`), and
  * the `patch_event` tool writes the same through the one handler. Skipped without
  * HISTMERSION_TEST_DB_URL. */
class EventScaleHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String)  = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())
  private def mcp(url: String, body: String) =
    send(json(s"$url/mcp").header("accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build())

  private val Id = "zqes_http_battle"

  test("PUT/PATCH/GET carry the scale; geo is the map feed; patch_event is the same over MCP"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        delete(s"$url/api/events/$Id")
        val (ps, pb) = put(s"$url/api/events/$Id",
          """{"label":"Zqes HTTP battle","kind":"battle","timeSpan":{"notBefore":-1274,"notAfter":-1274},
            |"geometry":"{\"type\":\"Point\",\"coordinates\":[36.5,34.55]}"}""".stripMargin)
        assertEquals(ps, 200, pb)
        val d0 = decode[EventDetail](pb).fold(e => fail(e.getMessage), identity)
        assertEquals((d0.event.scale, d0.event.scaleStated), ("local", false))

        // a value that is no scale is refused, and nothing changes
        val (bs, bb) = patch(s"$url/api/events/$Id", """{"scale":"global"}""")
        assertEquals(bs, 400, bb)
        assert(bb.contains("local | regional | auto"), bb)
        assertEquals(put(s"$url/api/events/$Id", """{"label":"x","scale":"planet"}""")._1, 400)

        val (s1, b1) = patch(s"$url/api/events/$Id", """{"scale":"regional"}""")
        assertEquals(s1, 200, b1)
        assertEquals(decode[EventDetail](b1).map(d => (d.event.scale, d.event.scaleStated)), Right(("regional", true)))
        val (s2, b2) = get(s"$url/api/events/$Id")
        assertEquals(s2, 200, b2)
        assert(b2.contains("\"scale\":\"regional\""), b2)

        // the map feed — before `events/{id}`, so `geo` is not an event id
        val (gs, gb) = get(s"$url/api/events/geo?text=Zqes%20HTTP")
        assertEquals(gs, 200, gb)
        val geo = decode[EventGeoList](gb).fold(e => fail(s"${e.getMessage}: $gb"), identity)
        val ours = geo.events.find(_.id == Id).getOrElse(fail(gb))
        assertEquals((ours.lat, ours.lon, ours.anchor, ours.scale), (34.55, 36.5, "event", "regional"))
        assertEquals(ours.outline, None, "a point has no outline to draw")

        // MCP: the tool is listed, and patches through the same handler
        assert(mcp(url, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")._2.contains("\"patch_event\""))
        val (ms, mb) = mcp(url,
          s"""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"patch_event","arguments":{"id":"$Id","scale":"auto","lat":34.5,"lon":36.6}}}""")
        assertEquals(ms, 200, mb)
        assert(mb.contains("**Scale:** local (derived)"), mb)
        val after = decode[EventDetail](get(s"$url/api/events/$Id")._2).fold(e => fail(e.getMessage), identity)
        assertEquals((after.event.scale, after.event.scaleStated), ("local", false))
        assert(after.geometry.exists(_.contains("36.6")), after.geometry.toString)
        val (_, refused) = mcp(url,
          s"""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"patch_event","arguments":{"id":"$Id","lat":34.5}}}""")
        assert(refused.contains("lat and lon go together"), refused)
        val (_, md) = mcp(url,
          s"""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"events","arguments":{"query":"Zqes HTTP"}}}""")
        assert(md.contains("battle · local · attested"), md)
      finally
        delete(s"$url/api/events/$Id")
        b.stop()
    }
