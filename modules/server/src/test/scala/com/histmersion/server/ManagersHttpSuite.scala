package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.config.AppConfig
import com.histmersion.api.automation.Managers
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.util.database.Migrate
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.automation.ApprovalDigest
import com.histmersion.api.automation.Stalls
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The managers' phase 2 over real HTTP and MCP (agents off: managing works without them, nothing runs): a seeding
  * manager is created paused with its first propose item, `/api/agents/enqueue` routes review / extend / enrich / seed,
  * a bad kind is a 400, and the `manager` tool creates and enqueues through the same handlers. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ManagersHttpSuite extends DbSuite:
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
  private def mcp(url: String, body: String) =
    send(
      json(s"$url/mcp")
        .header("accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private val Goal = "zqmgr-http-goal"

  private def clean(cfg: AppConfig): Unit =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zqmgr%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zqmgr%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zqmgr%'".update.run()
      sql"UPDATE agent_autopilot SET scheduler_strategy = NULL, drain_since = NULL, drained_at = NULL".update.run()

  test("create seed, enqueue every kind, refusals; the manager tool over MCP"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    clean(cfg)
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        assertEquals(put(s"$url/api/goals/$Goal", """{"title":"Zq tin"}""")._1, 200)
        assertEquals(put(s"$url/api/sources/zqmgr2019_tin", """{"title":"Zq tin","year":2019}""")._1, 200)
        assertEquals(
          put(s"$url/api/articles/zqmgr_a", """{"title":"Zq","kind":"concept","body":"Tin [[zqmgr2019_tin]]."}""")._1,
          200
        )

        val (cs, cb) = post(s"$url/api/agents/managers", s"""{"kind":"seed","goal":"$Goal"}""")
        assertEquals(cs, 200, cb)
        val m = RawJson.decode[Managers.ManagerInfo](cb).fold(e => fail(s"$e: $cb"), identity)
        assertEquals((m.id, m.state, m.counts.pending), (s"seed:$Goal", "paused", 1))
        assertEquals(m.effective.minSources.map(_.value), Some(cfg.libraryAgents.seed.minSources.value))
        assertEquals(post(s"$url/api/agents/managers", """{"kind":"seed"}""")._1, 400, "a seed needs its goal")
        assertEquals(post(s"$url/api/agents/managers", s"""{"kind":"seed","goal":"$Goal"}""")._1, 409)

        def enqueue(kind: String, target: String) =
          val (s, body) = post(s"$url/api/agents/enqueue", s"""{"kind":"$kind","target":"$target"}""")
          assertEquals(s, 200, body)
          RawJson.decode[Managers.EnqueueResult](body).fold(e => fail(s"$e: $body"), identity)
        assertEquals(
          enqueue("review", "zqmgr_a").items.map(i => (i.managerId, i.kind)),
          List(("review", Managers.ArticleReview))
        )
        assertEquals(enqueue("extend", "zqmgr_a").items.map(_.kind), List(Managers.ArticleExtend))
        assertEquals(
          enqueue("enrich", "zqmgr_a").items.map(i => (i.managerId, i.target)),
          List(("enrich", "zqmgr2019_tin"))
        )
        assertEquals(enqueue("seed", Goal).managerId, s"seed:$Goal")
        assertEquals(post(s"$url/api/agents/enqueue", """{"kind":"bogus","target":"x"}""")._1, 400)
        assertEquals(post(s"$url/api/agents/enqueue", """{"kind":"review","target":"zqmgr_nope"}""")._1, 404)
        val (ls, lb) = get(s"$url/api/agents/managers")
        assertEquals(ls, 200, lb)
        assertEquals(
          RawJson.decode[List[Managers.ManagerInfo]](lb).map(_.map(_.id).toSet),
          Right(Set("review", "enrich", s"seed:$Goal"))
        )
        val (is, ib) = get(s"$url/api/agents/managers/review/items")
        assertEquals(is, 200, ib)
        // the review of an article with no filter brought an infer-filter item
        assert(ib.contains(Managers.InferFilter), ib)

        // MCP: the same handlers
        val (ms, mb) = mcp(
          url,
          s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"manager","arguments":{"action":"enqueue","kind":"enrich","target":"zqmgr2019_tin"}}}"""
        )
        assertEquals(ms, 200, mb)
        assert(mb.contains("Queued for **enrich**"), mb)
        val (_, created) = mcp(
          url,
          """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"manager","arguments":{"action":"create","kind":"enrich"}}}"""
        )
        assert(created.contains("already exists"), created)
        val (_, listed) = mcp(
          url,
          """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"manager","arguments":{"action":"list"}}}"""
        )
        assert(listed.contains(s"seed:$Goal") && listed.contains("(seed · goal"), listed)

        // the central scheduler: its status (a dry pass with agents off), why one item waits, the live switch
        val (ss, sb) = get(s"$url/api/agents/scheduler?limit=5")
        assertEquals(ss, 200, sb)
        val st = RawJson
          .decode[AgentScheduler.SchedulerStatus](sb)
          .fold(e => fail(s"$e: $sb"), identity)
        assertEquals(
          (st.strategy.value: String, st.strategyFrom),
          (cfg.agents.scheduler.strategy.value: String, "config")
        )
        assert(st.strategies.map(_.name).contains("goal-first"), st.strategies)
        val (ws, wb) = get(s"$url/api/agents/scheduler/why/zqmgr2019_tin")
        assertEquals(ws, 200, wb)
        assert(wb.contains("\"manager\":\"enrich\""), wb)
        assertEquals(get(s"$url/api/agents/scheduler/why/zqmgr-nothing")._1, 404)
        val (xs, xb) = post(s"$url/api/agents/scheduler", """{"strategy":"goal-first","by":"test"}""")
        assertEquals(xs, 200, xb)
        assert(xb.contains("\"strategyFrom\":\"person\""), xb)
        assertEquals(post(s"$url/api/agents/scheduler", """{"strategy":"nope"}""")._1, 400)
        val (_, viaMcp) = mcp(
          url,
          """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"scheduler","arguments":{"action":"strategy","strategy":"default"}}}"""
        )
        assert(
          viaMcp.contains("# Scheduler: ") && viaMcp.contains("strategy back to agents.scheduler.strategy"),
          viaMcp
        )

        // the approval digest and a group decision
        val (ds_, db_) = get(s"$url/api/agents/approvals/digest")
        assertEquals(ds_, 200, db_)
        assert(RawJson.decode[ApprovalDigest.Digest](db_).isRight, db_)
        assertEquals(
          post(s"$url/api/agents/approvals/decide", """{"decision":"approve"}""")._1,
          400,
          "never every open approval"
        )
        val (bs, bb) =
          post(s"$url/api/agents/approvals/decide", """{"decision":"reject","kind":"goal-plan","dryRun":true}""")
        assertEquals(bs, 200, bb)
        val (_, dg) = mcp(
          url,
          """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"list_approvals","arguments":{"digest":true}}}"""
        )
        assert(dg.contains("# Waiting for a person"), dg)

        // the stall report and drain mode
        val (ss2, sb2) = get(s"$url/api/agents/stalls?days=0")
        assertEquals(ss2, 200, sb2)
        assert(
          RawJson.decode[Stalls.StallReport](sb2).exists(_.stalled.exists(_.slug.value == Goal)),
          sb2
        )
        val (dr, drb) = post(s"$url/api/agents/autopilot/drain", """{"on":true,"by":"test"}""")
        assertEquals(dr, 200, drb)
        assert(drb.contains("\"drain\""), drb)
        assertEquals(post(s"$url/api/agents/autopilot/drain", """{"on":false}""")._1, 200)
      finally
        b.stop()
        clean(cfg)
    }
