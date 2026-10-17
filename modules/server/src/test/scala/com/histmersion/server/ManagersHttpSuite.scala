package com.histmersion.server

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.core.{AppConfig, Managers}
import io.circe.parser.decode
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The managers' phase 2 over real HTTP and MCP (agents off: managing works without them, nothing
  * runs): a seeding manager is created paused with its first propose item, `/api/agents/enqueue` routes
  * review / extend / enrich / seed, a bad kind is a 400, and the `manager` tool creates and enqueues
  * through the same handlers. Skipped without HISTMERSION_TEST_DB_URL. */
class ManagersHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String)  = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def post(url: String, body: String) = send(json(url).POST(HttpRequest.BodyPublishers.ofString(body)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def mcp(url: String, body: String) =
    send(json(s"$url/mcp").header("accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build())

  private val Goal = "zqmgr-http-goal"

  private def clean(cfg: AppConfig): Unit =
    val ds = com.histmersion.db.Db.dataSource(cfg.db)
    com.histmersion.db.Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zqmgr%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zqmgr%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zqmgr%'".update.run()

  test("create seed, enqueue every kind, refusals; the manager tool over MCP"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    clean(cfg)
    supervised {
      val b   = Main.server(cfg, "127.0.0.1", 0).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/goals/$Goal", """{"title":"Zq tin"}""")._1, 200)
        assertEquals(put(s"$url/api/sources/zqmgr2019_tin", """{"title":"Zq tin","year":2019}""")._1, 200)
        assertEquals(put(s"$url/api/articles/zqmgr_a", """{"title":"Zq","kind":"concept","body":"Tin [[zqmgr2019_tin]]."}""")._1, 200)

        val (cs, cb) = post(s"$url/api/agents/managers", s"""{"kind":"seed","goal":"$Goal"}""")
        assertEquals(cs, 200, cb)
        val m = decode[Managers.ManagerInfo](cb).fold(e => fail(s"$e: $cb"), identity)
        assertEquals((m.id, m.state, m.counts.pending), (s"seed:$Goal", "paused", 1))
        assertEquals(m.effective.minSources, Some(cfg.agents.seed.minSources))
        assertEquals(post(s"$url/api/agents/managers", """{"kind":"seed"}""")._1, 400, "a seed needs its goal")
        assertEquals(post(s"$url/api/agents/managers", s"""{"kind":"seed","goal":"$Goal"}""")._1, 409)

        def enqueue(kind: String, target: String) =
          val (s, body) = post(s"$url/api/agents/enqueue", s"""{"kind":"$kind","target":"$target"}""")
          assertEquals(s, 200, body)
          decode[Managers.EnqueueResult](body).fold(e => fail(s"$e: $body"), identity)
        assertEquals(enqueue("review", "zqmgr_a").items.map(i => (i.managerId, i.kind)), List(("review", Managers.ArticleReview)))
        assertEquals(enqueue("extend", "zqmgr_a").items.map(_.kind), List(Managers.ArticleExtend))
        assertEquals(enqueue("enrich", "zqmgr_a").items.map(i => (i.managerId, i.target)), List(("enrich", "zqmgr2019_tin")))
        assertEquals(enqueue("seed", Goal).managerId, s"seed:$Goal")
        assertEquals(post(s"$url/api/agents/enqueue", """{"kind":"bogus","target":"x"}""")._1, 400)
        assertEquals(post(s"$url/api/agents/enqueue", """{"kind":"review","target":"zqmgr_nope"}""")._1, 404)
        val (ls, lb) = get(s"$url/api/agents/managers")
        assertEquals(ls, 200, lb)
        assertEquals(decode[List[Managers.ManagerInfo]](lb).map(_.map(_.id).toSet), Right(Set("review", "enrich", s"seed:$Goal")))
        val (is, ib) = get(s"$url/api/agents/managers/review/items")
        assertEquals(is, 200, ib)
        // the review of an article with no filter brought an infer-filter item
        assert(ib.contains(Managers.InferFilter), ib)

        // MCP: the same handlers
        val (ms, mb) = mcp(url,
          s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"manager","arguments":{"action":"enqueue","kind":"enrich","target":"zqmgr2019_tin"}}}""")
        assertEquals(ms, 200, mb)
        assert(mb.contains("Queued for **enrich**"), mb)
        val (_, created) = mcp(url,
          """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"manager","arguments":{"action":"create","kind":"enrich"}}}""")
        assert(created.contains("exists already"), created)
        val (_, listed) = mcp(url,
          """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"manager","arguments":{"action":"list"}}}""")
        assert(listed.contains(s"seed:$Goal") && listed.contains("(seed · goal"), listed)
      finally
        b.stop()
        clean(cfg)
    }
