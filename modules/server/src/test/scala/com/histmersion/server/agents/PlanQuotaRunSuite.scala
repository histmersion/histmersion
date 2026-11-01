package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.PlanQuota.*
import com.histmersion.db.{AgentRepo, Db, Migrate, PlanQuotaRepo}
import com.histmersion.server.{Composition, Deps, Handlers}
import sttp.ai.core.agent.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** Plan pools end to end against a `*_test` database (docs/research-plan-quotas.md §5): the pick with plan readings — a
  * window below its floor, a hold, a provider refusal — pauses a run with `plan-quota` until the reset instead of
  * falling through to a paid key (unless the role allows it), resumes it once the window resets; the readings repo
  * keeps the newest; the quota-guard endpoint; the REST surface with a run token under an API key. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class PlanQuotaRunSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = "zzplan2019_tin"
  private val Plan = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val Paid = ModelRef(ProviderKind.Anthropic, "zzPaid")
  private val Free = ModelRef(ProviderKind.Local, "zzFree")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(
        enabled = true,
        prices = "sonnet=0/0; zzPaid=3/15; zzFree=0/0",
        enrich = b.agents.enrich.copy(upstream = false),
        roles = b.agents.roles.copy(extractor = s"${Plan.wire} > ${Paid.wire}"),
        providers = b.agents.providers.copy(codex = b.agents.providers.codex.copy(appServer = false))
      )
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private val Body =
    "# Introduction\n\nTin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.\n"
  private val finding =
    """{"claim":"The Uluburun ship carried copper and tin.","quote":"The Uluburun ship carried ten tons of copper and one ton of tin.",
      |"place":null,"start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}""".stripMargin
  private def enrichScripts(c: ScriptedClient): Unit =
    c.scripts ++= List(
      Seq(
        text(
          """{"summary":"Tin by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["tin"],"period":null,"status":"current","statusEvidence":"x"}"""
        )
      ),
      Seq(text(s"""{"findings":[$finding]}""")),
      Seq(text(s"""{"notes":"Tin by sea [[$Ck]]."}"""))
    )

  private def handlers(clients: Map[ModelRef, LlmClient], cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
    (
      AgentsHandlers(
        TransitionSite(deps, h),
        (_, ref) => clients.get(ref).toRight(s"no scripted client for ${ref.wire}")
      ),
      h
    )

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
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019), body = Some(Body))).isRight)

  private val now = () => System.currentTimeMillis()
  private def low(resetIn: Long = 3600000L) =
    List(
      Reading("claude", "five_hour", 90, Some(now() + resetIn), rejected = false, now(), PlanQuota.Source.Run),
      Reading("claude", "seven_day", 40, Some(now() + 5 * 86400000L), rejected = false, now(), PlanQuota.Source.Run)
    )

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

  // 2026-10-08: the Claude plan at its five_hour floor and the ChatGPT plan HELD paused runs with "no
  // window frees by itself" and no resume time, so they waited for a manual resume past the reset
  test("a held pool later in the chain: the run still resumes by itself at the other pool's reset"):
    val Gpt = ModelRef(ProviderKind.Opencode, "openai/zzGpt")
    val cfg = baseCfg.copy(agents =
      baseCfg.agents.copy(
        roles = baseCfg.agents.roles.copy(extractor = s"${Plan.wire} > ${Gpt.wire}"),
        providers =
          baseCfg.agents.providers.copy(opencode = baseCfg.agents.providers.opencode.copy(chatgptLogin = true))
      )
    )
    val (plan, gpt) = (ScriptedClient(Plan), ScriptedClient(Gpt))
    val (a, h) = handlers(Map(Plan -> plan, Gpt -> gpt), cfg); seed(h)
    assertEquals(a.pools.poolOf(Gpt), Some(PoolId.ChatgptPlan))
    a.pools.record(PoolId.ClaudePlan, low())
    a.setPool("chatgpt-plan", PoolSet(hold = Some(true))).fold(e => fail(e.message), identity)
    val reset = PlanQuotaRepo.readings(ds, PoolId.ClaudePlan).find(_.kind == "five_hour").flatMap(_.resetsAt).get
    val run = startAndRun(a)
    assertEquals(run.status, "paused", run.error)
    assert(run.error.exists(_.startsWith("plan-quota:")), run.error)
    assert(!run.error.exists(_.contains("no window frees by itself")), run.error)
    assert(run.error.exists(_.contains(s"next reset at ${java.time.Instant.ofEpochMilli(reset)}")), run.error)
    assert(run.error.exists(_.contains("the ChatGPT plan is held")), run.error)
    assertEquals(
      run.resumeAt.map(t => java.time.Instant.parse(t).toEpochMilli / 1000),
      Some(reset / 1000),
      "it resumes by itself at the Claude plan's reset"
    )
    assertEquals(a.engine.resumeDue(java.time.Instant.ofEpochMilli(reset - 60000)), Nil, "not before the reset")
    // the window resets: the resume check re-queues the run, which goes on on the Claude plan
    connect(ds)(
      sql"UPDATE plan_quota_readings SET resets_at = now() - interval '1 second' WHERE kind = 'five_hour'".update.run()
    )
    enrichScripts(plan)
    assertEquals(a.engine.resumeDue(java.time.Instant.ofEpochMilli(reset + 1000)), List(run.id))
    a.engine.execute(run.id)
    val done = AgentRepo.get(ds, run.id).get
    assertEquals(done.status, "done", done.error)
    assertEquals(gpt.steps.size, 0, "the held pool was never drawn on")

  test(
    "with fallthrough on for the role, the chain falls from the plan to the paid key; a free local model never needs it"
  ):
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
    val cfg = baseCfg.copy(agents =
      baseCfg.agents.copy(roles = baseCfg.agents.roles.copy(extractor = s"${Plan.wire} > ${Free.wire}"))
    )
    val (a2, _) = handlers(Map(Plan -> p2, Free -> free), cfg)
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzplan%'".update.run())
    assertEquals(startAndRun(a2).status, "done")

  test("the hold switch, a provider refusal, a stale reading: the pick's view; floors saved over config"):
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)))
    def planUsable = a.models.chains.find(_.role == "extractor").get.models.head.usable
    assert(planUsable, "no reading: unknown, usable")
    a.setPool("claude-plan", PoolSet(hold = Some(true)))
      .fold(e => fail(e.message), st => assert(st.held && !st.usable, st))
    assert(!planUsable)
    assertEquals(
      a.picker.pick(Role.Extractor, None).left.toOption.collect { case PickFailure.Exhausted(_, at, r) => (at, r) },
      Some((None, PauseReason.PlanQuota)),
      "held: no time it frees by itself"
    )
    a.setPool("claude-plan", PoolSet(hold = Some(false)))
    assert(planUsable)
    // a reading old enough to be stale lets a run go on only with the margin
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 70, Some(now() + 3600000L), false, now() - 3600000L, PlanQuota.Source.Run))
    )
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

  // #913: a plan model needs no price — before, an unpriced claude-code model was "not ready"
  private lazy val unpricedPlan = baseCfg.copy(agents =
    baseCfg.agents.copy(prices = "zzPaid=3/15", roles = baseCfg.agents.roles.copy(extractor = Plan.wire))
  )

  test("a plan model with NO price is ready, costs 0 USD, and its run is still budgeted by tokens"):
    val plan = ScriptedClient(Plan); enrichScripts(plan)
    val (a, h) = handlers(Map(Plan -> plan), unpricedPlan); seed(h)
    assertEquals(a.runnable(Role.Extractor, None), Right(()))
    val entry = a.models.chains.find(_.role == "extractor").get.models.head
    assert(entry.ready && entry.usable, entry)
    assertEquals(entry.billing, Some("plan pool: claude-plan"))
    val doc = AgentDoctor(baseCfg.copy(agents = unpricedPlan.agents), a.picker, a.resolve).report(online = false)
    val check = doc.providers.find(_.provider == "claude-code").get.checks.find(_.name == s"model ${Plan.wire}").get
    assert(check.level == CheckLevel.Ok && check.detail.startsWith("plan pool: claude-plan"), check)
    // a micro USD budget is never reached (plan calls cost 0 USD), the token budget is: 1100 tokens a
    // call, 1500 allowed → two calls, then the run pauses on tokens
    val run = a
      .startRun(RunRequest("enrich-source", Ck, budgetUsd = Some(0.000001), budgetTokens = Some(1500)))
      .fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    val r = AgentRepo.get(ds, run.id).get
    assertEquals(r.status, "paused", r.error)
    assert(r.error.exists(e => e.startsWith("budget:") && e.contains("tokens")), r.error)
    assertEquals((r.costUsd, r.usage.total), (0.0, 2200L))
    // with room, it finishes on the plan for 0 USD
    a.control(run.id, "resume", None, Some(100000L)).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    val done = AgentRepo.get(ds, run.id).get
    assertEquals((done.status, done.costUsd), ("done", 0.0), done.error)

  test("a METERED unpriced model is still refused; claude-code set to use an API key needs a price"):
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), unpricedPlan)
    assert(a.runnable(Role.Extractor, Some("anthropic:zzUnpriced")).left.exists(_.contains("has no price")))
    val keyed = unpricedPlan.copy(agents =
      unpricedPlan.agents.copy(providers =
        unpricedPlan.agents.providers.copy(claudeCode = unpricedPlan.agents.providers.claudeCode.copy(useApiKey = true))
      )
    )
    val (k, _) = handlers(Map(Plan -> ScriptedClient(Plan)), keyed)
    val refused = k.runnable(Role.Extractor, None)
    assert(refused.left.exists(e => e.contains("has no price") && e.contains("use an API key")), refused)
    assert(k.startRun(RunRequest("enrich-source", Ck)).isLeft)
    // priced, it runs metered — and draws on no plan pool
    val priced = keyed.copy(agents = keyed.agents.copy(prices = "sonnet=3/15"))
    val (p, _) = handlers(Map(Plan -> ScriptedClient(Plan)), priced)
    assertEquals(p.runnable(Role.Extractor, None), Right(()))
    assertEquals(
      p.models.chains.find(_.role == "extractor").get.models.head.billing,
      Some("price: 3/15 USD per million tokens")
    )
    assertEquals(p.pools.poolOf(Plan), None)

  test("readings: the newest per window wins, an older observation never overwrites it"):
    val (a, _) = handlers(Map.empty)
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 50, None, false, now(), PlanQuota.Source.Run))
    )
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 99, None, false, now() - 60000, PlanQuota.Source.Mod))
    )
    assertEquals(
      PlanQuotaRepo.readings(ds, PoolId.ClaudePlan).map(r => (r.usedPct, r.source)),
      List((50.0, PlanQuota.Source.Run))
    )

  test("the quota-guard report: recorded, and answered with stop once the pool is below its floor"):
    val (a, _) = handlers(Map.empty)
    assertEquals(a.guard(ModReport("mod", List(ModWindow("five_hour", 40, None)))).allow, true)
    val stop = a.guard(
      ModReport("mod", List(ModWindow("five_hour", 80, Some(java.time.Instant.ofEpochMilli(now() + 600000).toString))))
    )
    assert(!stop.allow && stop.reason.exists(_.contains("floor 25%")) && stop.resetsAt.isDefined, stop)

  test("REST: quota, hold, and the guard endpoint takes a RUN TOKEN where writes need the API key"):
    val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    def send(r: HttpRequest.Builder) = {
      val x = http.send(r.build(), HttpResponse.BodyHandlers.ofString()); (x.statusCode, x.body)
    }
    def json(url: String, method: String, body: String, auth: Option[String]) =
      send(
        auth
          .foldLeft(HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json"))((b, t) =>
            b.header("authorization", s"Bearer $t")
          )
          .method(method, HttpRequest.BodyPublishers.ofString(body))
      )
    val keyed = baseCfg.copy(auth = baseCfg.auth.copy(apiKey = "zz-key"), agents = baseCfg.agents.copy(enabled = false))
    @volatile var hs: Option[Handlers] = None // the started server's handlers, for a run token
    ox.supervised {
      val b = Composition
        .server(
          keyed,
          "127.0.0.1",
          0,
          com.histmersion.server.ConverterWiring.client(keyed.converter),
          onHandlers = h => hs = Some(h)
        )
        .start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        val (gs, gb) = send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/quota")))
        assertEquals(gs, 200, gb)
        assert(gb.contains("\"claude-plan\"") && gb.contains("\"chatgpt-plan\""), gb.take(400))
        assertEquals(
          json(s"$url/api/agents/quota/claude-plan", "PUT", """{"hold":true}""", None)._1,
          401,
          "a write needs the key"
        )
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
