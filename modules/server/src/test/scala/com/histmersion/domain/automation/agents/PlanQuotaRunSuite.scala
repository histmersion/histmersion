package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.server.agents.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.PlanQuota.*
import com.histmersion.domain.automation.agents.{AgentRepo, PlanQuotaRepo}
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Composition, Deps, KeyedLoopback, ServerWiring}
import sttp.ai.core.agent.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.*
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.Citekey
import com.histmersion.db.DbSuite
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.CodexConfig
import com.histmersion.config.AgentEnrichConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.config.OpencodeConfig
import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.config.AuthConfig
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.server.ConverterWiring
import com.histmersion.api.Api
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestPermits.given

/** Plan pools end to end against a `*_test` database (docs/research-plan-quotas.md §5): the pick with plan readings — a
  * window below its floor, a hold, a provider refusal — pauses a run with `plan-quota` until the reset instead of
  * falling through to a paid key (unless the role allows it), resumes it once the window resets; the readings repo
  * keeps the newest; the quota-guard endpoint; the REST surface with a run token under an API key. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class PlanQuotaRunSuite extends DbSuite with RunTranscripts:
  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = Citekey("zzplan2019_tin")
  private val ApiTarget = RunRequest.Target(Ck.value)
  private def runId(id: RunInfo.Id): Run.Id = Run.Id.assume(id.value)
  private val Plan = ModelRef(ProviderKind.ClaudeCode, Agents.ModelRef.Model("sonnet"))
  private val Paid = ModelRef(ProviderKind.Anthropic, Agents.ModelRef.Model("zzPaid"))
  private val Free = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("zzFree"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  /** A CODEX_HOME of the suite's own with a login in it (its `auth.json` is never read), so the ChatGPT plan's pool is
    * judged by its readings and refusals, never by the machine running the suite.
    */
  private lazy val codexHome =
    val h = java.nio.file.Files.createTempDirectory("hm-codex-home-")
    java.nio.file.Files.writeString(h.resolve("auth.json"), "{}")
    h

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.server)
      .setTo(b.server.modify(_.host).setTo(NonEmptyVector.of(ServerConfig.Host("127.0.0.1"))))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("sonnet=0/0; zzPaid=3/15; zzFree=0/0"))
          .modify(_.roles)
          .setTo(b.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor(s"${Plan.wire} > ${Paid.wire}")))
          .modify(_.providers)
          .setTo(
            b.agents.providers
              .modify(_.codex)
              .setTo(
                b.agents.providers.codex
                  .modify(_.appServer)
                  .setTo(CodexConfig.AppServer(false))
                  .modify(_.home)
                  .setTo(CodexConfig.Home(codexHome.toString))
              )
          )
      )
      .modify(_.libraryAgents)
      .setTo(
        b.libraryAgents
          .modify(_.enrich)
          .setTo(b.libraryAgents.enrich.modify(_.upstream).setTo(AgentEnrichConfig.Upstream(false)))
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private val Body = Source.OcrMarkdown(
    "# Introduction\n\nTin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.\n"
  )
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

  private def handlers(clients: Map[ModelRef, LlmClient], cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (
      AgentsControllers(
        ServerEngineSite(deps, h),
        (_, ref) => clients.get(ref).toRight(Problem.Unclassified(Legacy.Text(s"no scripted client for ${ref.wire}")))
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

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = api.library.Title("Bronze Age tin"),
            year = (Some(2019)).map(Year.apply),
            body = Some(Body)
          )
        )
        .isRight
    )

  private val now = () => System.currentTimeMillis()
  private def low(resetIn: Long = 3600000L) =
    List(
      Reading(
        PlanQuota.Reading.Limit("claude"),
        PlanQuota.Reading.Kind("five_hour"),
        PlanQuota.Reading.UsedPct(90),
        Some(Reading.ResetsAt(now() + resetIn)),
        rejected = PlanQuota.Reading.Rejected(false),
        Reading.ObservedAt(now()),
        PlanQuota.Source.Run
      ),
      Reading(
        PlanQuota.Reading.Limit("claude"),
        PlanQuota.Reading.Kind("seven_day"),
        PlanQuota.Reading.UsedPct(40),
        Some(Reading.ResetsAt(now() + 5 * 86400000L)),
        rejected = PlanQuota.Reading.Rejected(false),
        Reading.ObservedAt(now()),
        PlanQuota.Source.Run
      )
    )

  private def startAndRun(a: AgentsControllers): RunInfo =
    val run = a
      .startRun(RunRequest(Agents.RunRequest.Pipeline("enrich-source"), ApiTarget))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(runId(run.id))
    connect(ds)(AgentRepo.get(run.id)).get

  test("a plan below its floor PAUSES the run (plan-quota) until the reset — never falls through to the paid key"):
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid))
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid)); seed(h)
    a.pools.record(PoolId.ClaudePlan, low())
    val run = startAndRun(a)
    assertEquals(run.status, "paused", run.error)
    assert(run.error.exists(_.sentence.startsWith("plan-quota:")), run.error)
    assert(run.error.exists(_.sentence.contains("five_hour: 10% left, floor 25%")), run.error)
    assert(run.error.exists(_.sentence.contains("does not fall from a plan to a paid key")), run.error)
    assert(run.resumeAt.isDefined, "it resumes by itself at the reset")
    assertEquals((plan.steps.size, paid.steps.size), (0, 0), "nothing was called, nothing paid")
    // the window resets: the run goes on, on the plan
    connect(ds)(sql"UPDATE plan_quota_readings SET resets_at = now() - interval '1 second'".update.run())
    connect(ds)(sql"UPDATE jobs SET resume_at = now() - interval '1 second' WHERE id = ${run.id}".update.run())
    enrichScripts(plan)
    assertEquals(a.engine.resumeDue(java.time.Instant.now()).map(_.value: String), List(run.id))
    a.engine.execute(runId(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    assertEquals(paid.steps.size, 0)

  // 2026-10-08: the Claude plan at its five_hour floor and the ChatGPT plan HELD paused runs with "no
  // window frees by itself" and no resume time, so they waited for a manual resume past the reset
  test("a held pool later in the chain: the run still resumes by itself at the other pool's reset"):
    val Gpt = ModelRef(ProviderKind.Opencode, Agents.ModelRef.Model("openai/zzGpt"))
    val cfg = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.roles)
          .setTo(
            baseCfg.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor(s"${Plan.wire} > ${Gpt.wire}"))
          )
          .modify(_.providers)
          .setTo(
            baseCfg.agents.providers
              .modify(_.opencode)
              .setTo(baseCfg.agents.providers.opencode.modify(_.chatgptLogin).setTo(OpencodeConfig.ChatgptLogin(true)))
          )
      )
    val (plan, gpt) = (ScriptedClient(Plan), ScriptedClient(Gpt))
    val (a, h) = handlers(Map(Plan -> plan, Gpt -> gpt), cfg); seed(h)
    assertEquals(a.pools.poolOf(Gpt), Some(PoolId.ChatgptPlan))
    a.pools.record(PoolId.ClaudePlan, low())
    a.setPool(PlanQuota.PoolIdText("chatgpt-plan"), PoolSet(hold = Some(PlanQuota.PoolSet.Hold(true))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val reset =
      Transactor(ds)
        .read(PlanQuotaRepo.readings(PoolId.ClaudePlan))
        .find(_.kind.value == "five_hour")
        .flatMap(_.resetsAt)
        .get
    val run = startAndRun(a)
    assertEquals(run.status, "paused", run.error)
    assert(run.error.exists(_.sentence.startsWith("plan-quota:")), run.error)
    assert(!run.error.exists(_.sentence.contains("no window frees by itself")), run.error)
    assert(
      run.error.exists(_.sentence.contains(s"next reset at ${java.time.Instant.ofEpochMilli(reset.value)}")),
      run.error
    )
    assert(run.error.exists(_.sentence.contains("the ChatGPT plan is held")), run.error)
    assertEquals(
      run.resumeAt.map(t => java.time.Instant.parse(t.value).toEpochMilli / 1000),
      Some(reset.value / 1000),
      "it resumes by itself at the Claude plan's reset"
    )
    assertEquals(a.engine.resumeDue(java.time.Instant.ofEpochMilli(reset.value - 60000)), Nil, "not before the reset")
    // the window resets: the resume check re-queues the run, which goes on on the Claude plan
    connect(ds)(
      sql"UPDATE plan_quota_readings SET resets_at = now() - interval '1 second' WHERE kind = 'five_hour'".update.run()
    )
    enrichScripts(plan)
    assertEquals(
      a.engine.resumeDue(java.time.Instant.ofEpochMilli(reset.value + 1000)).map(_.value: String),
      List(run.id)
    )
    a.engine.execute(runId(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    assertEquals(gpt.steps.size, 0, "the held pool was never drawn on")

  test(
    "with fallthrough on for the role, the chain falls from the plan to the paid key; a free local model never needs it"
  ):
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid)); enrichScripts(paid)
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid)); seed(h)
    a.pools.record(PoolId.ClaudePlan, low())
    assert(
      a.setFallthrough(Agents.Role.Wire("extractor"), FallthroughSet(PlanQuota.FallthroughSet.Fallthrough(true)))
        .left
        .map(Api.Error(_))
        .exists(_.fallthrough == List(PlanQuota.QuotaInfo.Fallthrough("extractor")))
    )
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    assertEquals((plan.steps.size, paid.steps.size > 0), (0, true))
    // a free model after the plan is used whatever fallthrough says
    assert(
      a.setFallthrough(Agents.Role.Wire("extractor"), FallthroughSet(PlanQuota.FallthroughSet.Fallthrough(false)))
        .left
        .map(Api.Error(_))
        .isRight
    )
    val (p2, free) = (ScriptedClient(Plan), ScriptedClient(Free)); enrichScripts(free)
    val cfg = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.roles)
          .setTo(
            baseCfg.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor(s"${Plan.wire} > ${Free.wire}"))
          )
      )
    val (a2, _) = handlers(Map(Plan -> p2, Free -> free), cfg)
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzplan%'".update.run())
    assertEquals(startAndRun(a2).status, "done")

  test("the hold switch, a provider refusal, a stale reading: the pick's view; floors saved over config"):
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)))
    def planUsable = a.models.chains.find(_.role.value == "extractor").get.models.head.usable.value
    assert(planUsable, "no reading: unknown, usable")
    a.setPool(PlanQuota.PoolIdText("claude-plan"), PoolSet(hold = Some(PlanQuota.PoolSet.Hold(true))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), st => assert(st.held.value && !st.usable.value, st))
    assert(!planUsable)
    assertEquals(
      a.picker.pick(Role.Extractor, None).left.toOption.collect { case PickFailure.Exhausted(_, at, r) => (at, r) },
      Some((None, PauseReason.PlanQuota)),
      "held: no time it frees by itself"
    )
    a.setPool(PlanQuota.PoolIdText("claude-plan"), PoolSet(hold = Some(PlanQuota.PoolSet.Hold(false))))
      .left
      .map(Api.Error(_))
    assert(planUsable)
    // a reading old enough to be stale lets a run go on only with the margin
    a.pools.record(
      PoolId.ClaudePlan,
      List(
        Reading(
          PlanQuota.Reading.Limit("claude"),
          PlanQuota.Reading.Kind("five_hour"),
          PlanQuota.Reading.UsedPct(70),
          Some(Reading.ResetsAt(now() + 3600000L)),
          PlanQuota.Reading.Rejected(false),
          Reading.ObservedAt(now() - 3600000L),
          PlanQuota.Source.Run
        )
      )
    )
    assert(!planUsable, "30% left, floor 25%, but stale: needs 35%")
    // floors saved over config
    val st = a
      .setPool(PlanQuota.PoolIdText("claude-plan"), PoolSet(floors = Some(PlanQuota.PoolSet.Floors("five_hour=10"))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((st.floors, st.floorsSource), ("five_hour=10", "saved"))
    assert(planUsable, "30% left ≥ 10 + 10")
    assert(
      a.setPool(PlanQuota.PoolIdText("claude-plan"), PoolSet(floors = Some(PlanQuota.PoolSet.Floors("five_hour=x"))))
        .left
        .map(Api.Error(_))
        .isLeft
    )
    assertEquals(
      a.setPool(PlanQuota.PoolIdText("claude-plan"), PoolSet(floors = Some(PlanQuota.PoolSet.Floors(""))))
        .left
        .map(Api.Error(_))
        .map(_.floorsSource),
      Right("config")
    )
    assert(
      a.setPool(PlanQuota.PoolIdText("nobody-plan"), PoolSet(hold = Some(PlanQuota.PoolSet.Hold(true))))
        .left
        .map(Api.Error(_))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown pool"))
    )
    // the ChatGPT plan refused by the provider
    Transactor(ds).write(
      PlanQuotaRepo.setRefusal(
        PoolId.ChatgptPlan,
        Some(Problem.Unclassified(Legacy.Text("ordinary usage not allowed"))),
        None,
        Some(PlanQuotaRepo.Pool.PlanType("prolite"))
      )
    )
    val q = a.quota
    val gpt = q.plans.find(_.pool.value == "chatgpt-plan").get
    assert(
      !gpt.usable.value && gpt.reason.map(w => s"$w").contains("ordinary usage not allowed") && gpt.planType.contains(
        "prolite"
      ),
      gpt
    )
    assertEquals(q.plans.find(_.pool.value == "claude-plan").map(_.covers), Some(List("claude-code:*")))

  // #913: a plan model needs no price — before, an unpriced claude-code model was "not ready"
  private lazy val unpricedPlan = baseCfg
    .modify(_.agents)
    .setTo(
      baseCfg.agents
        .modify(_.prices)
        .setTo(AgentsConfig.Prices("zzPaid=3/15"))
        .modify(_.roles)
        .setTo(baseCfg.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor(Plan.wire.value)))
    )

  test("a plan model with NO price is ready, costs 0 USD, and its run is still budgeted by tokens"):
    val plan = ScriptedClient(Plan); enrichScripts(plan)
    val (a, h) = handlers(Map(Plan -> plan), unpricedPlan); seed(h)
    assertEquals(a.runnable(Role.Extractor, None), Right(()))
    val entry = a.models.chains.find(_.role.value == "extractor").get.models.head
    assert(entry.ready.value && entry.usable.value, entry)
    assertEquals(entry.billing, Some("plan pool: claude-plan"))
    val doc = AgentDoctor(
      baseCfg.modify(_.agents).setTo(unpricedPlan.agents),
      a.picker,
      m => a.resolve(m)
    ).report(online = Agents.DoctorReport.Online(false))
    val check =
      doc.providers.find(_.provider.value == "claude-code").get.checks.find(_.name.value == s"model ${Plan.wire}").get
    assert(check.level == CheckLevel.Ok && check.detail.value.startsWith("plan pool: claude-plan"), check)
    // a micro USD budget is never reached (plan calls cost 0 USD), the token budget is: 1100 tokens a
    // call, 1500 allowed → two calls, then the run pauses on tokens
    val run = a
      .startRun(
        RunRequest(
          Agents.RunRequest.Pipeline("enrich-source"),
          ApiTarget,
          budgetUsd = Some(RunRequest.BudgetUsd(0.000001)),
          budgetTokens = Some(RunRequest.BudgetTokens(1500L))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(runId(run.id))
    val r = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(r.status, "paused", r.error)
    assert(r.error.map(_.sentence).exists(e => e.startsWith("budget:") && e.contains("tokens")), r.error)
    assertEquals((r.costUsd, r.usage.total), (0.0, 2200L))
    // with room, it finishes on the plan for 0 USD
    a.control(runId(run.id), Agents.Action("resume"), None, Some(AgentsControllers.BudgetTokens(100000L)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(runId(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals((done.status, done.costUsd), ("done", 0.0), done.error)

  test("a METERED unpriced model is still refused; claude-code set to use an API key needs a price"):
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), unpricedPlan)
    assert(
      a.runnable(Role.Extractor, Some(AgentsControllers.ModelName("anthropic:zzUnpriced")))
        .left
        .exists(_.sentences.mkString("; ").contains("has no price"))
    )
    val keyed = unpricedPlan
      .modify(_.agents)
      .setTo(
        unpricedPlan.agents
          .modify(_.providers)
          .setTo(
            unpricedPlan.agents.providers
              .modify(_.claudeCode)
              .setTo(
                unpricedPlan.agents.providers.claudeCode.modify(_.useApiKey).setTo(ClaudeCodeConfig.UseApiKey(true))
              )
          )
      )
    val (k, _) = handlers(Map(Plan -> ScriptedClient(Plan)), keyed)
    val refused = k.runnable(Role.Extractor, None)
    assert(
      refused.left
        .map(_.sentences.mkString("; "))
        .left
        .exists(e => e.contains("has no price") && e.contains("use an API key")),
      refused
    )
    assert(k.startRun(RunRequest(Agents.RunRequest.Pipeline("enrich-source"), ApiTarget)).isLeft)
    // priced, it runs metered — and draws on no plan pool
    val priced = keyed.modify(_.agents).setTo(keyed.agents.modify(_.prices).setTo(AgentsConfig.Prices("sonnet=3/15")))
    val (p, _) = handlers(Map(Plan -> ScriptedClient(Plan)), priced)
    assertEquals(p.runnable(Role.Extractor, None), Right(()))
    assertEquals(
      p.models.chains.find(_.role.value == "extractor").get.models.head.billing,
      Some("price: 3/15 USD per million tokens")
    )
    assertEquals(p.pools.poolOf(Plan), None)

  test("readings: the newest per window wins, an older observation never overwrites it"):
    val (a, _) = handlers(Map.empty)
    a.pools.record(
      PoolId.ClaudePlan,
      List(
        Reading(
          PlanQuota.Reading.Limit("claude"),
          PlanQuota.Reading.Kind("five_hour"),
          PlanQuota.Reading.UsedPct(50),
          None,
          PlanQuota.Reading.Rejected(false),
          Reading.ObservedAt(now()),
          PlanQuota.Source.Run
        )
      )
    )
    a.pools.record(
      PoolId.ClaudePlan,
      List(
        Reading(
          PlanQuota.Reading.Limit("claude"),
          PlanQuota.Reading.Kind("five_hour"),
          PlanQuota.Reading.UsedPct(99),
          None,
          PlanQuota.Reading.Rejected(false),
          Reading.ObservedAt(now() - 60000),
          PlanQuota.Source.Mod
        )
      )
    )
    assertEquals(
      Transactor(ds).read(PlanQuotaRepo.readings(PoolId.ClaudePlan)).map(r => (r.usedPct, r.source)),
      List((50.0, PlanQuota.Source.Run))
    )

  test("the quota-guard report: recorded, and answered with stop once the pool is below its floor"):
    val (a, _) = handlers(Map.empty)
    assertEquals(
      a.guard(
        ModReport(
          PlanQuota.ModReport.Source("mod"),
          List(ModWindow(PlanQuota.ModWindow.Kind("five_hour"), PlanQuota.ModWindow.PercentUsed(40), None))
        )
      ).allow,
      true
    )
    val stop = a.guard(
      ModReport(
        PlanQuota.ModReport.Source("mod"),
        List(
          ModWindow(
            PlanQuota.ModWindow.Kind("five_hour"),
            PlanQuota.ModWindow.PercentUsed(80),
            Some(ModWindow.ResetsAt(java.time.Instant.ofEpochMilli(now() + 600000).toString))
          )
        )
      )
    )
    assert(!stop.allow.value && stop.reason.exists(w => s"$w".contains("floor 25%")) && stop.resetsAt.isDefined, stop)

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
    val keyed = baseCfg
      .modify(_.auth)
      .setTo(KeyedLoopback(baseCfg.auth, "zz-key"))
      .modify(_.agents)
      .setTo(baseCfg.agents.modify(_.enabled).setTo(AgentsConfig.Enabled(false)))
    @volatile var hs: Option[ServerWiring] = None // the started server's handlers, for a run token
    ox.supervised {
      val b = Composition
        .server(
          keyed,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          ConverterWiring.client(keyed.converter),
          onWiring = h => hs = Some(h)
        )
        .start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        assertEquals(send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/quota")))._1, 401, "a read needs it too")
        val (gs, gb) =
          send(HttpRequest.newBuilder(URI.create(s"$url/api/agents/quota")).header("authorization", "Bearer zz-key"))
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
        val tok = hs.get.agents.tokens.issue(skeptic, None, RunTokens.TtlMillis(60000)).token
        val (rs, rb) = json(s"$url/api/agents/quota/claude", "POST", report, Some(tok.value))
        assertEquals(rs, 200, rb)
        assert(rb.contains("\"allow\":false") && rb.contains("held"), rb)
        // a run token is no API key anywhere else
        assertEquals(json(s"$url/api/agents/quota/claude-plan", "PUT", """{"hold":false}""", Some(tok.value))._1, 401)
      finally b.stop()
    }
