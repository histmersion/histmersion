package com.histmersion.server

import ox.supervised
import com.histmersion.config.AppConfig
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The stream-2.8 surface over real HTTP: status codes and JSON shapes are part of the contract, so they are asserted
  * through the wire rather than through the handlers. Skipped without HISTMERSION_TEST_DB_URL.
  */
class OpsHttpSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def json(url: String) =
    HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def patch(url: String, body: String) =
    send(json(url).method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(json(url).DELETE().build())

  /** `queue_items` is a small, GLOBAL table and this suite writes rows the db module's QueueRepoSuite deletes wholesale
    * against the SAME test database — and sbt runs the two module test tasks in parallel. A Postgres advisory lock
    * (held on its own connection for the life of the suite) serialises them; without it one suite's `DELETE` pulls the
    * row out from under the other. `var` because the connection must outlive `beforeAll` and be closed in `afterAll`.
    */
  private var lock: java.sql.Connection = null
  private val LockKey = 2808 // stream 2.8

  override def beforeAll(): Unit =
    if testDb.isDefined then
      lock = testPool(AppConfig.load().db.copy(url = testDb.get)).getConnection
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_lock($LockKey)") }
      finally st.close()

  override def afterSuite(): Unit =
    if lock != null then
      val st = lock.createStatement()
      try { val _ = st.execute(s"SELECT pg_advisory_unlock($LockKey)") }
      finally st.close()
      lock.close()
      lock = null

  test("lint · queue · query · schema over HTTP"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
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
      val (is, ib) =
        post(s"$url/api/queue/acquire/import", io.circe.Json.obj("text" -> io.circe.Json.fromString(eb)).noSpaces)
      assertEquals(is, 200, ib)
      assertEquals(get(s"$url/api/queue/acquire/export")._2, eb, "export → import → export is stable")

      // clean up the probe row, and check DELETE reports what it removed
      val all = get(s"$url/api/queue/acquire?all=true")._2
      val probeId = """"id"\s*:\s*(\d+)\s*,\s*"list"\s*:\s*"acquire"\s*,\s*"text"\s*:\s*"zzhttp buy a book"""".r
        .findFirstMatchIn(all)
        .map(_.group(1).toInt)
        .getOrElse(fail(all.take(500)))
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
      val (cs2, cb2) = post(s"$url/api/queue/acquire", """{"text":"zzhttp bad date","createdAt":"yesterday"}""")
      assertEquals(cs2, 400, cb2)
      assert(cb2.contains("ISO-8601"), cb2)
      assertEquals(patch(s"$url/api/queue/items/$id", """{"status":"pending"}""")._1, 400)
      // the web UI's editor: a blank text is refused, a blank topic clears it, and the rest stays
      val (es2, eb2) = post(s"$url/api/queue/tasks", """{"text":"zzhttp edit me","topic":"Books"}""")
      assertEquals(es2, 200, eb2)
      val eid = """"id"\s*:\s*(\d+)""".r.findFirstMatchIn(eb2).map(_.group(1).toInt).getOrElse(fail(eb2))
      val (bt, btb) = patch(s"$url/api/queue/items/$eid", """{"text":"   "}""")
      assertEquals(bt, 400, btb)
      assert(btb.contains("text is empty"), btb)
      val (et, etb) = patch(s"$url/api/queue/items/$eid", """{"text":"zzhttp edited","topic":""}""")
      assertEquals(et, 200, etb)
      assert(etb.contains("\"text\":\"zzhttp edited\"") && !etb.contains("\"topic\":\"Books\""), etb)
      assertEquals(delete(s"$url/api/queue/items/$eid")._1, 200)
      assert(
        !get(s"$url/api/queue/acquire?all=true")._2.contains("zzhttp bad"),
        "a refused add must not have written a row"
      )

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

  // #906: the goals go to the markdown mirror too, as meta/GOALS.md — rendered server-side from the
  // goals, their links and the linked queue items' state.
  test("GET /api/export/goals renders the goal tree with its links and queue-item state"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      def put(u: String, body: String) = send(json(u).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
      def idOf(body: String) =
        """"id"\s*:\s*(\d+)""".r.findFirstMatchIn(body).map(_.group(1).toInt).getOrElse(fail(body))
      val (ok, gone) = (
        idOf(post(s"$url/api/queue/tasks", """{"text":"zzgoals done item"}""")._2),
        idOf(post(s"$url/api/queue/tasks", """{"text":"zzgoals open item"}""")._2)
      )
      try
        assertEquals(
          put(s"$url/api/goals/zzgoals-top", """{"title":"ZZ top goal","status":"compiled","notes":"Top notes."}""")._1,
          200
        )
        assertEquals(
          put(
            s"$url/api/goals/zzgoals-sub",
            """{"title":"ZZ sub goal","status":"seeding","parentSlug":"zzgoals-top","periodStart":-1300,"periodEnd":-1050}"""
          )._1,
          200
        )
        assertEquals(patch(s"$url/api/queue/items/$ok", """{"status":"done"}""")._1, 200)
        for id <- List(ok, gone) do
          val (ls, lb) = post(s"$url/api/goals/zzgoals-sub/links", s"""{"kind":"queue-item","ref":"$id"}""")
          assertEquals(ls, 200, lb)
        assertEquals(post(s"$url/api/goals/zzgoals-sub/links", """{"kind":"query","ref":"zz drought"}""")._1, 200)

        // the web UI's links both ways: an item names the goals that link it; a goal's link names the item's list
        val listing = io.circe.parser
          .decode[com.histmersion.api.Queue.QueueListing](get(s"$url/api/queue/tasks?all=true")._2)
          .fold(e => fail(e.toString), identity)
        assertEquals(listing.goals.find(_.item == gone).map(_.goals.map(_.slug)), Some(List("zzgoals-sub")))
        assertEquals(listing.goals.find(_.item == ok).flatMap(_.goals.headOption).map(_.title), Some("ZZ sub goal"))
        val view = get(s"$url/api/goals/zzgoals-sub/view")._2
        assert(view.contains(s""""ref":"$gone"""") && view.contains(""""queue":"tasks""""), view)

        val (es, eb) = get(s"$url/api/export/goals")
        assertEquals(es, 200, eb)
        assert(eb.startsWith(com.histmersion.api.GoalsExport.Header), eb.take(200))
        assert(eb.contains("Do not edit"), eb.take(400))
        val top = eb.indexOf("\n## ZZ top goal\n")
        val sub = eb.indexOf("\n### ZZ sub goal\n")
        assert(top >= 0 && sub > top, eb)
        assert(eb.contains("`zzgoals-sub` · seeding  \n**Period:** 1300 BCE–1050 BCE"), eb)
        assert(eb.contains(s"- [x] `#$ok` zzgoals done item"), eb)
        assert(eb.contains(s"- [ ] `#$gone` zzgoals open item"), eb)
        assert(eb.contains("- \"zz drought\""), eb)
        // the queue item is deleted: its link stays, marked
        assertEquals(delete(s"$url/api/queue/items/$gone")._1, 200)
        assert(get(s"$url/api/export/goals")._2.contains(s"- `#$gone` (not found)"))
        assertEquals(get(s"$url/api/export/goals")._2, get(s"$url/api/export/goals")._2, "deterministic")
      finally
        val _ = delete(s"$url/api/goals/zzgoals-sub")
        val _ = delete(s"$url/api/goals/zzgoals-top")
        val _ = delete(s"$url/api/queue/items/$ok")
        val _ = delete(s"$url/api/queue/items/$gone")
        b.stop()
    }

  // The READMEs said "29 tools" and listed 30 of 48; nothing kept them honest. The server's own
  // tools/list is the authority — every tool it advertises must be documented, with the right count.
  test("every MCP tool the server advertises is documented in the docs and the plugin README, with the right count"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val req = HttpRequest
        .newBuilder(URI.create(s"http://127.0.0.1:${b.port}/mcp"))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
        .build()
      val (s, body) = send(req)
      b.stop()
      assertEquals(s, 200, body)
      val tools = io.circe.parser
        .parse(body.dropWhile(_ != '{'))
        .toOption
        .flatMap(_.hcursor.downField("result").downField("tools").values)
        .map(_.flatMap(_.hcursor.downField("name").as[String].toOption).toList)
        .getOrElse(Nil)
      assert(tools.size > 40, tools.toString)
      // every {{tool:x}} placeholder in the canonical agents and skills names a tool /mcp really has
      val named = com.histmersion.server.agents.Generated
        .known(
          com.histmersion.domain.automation.agents.AgentDefinitions(
            com.histmersion.domain.automation.agents.Prompts(""),
            com.histmersion.server.agents.TransitionWorkflows.roster
          )
        )
      assertEquals(named.filterNot(tools.toSet).toList.sorted, Nil, "placeholders naming no MCP tool")
      val root = Iterator
        .iterate(java.nio.file.Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
        .takeWhile(_ != null)
        .find(d => java.nio.file.Files.exists(d.resolve("build.sbt")))
        .get
      for doc <- List("docs/user-guide/reference/mcp-tools.md", "plugin/README.md") do
        val text = java.nio.file.Files.readString(root.resolve(doc))
        val missing = tools.filterNot(t => text.contains(s"`$t`"))
        assert(missing.isEmpty, s"$doc does not document: ${missing.mkString(", ")}")
        assert(text.contains(s"**${tools.size} tools**"), s"$doc does not state **${tools.size} tools**")
    }

  test("the MCP tool list advertises lint, queue and query"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}/mcp"
      val req = HttpRequest
        .newBuilder(URI.create(url))
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

  test("POST /api/articles/{slug}/check and the check_edits MCP tool dry-run edits, writing nothing"):
    val base = AppConfig.load()
    val cfg = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val body = "Tin reached the Aegean by sea.\n\n## Disagreements\n\nNone.\n"
        val (ps, pb) = send(
          json(s"$url/api/articles/zzhttp_check")
            .PUT(
              HttpRequest.BodyPublishers.ofString(
                io.circe.Json
                  .obj(
                    "title" -> io.circe.Json.fromString("Zz check"),
                    "kind" -> io.circe.Json.fromString("index"),
                    "body" -> io.circe.Json.fromString(body)
                  )
                  .noSpaces
              )
            )
            .build()
        )
        assertEquals(ps, 200, pb)
        val edits =
          """{"edits":[{"op":"replace","anchor":"by sea.","text":"by sea [[zzhttp_ghost]]."},{"op":"delete","anchor":"nowhere"}]}"""
        val (s, r) = post(s"$url/api/articles/zzhttp_check/check", edits)
        assertEquals(s, 200, r)
        val check = io.circe.parser.decode[com.histmersion.api.ArticleEdits.ArticleCheck](r).toOption.get
        assertEquals(check.edits.map(_.applied), List(false, false))
        assert(
          check.problems.exists(_.contains("zzhttp_ghost")) && check.problems.exists(_.startsWith("edit 2 (delete")),
          check.problems
        )
        assertEquals(post(s"$url/api/articles/zzhttp_check/check", "{}")._1, 400)
        // the same check as an MCP tool
        val call =
          s"""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"check_edits","arguments":{"slug":"zzhttp_check",${edits
              .drop(1)}}}"""
        val (ms, mb) = send(
          json(s"$url/mcp")
            .header("accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(call))
            .build()
        )
        assertEquals(ms, 200, mb)
        assert(mb.contains("Check of [[zzhttp_check]]") && mb.contains("zzhttp_ghost"), mb)
        val (_, after) = get(s"$url/api/articles/zzhttp_check")
        assert(after.contains("by sea.") && !after.contains("zzhttp_ghost"), "a check writes nothing")
      finally
        val _ = delete(s"$url/api/articles/zzhttp_check")
        b.stop()
    }
