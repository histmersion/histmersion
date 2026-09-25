package com.histmersion.server

import ox.supervised
import com.histmersion.core.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The stream-2.8 surface over real HTTP: status codes and JSON shapes are part of the contract, so
  * they are asserted through the wire rather than through the handlers. Skipped without
  * HISTMERSION_TEST_DB_URL. */
class OpsHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def json(url: String) =
    HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String)                 = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String)  = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String)              = send(json(url).DELETE().build())


  /** `queue_items` is a small, GLOBAL table and this suite writes rows the db module's
    * QueueRepoSuite deletes wholesale against the SAME test database — and sbt runs the two module
    * test tasks in parallel. A Postgres advisory lock (held on its own connection for the life of the
    * suite) serialises them; without it one suite's `DELETE` pulls the row out from under the other.
    * `var` because the connection must outlive `beforeAll` and be closed in `afterAll`. */
  private var lock: java.sql.Connection = null
  private val LockKey = 2808 // stream 2.8

  override def beforeAll(): Unit =
    if testDb.isDefined then
      lock = com.histmersion.db.Db.dataSource(AppConfig.load().db.copy(url = testDb.get)).getConnection
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_lock($LockKey)") } finally st.close()

  override def afterAll(): Unit =
    if lock != null then
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_unlock($LockKey)") } finally st.close()
      lock.close()
      lock = null

  test("lint · queue · query · schema over HTTP"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"

      // ── lint ───────────────────────────────────────────────────────────────────────────────
      val (ls, lb) = get(s"$url/api/lint")
      assertEquals(ls, 200, lb)
      assert(lb.contains("\"dangling-edges\""), lb.take(200))
      assert(lb.contains("\"unresolved-short-citekeys\""), lb.take(200))

      val (rs, rb) = get(s"$url/api/lint?rule=no-findings&rule=no-period")
      assertEquals(rs, 200)
      assert(rb.contains("no-findings") && rb.contains("no-period") && !rb.contains("empty-summary"), rb)

      val (bs, bb) = get(s"$url/api/lint?rule=no-vibes")
      assertEquals(bs, 400, bb)
      assert(bb.contains("no-vibes"), bb)

      val (fs, fb) = post(s"$url/api/lint/fix", "")
      assertEquals(fs, 200, fb)
      assert(fb.contains("resolve-edges"), fb)

      // ── queue ──────────────────────────────────────────────────────────────────────────────
      val (as, ab) = post(s"$url/api/queue/acquire", """{"text":"zzhttp buy a book","topic":"Books"}""")
      assertEquals(as, 200, ab)
      val id = """"id"\s*:\s*(\d+)""".r.findFirstMatchIn(ab).map(_.group(1).toInt).getOrElse(fail(ab))

      val (qs, qb) = get(s"$url/api/queue/acquire")
      assertEquals(qs, 200)
      assert(qb.contains("zzhttp buy a book"), qb.take(300))

      val (ps, pb) = patch(s"$url/api/queue/items/$id", """{"status":"done"}""")
      assertEquals(ps, 200, pb)
      assert(pb.contains("\"status\":\"done\""), pb)
      assert(!get(s"$url/api/queue/acquire")._2.contains("zzhttp buy a book"), "a done item leaves the open list")
      assert(get(s"$url/api/queue/acquire?all=true")._2.contains("zzhttp buy a book"))

      // export renders the file; import parses it back
      val (es, eb) = get(s"$url/api/queue/acquire/export")
      assertEquals(es, 200)
      assert(eb.startsWith("# "), eb.take(80))
      val (is, ib) = post(s"$url/api/queue/acquire/import",
        io.circe.Json.obj("text" -> io.circe.Json.fromString(eb)).noSpaces)
      assertEquals(is, 200, ib)
      assertEquals(get(s"$url/api/queue/acquire/export")._2, eb, "export → import → export is stable")

      // clean up the probe row, and check DELETE reports what it removed
      val all = get(s"$url/api/queue/acquire?all=true")._2
      val probeId = """"id"\s*:\s*(\d+)\s*,\s*"list"\s*:\s*"acquire"\s*,\s*"text"\s*:\s*"zzhttp buy a book""""
        .r.findFirstMatchIn(all).map(_.group(1).toInt).getOrElse(fail(all.take(500)))
      val (ds, db) = delete(s"$url/api/queue/items/$probeId")
      assertEquals(ds, 200, db)
      assert(db.contains("zzhttp buy a book"), db)

      assertEquals(patch(s"$url/api/queue/items/987654321", """{"status":"done"}""")._1, 404)
      assertEquals(delete(s"$url/api/queue/items/987654321")._1, 404)
      assertEquals(get(s"$url/api/queue/notalist")._1, 400)

      // a bad status or created_at used to reach the INSERT: an unknown status produced a row no
      // listing ever returns, and a malformed created_at was a 500 from `?::timestamptz`.
      val (badS, badB) = post(s"$url/api/queue/acquire", """{"text":"zzhttp bad","status":"pending"}""")
      assertEquals(badS, 400, badB)
      assert(badB.contains("unknown status 'pending'"), badB)
      val (cs2, cb2) = post(s"$url/api/queue/acquire",
        """{"text":"zzhttp bad date","createdAt":"yesterday"}""")
      assertEquals(cs2, 400, cb2)
      assert(cb2.contains("ISO-8601"), cb2)
      assertEquals(patch(s"$url/api/queue/items/$id", """{"status":"pending"}""")._1, 400)
      assert(!get(s"$url/api/queue/acquire?all=true")._2.contains("zzhttp bad"),
        "a refused add must not have written a row")

      // ── read-only query ────────────────────────────────────────────────────────────────────
      val (ss, sb) = post(s"$url/api/query", """{"sql":"select count(*) from sources"}""")
      assertEquals(ss, 200, sb)
      assert(sb.contains("\"columns\":[\"count\"]"), sb)

      val (ws, wb) = post(s"$url/api/query", """{"sql":"delete from sources"}""")
      assertEquals(ws, 400, wb)
      assert(wb.toLowerCase.contains("read-only transaction"), wb)

      // ── the live schema ────────────────────────────────────────────────────────────────────
      val (cs, cb) = get(s"$url/api/schema/tables")
      assertEquals(cs, 200)
      assert(cb.contains("\"name\":\"queue_items\""), cb.take(400))
      assert(cb.contains("\"name\":\"sources\""))

      b.stop()
    }

  test("the MCP tool list advertises lint, queue and query"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}/mcp"
      val req = HttpRequest.newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
        .build()
      val (s, body) = send(req)
      assertEquals(s, 200, body)
      List("\"lint\"", "\"queue\"", "\"query\"").foreach(t => assert(body.contains(t), s"$t missing"))
      // the query tool's description is generated from the live schema
      assert(body.contains("queue_items("), "the query tool does not name the live tables")
      b.stop()
    }
