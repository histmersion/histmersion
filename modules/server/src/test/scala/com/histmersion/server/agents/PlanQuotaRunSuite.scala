package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.PlanQuota.*
import com.histmersion.db.{AgentRepo, Db, Migrate, PlanQuotaRepo}
import com.histmersion.server.{Deps, Handlers, Main}
import sttp.ai.core.agent.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** Plan pools end to end against a `*_test` database (docs/research-plan-quotas.md §5): the pick with
  * plan readings — a window below its floor, a hold, a provider refusal — pauses a run with
  * `plan-quota` until the reset instead of falling through to a paid key (unless the role allows it),
  * resumes it once the window resets; the readings repo keeps the newest; the quota-guard endpoint;
  * the REST surface with a run token under an API key. Skipped without HISTMERSION_TEST_DB_URL. */
class PlanQuotaRunSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck   = "zzplan2019_tin"
  private val Plan = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val Paid = ModelRef(ProviderKind.Anthropic, "zzPaid")
  private val Free = ModelRef(ProviderKind.Local, "zzFree")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get), server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(enabled = true, prices = "sonnet=0/0; zzPaid=3/15; zzFree=0/0",
        enrich = b.agents.enrich.copy(upstream = false),
        roles = b.agents.roles.copy(extractor = s"${Plan.wire} > ${Paid.wire}"),
        providers = b.agents.providers.copy(codex = b.agents.providers.codex.copy(appServer = false))))

  private lazy val ds = Db.dataSource(baseCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private val Body = "# Introduction\n\nTin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.\n"
  private val finding =
    """{"claim":"The Uluburun ship carried copper and tin.","quote":"The Uluburun ship carried ten tons of copper and one ton of tin.",
      |"place":null,"start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}""".stripMargin
  private def enrichScripts(c: ScriptedClient): Unit =
    c.scripts ++= List(
      Seq(text("""{"summary":"Tin by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["tin"],"period":null,"status":"current","statusEvidence":"x"}""")),
      Seq(text(s"""{"findings":[$finding]}""")), Seq(text(s"""{"notes":"Tin by sea [[$Ck]]."}""")))

  private def handlers(clients: Map[ModelRef, LlmClient], cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
    (AgentsHandlers(deps, h, (_, ref) => clients.get(ref).toRight(s"no scripted client for ${ref.wire}")), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzplan%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzplan%'".update.run()
      sql"DELETE FROM plan_quota_readings".update.run()
      sql"DELETE FROM plan_pools".update.run()
      sql"DELETE FROM agent_role_fallthrough".update.run()
      sql"DELETE FROM agent_role_chains".update.run()
      sql"DELETE FROM agent_allowances".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019), body = Some(Body))).isRight)

  private val now = () => System.currentTimeMillis()
  private def low(resetIn: Long = 3600000L) =
    List(Reading("claude", "five_hour", 90, Some(now() + resetIn), rejected = false, now(), PlanQuota.Source.Run),
      Reading("claude", "seven_day", 40, Some(now() + 5 * 86400000L), rejected = false, now(), PlanQuota.Source.Run))

  private def startAndRun(a: AgentsHandlers): RunInfo =
    val run = a.startRun(RunRequest("enrich-source", Ck)).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  test("a plan below its floor PAUSES the run (plan-quota) until the reset — never falls through to the paid key"):
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid))
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid)); seed(h)
    a.pools.record(PoolId.ClaudePlan, low())
    val run = startAndRun(a)
    assertEquals(run.status, "paused", run.error)
    assert(run.error.exists(_.startsWith("plan-quota:")), run.error)
    assert(run.error.exists(_.contains("five_hour: 10% left, floor 25%")), run.error)
    assert(run.error.exists(_.contains("does not fall from a plan to a paid key")), run.error)
    assert(run.resumeAt.isDefined, "it resumes by itself at the reset")
    assertEquals((plan.steps.size, paid.steps.size), (0, 0), "nothing was called, nothing paid")
    // the window resets: the run goes on, on the plan
    connect(ds)(sql"UPDATE plan_quota_readings SET resets_at = now() - interval '1 second'".update.run())
    connect(ds)(sql"UPDATE jobs SET resume_at = now() - interval '1 second' WHERE id = ${run.id}".update.run())
    enrichScripts(plan)
    assertEquals(a.engine.resumeDue(java.time.Instant.now()), List(run.id))
    a.engine.execute(run.id)
    val done = AgentRepo.get(ds, run.id).get
    assertEquals(done.status, "done", done.error)
    assertEquals(paid.steps.size, 0)

  test("with fallthrough on for the role, the chain falls from the plan to the paid key; a free local model never needs it"):
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid)); enrichScripts(paid)
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid)); seed(h)
    a.pools.record(PoolId.ClaudePlan, low())
    assert(a.setFallthrough("extractor", FallthroughSet(true)).exists(_.fallthrough == List("extractor")))
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    assertEquals((plan.steps.size, paid.steps.size > 0), (0, true))
    // a free model after the plan is used whatever fallthrough says
    assert(a.setFallthrough("extractor", FallthroughSet(false)).isRight)
    val (p2, free) = (ScriptedClient(Plan), ScriptedClient(Free)); enrichScripts(free)
    val cfg = baseCfg.copy(agents = baseCfg.agents.copy(roles = baseCfg.agents.roles.copy(extractor = s"${Plan.wire} > ${Free.wire}")))
    val (a2, _) = handlers(Map(Plan -> p2, Free -> free), cfg)
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzplan%'".update.run())
    assertEquals(startAndRun(a2).status, "done")

  test("the hold switch, a provider refusal, a stale reading: the pick's view; floors saved over config"):
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)))
    def planUsable = a.models.chains.find(_.role == "extractor").get.models.head.usable
    assert(planUsable, "no reading: unknown, usable")
    a.setPool("claude-plan", PoolSet(hold = Some(true))).fold(e => fail(e.message), st => assert(st.held && !st.usable, st))
    assert(!planUsable)
    assertEquals(a.picker.pick(Role.Extractor, None).left.toOption.collect { case PickFailure.Exhausted(_, at, r) => (at, r) },
      Some((None, PauseReason.PlanQuota)), "held: no time it frees by itself")
    a.setPool("claude-plan", PoolSet(hold = Some(false)))
    assert(planUsable)
    // a reading old enough to be stale lets a run go on only with the margin
    a.pools.record(PoolId.ClaudePlan, List(Reading("claude", "five_hour", 70, Some(now() + 3600000L), false, now() - 3600000L, PlanQuota.Source.Run)))
    assert(!planUsable, "30% left, floor 25%, but stale: needs 35%")
    // floors saved over config
    val st = a.setPool("claude-plan", PoolSet(floors = Some("five_hour=10"))).fold(e => fail(e.message), identity)
    assertEquals((st.floors, st.floorsSource), ("five_hour=10", "saved"))
    assert(planUsable, "30% left ≥ 10 + 10")
    assert(a.setPool("claude-plan", PoolSet(floors = Some("five_hour=x"))).isLeft)
    assertEquals(a.setPool("claude-plan", PoolSet(floors = Some(""))).map(_.floorsSource), Right("config"))
    assert(a.setPool("nobody-plan", PoolSet(hold = Some(true))).left.exists(_.message.contains("unknown pool")))
    // the ChatGPT plan refused by the provider
    PlanQuotaRepo.setRefusal(ds, PoolId.ChatgptPlan, Some("ordinary usage not allowed"), None, Some("prolite"))
    val q = a.quota
    val gpt = q.plans.find(_.pool == "chatgpt-plan").get
    assert(!gpt.usable && gpt.reason.contains("ordinary usage not allowed") && gpt.planType.contains("prolite"), gpt)
    assertEquals(q.plans.find(_.pool == "claude-plan").map(_.covers), Some(List("claude-code:*")))

  test("readings: the newest per window wins, an older observation never overwrites it"):
    val (a, _) = handlers(Map.empty)
    a.pools.record(PoolId.ClaudePlan, List(Reading("claude", "five_hour", 50, None, false, now(), PlanQuota.Source.Run)))
    a.pools.record(PoolId.ClaudePlan, List(Reading("claude", "five_hour", 99, None, false, now() - 60000, PlanQuota.Source.Mod)))
    assertEquals(PlanQuotaRepo.readings(ds, PoolId.ClaudePlan).map(r => (r.usedPct, r.source)), List((50.0, PlanQuota.Source.Run)))

  test("the quota-guard report: recorded, and answered with stop once the pool is below its floor"):
    val (a, _) = handlers(Map.empty)
    assertEquals(a.guard(ModReport("mod", List(ModWindow("five_hour", 40, None)))).allow, true)
    val stop = a.guard(ModReport("mod", List(ModWindow("five_hour", 80, Some(java.time.Instant.ofEpochMilli(now() + 600000).toString)))))
    assert(!stop.allow && stop.reason.exists(_.contains("floor 25%")) && stop.resetsAt.isDefined, stop)

  test("REST: quota, hold, and the guard endpoint takes a RUN TOKEN where writes need the API key"):
    val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    def send(r: HttpRequest.Builder) = { val x = http.send(r.build(), HttpResponse.BodyHandlers.ofString()); (x.statusCode, x.body) }
    def json(url: String, method: String, body: String, auth: Option[String]) =
      send(auth.foldLeft(HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json"))((b, t) =>
        b.header("authorization", s"Bearer $t")).method(method, HttpRequest.BodyPublishers.ofString(body)))
    val keyed = baseCfg.copy(auth = baseCfg.auth.copy(apiKey = "zz-key"), agents = baseCfg.agents.copy(enabled = false))
    @volatile var hs: Option[Handlers] = None // the started server's handlers, for a run token
    ox.supervised {
      val b = Main.server(keyed, "127.0.0.1", 0, com.histmersion.server.ConverterClient.fromConfig(keyed.converter),
        onHandlers = h => hs = Some(h)).start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        val (gs, gb) = send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/quota")))
        assertEquals(gs, 200, gb)
        assert(gb.contains("\"claude-plan\"") && gb.contains("\"chatgpt-plan\""), gb.take(400))
        assertEquals(json(s"$url/api/agents/quota/claude-plan", "PUT", """{"hold":true}""", None)._1, 401, "a write needs the key")
        val (hs1, hb) = json(s"$url/api/agents/quota/claude-plan", "PUT", """{"hold":true}""", Some("zz-key"))
        assertEquals(hs1, 200, hb)
        assert(hb.contains("\"held\":true"), hb)
        val report = """{"source":"mod","rateLimits":[{"kind":"five_hour","percentUsed":10}]}"""
        assertEquals(json(s"$url/api/agents/quota/claude", "POST", report, None)._1, 401)
        val skeptic = hs.get.agents.definitions.agent(StepAgent.Skeptic).toOption.get
        val tok = hs.get.agents.tokens.issue(skeptic, None, 60000).token
        val (rs, rb) = json(s"$url/api/agents/quota/claude", "POST", report, Some(tok))
        assertEquals(rs, 200, rb)
        assert(rb.contains("\"allow\":false") && rb.contains("held"), rb)
        // a run token is no API key anywhere else
        assertEquals(json(s"$url/api/agents/quota/claude-plan", "PUT", """{"hold":false}""", Some(tok))._1, 401)
      finally b.stop()
    }
