package com.histmersion.server.agents

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*
import com.histmersion.domain.automation.agents.{AgentRepo, ModelSettingsRepo}
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Composition, Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.library.Source

/** Model chains and allowances end to end against a `*_test` database, with sttp-ai's scripted model per chained model:
  * the chain's next model takes over when the first is below its allowance (before a step, during one, or when its plan
  * says so), a run pauses with a reset time when none is left and resumes by itself once a window frees, the settings
  * saved over config survive a restart, `agent doctor` spends nothing, and `/mcp/agent` offers the read-only tools
  * only. Skipped without HISTMERSION_TEST_DB_URL.
  */
class ModelChainRunSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = "zzchain2019_tin"
  private val A = ModelRef(ProviderKind.Local, "zzA")
  private val B = ModelRef(ProviderKind.Local, "zzB")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private val Body =
    """# Introduction
      |
      |Tin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.
      |""".stripMargin

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(
        enabled = true,
        prices = "zzA=1/2; zzB=1/2; sonnet=0/0",
        roles = b.agents.roles.copy(extractor = s"${A.wire} > ${B.wire}")
      ),
      libraryAgents = b.libraryAgents.copy(enrich = b.libraryAgents.enrich.copy(upstream = false))
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def tool(name: String, args: String) =
    AgentResponse("", Seq(ToolCall("call_1", name, args)), StopReason.ToolUse, Usage1, Some("scripted"))

  private val classifyJson =
    """{"summary":"Tin reached the Aegean by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["Tin Trade"],
      |"period":null,"status":"current","statusEvidence":"widely cited"}""".stripMargin
  private val finding =
    """{"claim":"The Uluburun ship carried copper and tin.","quote":"The Uluburun ship carried ten tons of copper and one ton of tin.",
      |"place":null,"start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}""".stripMargin
  private val notesJson = s"""{"notes":"The source traces tin by sea [[$Ck]]."}"""

  /** classify · findings-1 · notes, each answered at once. */
  private def enrichScripts(c: ScriptedClient): Unit =
    c.scripts ++= List(Seq(text(classifyJson)), Seq(text(s"""{"findings":[$finding]}""")), Seq(text(notesJson)))

  private def handlers(clients: Map[ModelRef, LlmClient], cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (
      AgentsControllers(
        ServerEngineSite(deps, h),
        (_, ref) => clients.get(ref).toRight(s"no scripted client for ${ref.wire}")
      ),
      h
    )

  private def withAllowances(s: String) = baseCfg.copy(agents = baseCfg.agents.copy(allowances = s))

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzchain%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzchain%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzchain%'".update.run()
      sql"DELETE FROM agent_role_chains".update.run()
      sql"DELETE FROM agent_allowances".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources.put(Ck, Source.Put(title = "Bronze Age tin", year = Some(2019), body = Some(Body))).isRight
    )

  /** Spend `tokens` on `model`, finished `minutesAgo`, as a recorded LLM attempt of a run of its own. */
  private def spend(model: ModelRef, tokens: Long, minutesAgo: Int = 1): String =
    val run = TestRuns.create(
      ds,
      Pipeline.EnrichSource,
      "zzchain_spent",
      None,
      BigDecimal(1),
      1000000L,
      RunParams(),
      None,
      None,
      None,
      Origin.Person
    )
    transact(ds)(
      AgentRepo.record(
        run.id,
        AgentRepo.StepRecord(
          1,
          1,
          "classify",
          StepKind.Llm,
          StepStatus.Done,
          Some("{}"),
          None,
          Some(model.wire),
          Usage(tokens, 0),
          BigDecimal(0),
          None,
          Instant.now()
        ),
        Nil
      )
    )
    connect(ds)(
      sql"UPDATE agent_steps SET finished_at = now() - make_interval(mins => $minutesAgo) WHERE job_id = ${run.id}".update
        .run()
    )
    run.id

  private def startAndRun(a: AgentsControllers): RunInfo =
    val run = a.startRun(RunRequest("enrich-source", Ck)).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    connect(ds)(AgentRepo.get(run.id)).get

  private def llmModels(id: String) =
    connect(ds)(AgentRepo.steps(id, 200)).filter(_.kind == "llm").map(s => (s.name, s.attempt, s.status, s.model))

  test(
    "the first model below its allowance is passed over: the chain's next one runs every step, the run says so once"
  ):
    val (ca, cb) = (ScriptedClient(A), ScriptedClient(B)); enrichScripts(cb)
    val (a, h) = handlers(Map(A -> ca, B -> cb), withAllowances("local:zzA = 1000 tokens/1h")); seed(h)
    val _ = spend(A, 1500)
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    assertEquals(ca.steps.size, 0, "the spent model was never called")
    assertEquals(llmModels(run.id).map(_._4).distinct, List(Some(B.wire)))
    val passed = run.warnings.filter(_.contains("passed over local:zzA"))
    assertEquals(passed.size, 1, run.warnings)
    assert(passed.head.contains("1k tokens/1h"), passed)
    // the picture: zzA paused until its spend leaves the window, zzB the active model
    val m = a.models
    val ex = m.chains.find(_.role == "extractor").get
    assertEquals(ex.active, Some(B.wire))
    assert(!ex.models.head.usable && ex.models.head.freesAt.isDefined, ex)
    val al = m.allowances.find(_.target == A.wire).get
    assert(!al.usable && al.limits.head.used >= 1500, al)
    assertEquals(m.roles.find(_.role == "extractor").flatMap(_.model), Some(B.wire))

  test(
    "every model below its allowance: the run PAUSES with the reset time, and resumes by itself once a window frees"
  ):
    val ca = ScriptedClient(A)
    val (a, h) =
      handlers(Map(A -> ca, B -> ScriptedClient(B)), withAllowances("local:* = 10000 tokens/1h, reserve 10%"));
    seed(h)
    val spent = spend(A, 15000, minutesAgo = 50)
    val run = startAndRun(a)
    assertEquals(run.status, "paused")
    assert(
      run.error.exists(e =>
        e.startsWith("allowance:") && e.contains("all models for role extractor are below their allowance")
          && e.contains("next reset at")
      ),
      run.error
    )
    val at = Instant.parse(run.resumeAt.getOrElse(fail("no resume time")))
    val expected = Instant.now().plusSeconds(10 * 60)
    assert(math.abs(at.getEpochSecond - expected.getEpochSecond) < 120, s"$at vs $expected")
    // not yet: nothing is re-queued before the reset time
    assertEquals(a.engine.resumeDue(Instant.now()), Nil)
    // the window frees (the spend ages out) and the waker re-queues the run, which then finishes
    connect(ds)(sql"UPDATE agent_steps SET finished_at = now() - interval '2 hours' WHERE job_id = $spent".update.run())
    assertEquals(a.engine.resumeDue(at.plusSeconds(1)), List(run.id))
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.resumeAt, None, "re-queued: the reset time is cleared")
    enrichScripts(ca) // the chain's first model has room again
    a.engine.execute(run.id)
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)

  test("a model that runs out DURING a step hands it to the chain's next model; the cut attempt is not a retry"):
    val (ca, cb) = (ScriptedClient(A), ScriptedClient(B))
    // zzA may spend 1000 tokens: its first call (1100) asks for a tool and passes the line — the loop
    // is told to finish (one forced call), and the step goes to zzB
    ca.scripts += Seq(tool("facets", """{"field":"tags"}"""), text(classifyJson))
    enrichScripts(cb)
    val (a, h) = handlers(Map(A -> ca, B -> cb), withAllowances("local:zzA = 1000 tokens/1h")); seed(h)
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    val classify = connect(ds)(AgentRepo.steps(run.id, 200)).filter(_.name == "classify")
    assertEquals(
      classify.map(s => (s.attempt, s.status, s.model)),
      List((1, "failed", Some(A.wire)), (2, "done", Some(B.wire)))
    )
    assert(classify.head.error.exists(_.contains("reached its allowance during the step")), classify.head.error)

  test("a plan's own usage limit (claude -p) skips the model until its reset; the chain's next takes the step"):
    val until = Instant.now().plusSeconds(3600)
    val limited = new LlmClient:
      val model = A
      def run[Out](call: LlmCall[Out]) =
        throw ModelLimitReached(A, Some(until), "claude -p: Claude AI usage limit reached")
    val cb = ScriptedClient(B); enrichScripts(cb)
    val (a, h) = handlers(Map(A -> limited, B -> cb)); seed(h)
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    val first = connect(ds)(AgentRepo.steps(run.id, 200)).filter(_.kind == "llm").head
    assert(first.error.exists(e => e.contains("usage limit") && e.contains("skipped until")), first)
    assertEquals(llmModels(run.id).count(_._4.contains(A.wire)), 1, "zzA was tried once, then skipped")
    val ex = a.models.chains.find(_.role == "extractor").get
    assertEquals(ex.models.head.freesAt, Some(until.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString))

  test("a provider failure still pauses the run (provider) — only a plan's limit moves down the chain"):
    val down = new LlmClient:
      val model = A
      def run[Out](call: LlmCall[Out]) = throw java.net.ConnectException("refused")
    val (a, h) = handlers(Map(A -> down, B -> ScriptedClient(B))); seed(h)
    val run = startAndRun(a)
    assertEquals(run.status, "paused")
    assert(run.error.exists(_.startsWith("provider:")), run.error)

  test("chains and allowances saved over config survive a restart; reset goes back to config; bad input is refused"):
    val (a, _) = handlers(Map(A -> ScriptedClient(A), B -> ScriptedClient(B)), withAllowances("local:zzA = 5 usd/day"))
    val saved = a.setChain("extractor", ChainSet(List(B.wire, A.wire))).fold(e => fail(e.message), identity)
    assertEquals((saved.source, saved.active, saved.models.map(_.model)), ("saved", Some(B.wire), List(B.wire, A.wire)))
    val st = a
      .setAllowance(AllowanceSet(A.wire, List(LimitSpec(2000000, Measure.Tokens, "5h")), 20))
      .fold(e => fail(e.message), identity)
    assertEquals((st.source, st.reservePct, st.limits.map(_.limit)), ("saved", 20, List("2M tokens/5h")))
    // a new process sees the same
    val (again, _) =
      handlers(Map(A -> ScriptedClient(A), B -> ScriptedClient(B)), withAllowances("local:zzA = 5 usd/day"))
    val m = again.models
    assertEquals(
      m.chains.find(_.role == "extractor").map(c => (c.source, c.models.map(_.model))),
      Some(("saved", List(B.wire, A.wire)))
    )
    assertEquals(
      m.allowances.filter(_.target == A.wire).map(a => (a.source, a.limits.map(_.limit))),
      List(("saved", List("2M tokens/5h"))),
      "the saved allowance replaces config's for the same target"
    )
    assertEquals(Transactor(ds).read(ModelSettingsRepo.chains).get("extractor"), Some(List(B.wire, A.wire)))
    // reset
    assertEquals(
      again.resetChain("extractor").map(c => (c.source, c.models.map(_.model))),
      Right(("config", List(A.wire, B.wire)))
    )
    assertEquals(again.removeAllowance(A.wire).map(_.removed), Right(true))
    assertEquals(again.removeAllowance(A.wire).map(_.removed), Right(false))
    assertEquals(
      again.models.allowances.filter(_.target == A.wire).map(a => (a.source, a.limits.map(_.limit))),
      List(("config", List("5 usd/1d")))
    )
    // refused
    assert(again.setChain("narrator", ChainSet(List(A.wire))).left.exists(_.message.contains("unknown role")))
    assert(again.setChain("extractor", ChainSet(List("ollama:x"))).left.exists(_.message.contains("unknown provider")))
    assert(again.setChain("extractor", ChainSet(Nil)).left.exists(_.message.contains("at least one")))
    assert(
      again
        .setAllowance(AllowanceSet(A.wire, List(LimitSpec(1, Measure.Usd, "fortnight"))))
        .left
        .exists(_.message.contains("not a window"))
    )
    assert(again.setAllowance(AllowanceSet(A.wire, Nil, 120)).left.exists(_.message.contains("0 to 99")))
    assert(again.setAllowance(AllowanceSet("nobody:*", Nil)).left.exists(_.message.contains("unknown provider")))

  test("a saved chain decides which model a run uses; an unready chained model is shown, not refused"):
    val cb = ScriptedClient(B); enrichScripts(cb)
    val (a, h) = handlers(Map(B -> cb)); seed(h)
    val c = a.setChain("extractor", ChainSet(List("local:zzUnpriced", B.wire))).fold(e => fail(e.message), identity)
    assertEquals(c.models.map(e => (e.model, e.ready)), List(("local:zzUnpriced", false), (B.wire, true)))
    assert(c.models.head.problem.exists(_.contains("has no price")), c)
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    assertEquals(llmModels(run.id).map(_._4).distinct, List(Some(B.wire)))
    // a run's own --model still wins over the chain
    assert(a.runnable(Role.Extractor, Some("local:zzUnpriced")).left.exists(_.contains("has no price")))

  // ── agent doctor ───────────────────────────────────────────────────────────────────────────────

  test(
    "doctor: the free model listing (GET only), a model missing upstream, an unpriced model, claude-code's login without the account"
  ):
    val requests = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val stub = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    stub.createContext(
      "/",
      ex => {
        requests.add(s"${ex.getRequestMethod} ${ex.getRequestURI.getPath}")
        val body = """{"object":"list","data":[{"id":"zzA"},{"id":"other"}]}""".getBytes
        ex.sendResponseHeaders(200, body.length.toLong); ex.getResponseBody.write(body); ex.close()
      }
    )
    stub.start()
    val dir = Files.createTempDirectory("hm-doctor-")
    val claude = dir.resolve("claude")
    Files.writeString(
      claude,
      """#!/bin/bash
        |if [ "$1" = "--version" ]; then echo "2.1.289 (Claude Code)"; exit 0; fi
        |if [ "$1 $2" = "auth status" ]; then
        |  echo '{"loggedIn":true,"authMethod":"claude.ai","email":"someone@example.org","orgId":"o-1","subscriptionType":"max"}'; exit 0
        |fi
        |echo "unexpected: $*" >&2; exit 9
        |""".stripMargin
    )
    claude.toFile.setExecutable(true)
    try
      val cc = baseCfg.agents.providers.claudeCode.copy(command = claude.toString)
      val cfg = baseCfg.copy(agents =
        baseCfg.agents.copy(
          roles = baseCfg.agents.roles.copy(extractor = "local:zzA > local:zzMissing > claude-code:sonnet"),
          providers = baseCfg.agents.providers.copy(
            claudeCode = cc,
            local = baseCfg.agents.providers.local.copy(baseUrl = s"http://127.0.0.1:${stub.getAddress.getPort}/v1")
          )
        )
      )
      val deps = Deps(cfg, ds)
      val a =
        AgentsControllers(
          ServerEngineSite(deps, ServerWiring(deps))
        ) // the server's own factory: real clients, nothing called
      val d = a.doctor(offline = false)
      val local = d.providers.find(_.provider == "local").get
      assert(local.used && local.upstreamModels.contains("zzA"), local)
      assert(local.checks.exists(c => c.name == "upstream local:zzMissing" && c.level == CheckLevel.Warn), local.checks)
      assert(
        local.checks.exists(c =>
          c.name == "model local:zzMissing" && c.level == CheckLevel.Fail && c.detail.contains("no price")
        ),
        local.checks
      )
      assert(local.checks.exists(c => c.name == "model local:zzA" && c.level == CheckLevel.Ok), local.checks)
      val code = d.providers.find(_.provider == "claude-code").get
      assert(
        code.checks.exists(c => c.name == "login" && c.level == CheckLevel.Ok && c.detail.contains("plan max")),
        code.checks
      )
      assert(
        code.checks.exists(c => c.name == "allowance" && c.level == CheckLevel.Ok && c.detail.contains("plan floors")),
        "no metered allowance: the plan pool's floors are the cap"
      )
      assert(code.checks.exists(c => c.name == "bind" && c.level == CheckLevel.Ok), code.checks)
      val md = AgentsMarkdown.doctor(d)
      assert(!md.contains("someone@example.org") && !md.contains("o-1"), "the account never leaves the doctor")
      assert(d.providers.find(_.provider == "anthropic").exists(p => !p.used && p.level != CheckLevel.Fail))
      import scala.jdk.CollectionConverters.*
      assertEquals(requests.asScala.toList, List("GET /v1/models"), "one free listing; never a generation")
      // offline: no request at all
      requests.clear()
      assert(
        a.doctor(offline = true).providers.find(_.provider == "local").get.checks.exists(_.detail.contains("offline"))
      )
      assert(requests.isEmpty)
      // the same server bound beyond loopback refuses claude-code (and says why)
      val lan = cfg.copy(server = cfg.server.copy(host = "0.0.0.0"))
      val ld = AgentsControllers(ServerEngineSite(Deps(lan, ds), ServerWiring(Deps(lan, ds)))).doctor(offline = true)
      val bind = ld.providers.find(_.provider == "claude-code").get.checks.find(_.name == "bind").get
      assertEquals(bind.level, CheckLevel.Fail)
      assert(
        ld.providers
          .find(_.provider == "claude-code")
          .get
          .checks
          .exists(c => c.name == "model claude-code:sonnet" && c.detail.contains("beyond loopback"))
      )
    finally stub.stop(0)

  // ── the agent MCP endpoint and the REST settings ───────────────────────────────────────────────

  test(
    "/mcp/agent answers only to a live run token, with ITS agent's tools; the settings and the doctor are reachable over REST"
  ):
    val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    def send(r: HttpRequest) = { val x = http.send(r, HttpResponse.BodyHandlers.ofString()); (x.statusCode, x.body) }
    def mcp(url: String, body: String, token: Option[String] = None) = send(
      token
        .foldLeft(
          HttpRequest
            .newBuilder(URI.create(url))
            .header("content-type", "application/json")
            .header("accept", "application/json, text/event-stream")
        )((b, t) => b.header("authorization", s"Bearer $t"))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
    def names(body: String) = io.circe.parser
      .parse(body.dropWhile(_ != '{'))
      .toOption
      .flatMap(_.hcursor.downField("result").downField("tools").values)
      .map(_.flatMap(_.hcursor.downField("name").as[String].toOption).toSet)
      .getOrElse(Set.empty)
    val off = baseCfg.copy(agents = baseCfg.agents.copy(enabled = false)) // settings and tools need no run pool
    val list = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
    @volatile var hs: Option[ServerWiring] = None // the started server's handlers, for its run tokens
    ox.supervised {
      val b = Composition
        .server(
          off,
          "127.0.0.1",
          0,
          com.histmersion.server.ConverterWiring.client(off.converter),
          onWiring = h => hs = Some(h)
        )
        .start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        val a = hs.get.agents
        // no token, a made-up token: refused before MCP sees the request
        assertEquals(mcp(s"$url/mcp/agent", list)._1, 401)
        assertEquals(mcp(s"$url/mcp/agent", list, Some("hmrt_nope"))._1, 401)
        val skeptic = a.definitions.agent(StepAgent.Skeptic).toOption.get
        val cold = a.definitions.agent(StepAgent.ColdReader).toOption.get
        val g = a.tokens.issue(skeptic, Some(RunScope("r1", 3)), 60000)
        val (s, body) = mcp(s"$url/mcp/agent", list, Some(g.token))
        assertEquals(s, 200, body)
        assertEquals(
          names(body),
          Set("get_source", "passages", "neighbors", "search", "query", "followups"),
          "the skeptic's tools, nothing else"
        )
        assert(!body.contains("\"apply\""), "followups as an agent sees it has no apply")
        // the same token on another agent's path: refused; the classify step's token lists ITS tools
        assertEquals(mcp(s"$url/mcp/agent/${cold.name}", list, Some(g.token))._1, 403)
        val gc = a.tokens.issue(a.definitions.agent(StepAgent.EnrichClassify).toOption.get, None, 60000)
        assertEquals(names(mcp(s"$url/mcp/agent", list, Some(gc.token))._2), Set("facets", "search"))
        // a tool outside the grant is not there to call
        val (fs, fb) = mcp(
          s"$url/mcp/agent",
          """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"facets","arguments":{"field":"tags"}}}""",
          Some(g.token)
        )
        assert(fs != 200 || fb.contains("error") || fb.contains("isError"), fb.take(500))
        seed(ServerWiring(Deps(baseCfg, ds)))
        val (cs, cb) = mcp(
          s"$url/mcp/agent",
          s"""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_source","arguments":{"citekey":"$Ck"}}}""",
          Some(g.token)
        )
        assertEquals(cs, 200, cb)
        assert(cb.contains("Bronze Age tin"), cb.take(500))
        // a revoked (or expired) token is dead
        a.tokens.revoke(g.token)
        assertEquals(mcp(s"$url/mcp/agent", list, Some(g.token))._1, 401)
        val expired = a.tokens.issue(skeptic, None, 1000, now = System.currentTimeMillis() - 5000)
        assertEquals(mcp(s"$url/mcp/agent", list, Some(expired.token))._1, 401)
        val (ps, pb) = send(
          HttpRequest
            .newBuilder(URI.create(s"$url/api/agents/models/extractor"))
            .header("content-type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(s"""{"models":["${B.wire}"]}"""))
            .build()
        )
        assertEquals(ps, 200, pb)
        assert(pb.contains("\"source\":\"saved\""), pb)
        val (gs, gb) = send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/models")).build())
        assertEquals(gs, 200)
        assert(gb.contains("\"chains\"") && gb.contains(B.wire), gb.take(500))
        val (as, ab) = send(
          HttpRequest
            .newBuilder(URI.create(s"$url/api/agents/allowances"))
            .header("content-type", "application/json")
            .PUT(
              HttpRequest.BodyPublishers.ofString(
                """{"target":"claude-code:*","limits":[{"amount":2000000,"measure":"tokens","window":"5h"}],"reservePct":20}"""
              )
            )
            .build()
        )
        assertEquals(as, 200, ab)
        val (ds2, db2) =
          send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/allowances?target=claude-code:*")).DELETE().build())
        assertEquals(ds2, 200, db2)
        assert(db2.contains("\"removed\":true"), db2)
        val (dd, dbody) = send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/doctor?offline=true")).build())
        assertEquals(dd, 200, dbody)
        assert(dbody.contains("\"providers\""), dbody.take(300))
      finally b.stop()
    }
