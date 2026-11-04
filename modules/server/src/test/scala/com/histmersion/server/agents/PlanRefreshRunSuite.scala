package com.histmersion.server.agents

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.PlanQuota.*
import com.histmersion.domain.automation.agents.{AgentRepo, PlanQuotaRepo}
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Composition, Deps, ServerWiring}
import io.circe.parser
import sttp.ai.core.agent.*

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import com.histmersion.db.*
import com.histmersion.api.automation.{AgentsMarkdown, PlanQuota}
import com.histmersion.api.library.Source

/** #989 against a `*_test` database: a pool paused only because its reading is stale takes a fresh one before it pauses
  * (the Claude probe, the ChatGPT app-server read) — once per stale-minutes, one at a time; a run paused on such a pool
  * is resumed by the allowance check once a fresh reading shows headroom; a pool whose runtime has no login is
  * unavailable and never offered; a pool with no reading is read before its first use; `agent quota refresh` over HTTP
  * against a FAKE `claude`. Every CLI is a stand-in: nothing is spent. Skipped without HISTMERSION_TEST_DB_URL.
  */
class PlanRefreshRunSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = "zzrefresh2019_tin"
  private val Plan = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val Paid = ModelRef(ProviderKind.Anthropic, "zzPaid")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(
      _.db).setTo(b.db.modify(_.url).setTo(testDb.get)).modify(_.server).setTo(b.server.modify(_.host).setTo("127.0.0.1")).modify(_.agents).setTo(b.agents.modify(
        _.enabled).setTo(true).modify(_.prices).setTo("zzPaid=3/15").modify(_.roles).setTo(b.agents.roles.modify(_.extractor).setTo(s"${Plan.wire} > ${Paid.wire}")).modify(_.providers).setTo(b.agents.providers.modify(_.codex).setTo(b.agents.providers.codex.modify(_.appServer).setTo(false))
      )).modify(_.libraryAgents).setTo(b.libraryAgents.modify(_.enrich).setTo(b.libraryAgents.enrich.modify(_.upstream).setTo(false))
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private val now = () => System.currentTimeMillis()
  private val Min = 60000L

  /** A `rate_limit_event` with these shares used, resetting in an hour / five days. */
  private def event(fiveUsed: Double, sevenUsed: Double): String =
    val (r5, r7) = ((now() + 3600000L) / 1000, (now() + 5 * 86400000L) / 1000)
    s"""{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":$r5,"rateLimitType":"five_hour",
       |"unifiedWindows":{"five_hour":{"utilization":${fiveUsed / 100},"resetsAt":$r5},
       |"seven_day":{"utilization":${sevenUsed / 100},"resetsAt":$r7}}},"uuid":"u","session_id":"s"}""".stripMargin
      .replace("\n", "")
  private val Result = """{"type":"result","subtype":"success","is_error":false,"result":"Quota.","num_turns":1}"""

  /** The CLIs as stand-ins: what the probe prints, what `claude auth status` says, the app-server's answer — and how
    * often each was asked. `gate` holds a probe until released (single flight).
    */
  final class Reads:
    val probes = AtomicInteger(0)
    val codexReads = AtomicInteger(0)
    @volatile var probeOut: List[String] = Nil
    @volatile var probeArgs: List[String] = Nil
    @volatile var auth: String = """{"loggedIn":true,"authMethod":"claude.ai","subscriptionType":"max"}"""
    @volatile var codex: Either[String, io.circe.Json] = Left("no app-server here")
    @volatile var hold: Option[(CountDownLatch, CountDownLatch)] = None
    def seams: PlanPools.Seams = PlanPools.Seams(
      (_, _, _) => { codexReads.incrementAndGet(); codex },
      (argv, _, stdin, _) =>
        assertEquals(stdin, ClaudeCode.ProbePrompt)
        probes.incrementAndGet()
        probeArgs = argv
        hold.foreach { (in, out) => in.countDown(); out.await(10, TimeUnit.SECONDS) }
        Right(CliProcess.Run(probeOut, 0, "", None))
      ,
      (_, _, _) => Right((0, auth))
    )

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

  private def handlers(
      clients: Map[ModelRef, LlmClient],
      reads: Reads,
      cfg: AppConfig = baseCfg
  ): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (
      AgentsControllers(
        ServerEngineSite(deps, h),
        (_, ref) => clients.get(ref).toRight(s"no scripted client for ${ref.wire}"),
        CliRuntime(cfg, EngineContributions.roster),
        reads.seams
      ),
      h
    )

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzrefresh%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzrefresh%'".update.run()
      sql"DELETE FROM plan_quota_readings".update.run()
      sql"DELETE FROM plan_quota_refreshes".update.run()
      sql"DELETE FROM plan_pools".update.run()
      sql"DELETE FROM agent_role_fallthrough".update.run()
      sql"DELETE FROM agent_role_chains".update.run()
      sql"DELETE FROM agent_allowances".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources.put(Ck, Source.Put(title = "Bronze Age tin", year = Some(2019), body = Some(Body))).isRight
    )

  /** 30% left of the 5-hour window (floor 25%), read an hour ago: stale, so it needs 35% — paused ONLY on its age.
    */
  private def staleReading(): Unit =
    Transactor(ds).transact(
      PlanQuotaRepo.record(
        PoolId.ClaudePlan,
        List(
          Reading(
            "claude",
            "five_hour",
            70,
            Some(now() + 3 * 3600000L),
            rejected = false,
            now() - 60 * Min,
            PlanQuota.Source.Run
          )
        )
      )
    )

  private def startAndRun(a: AgentsControllers): RunInfo =
    val run = a.startRun(RunRequest("enrich-source", Ck)).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    connect(ds)(AgentRepo.get(run.id)).get

  test("#989: a run wanting a pool paused only on a STALE reading takes a fresh one first, and goes on"):
    val reads = Reads(); reads.probeOut = List(event(20, 30), Result)
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid)); enrichScripts(plan)
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid), reads); seed(h)
    staleReading()
    assert(
      !a.models.chains.find(_.role == "extractor").get.models.head.usable,
      "looking does not refresh: paused on the stale reading"
    )
    assertEquals(reads.probes.get(), 0)
    val run = startAndRun(a)
    assertEquals(run.status, "done", run.error)
    assertEquals(reads.probes.get(), 1, "one probe for the whole run: the reading is fresh afterwards")
    assertEquals(paid.steps.size, 0)
    assert(
      reads.probeArgs.containsSlice(List("--max-turns", "1")) && reads.probeArgs.containsSlice(
        List("--model", "haiku")
      ),
      reads.probeArgs
    )
    assert(
      run.warnings.exists(w =>
        w.contains("extractor: Claude plan: fresh reading (probe: five_hour 20% used, seven_day 30% used)")
      ),
      run.warnings
    )
    val pool = a.quota.plans.find(_.pool == "claude-plan").get
    assertEquals(
      (pool.refreshesToday, pool.lastRefresh.map(r => (r.trigger, r.mode, r.ok))),
      (1, Some(("pick", "probe", true)))
    )
    assert(pool.windows.forall(w => w.source == "probe" && !w.stale), pool.windows)
    assert(AgentsMarkdown.pool(pool).contains("refreshed once today"), AgentsMarkdown.pool(pool))

  test("#989: below its floor on a FRESH reading the run pauses with no refresh; a hold is never refreshed away"):
    val reads = Reads(); reads.probeOut = List(event(1, 1), Result)
    val (a, h) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads); seed(h)
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 90, Some(now() + 3600000L), false, now(), PlanQuota.Source.Run))
    )
    val run = startAndRun(a)
    assert(run.status == "paused" && run.error.exists(_.startsWith("plan-quota:")), run.error)
    a.setPool("claude-plan", PoolSet(hold = Some(true)))
    connect(ds)(sql"UPDATE plan_quota_readings SET observed_at = now() - interval '1 hour'".update.run())
    assert(a.picker.pick(Role.Extractor, None).isLeft)
    assertEquals(reads.probes.get(), 0, "a fresh reading would lift neither")

  test("#989: the allowance check resumes a run paused on a stale reading once a fresh one shows headroom (throttled)"):
    val reads = Reads() // the first probe says nothing about the plan: the run pauses on the stale reading
    val (plan, paid) = (ScriptedClient(Plan), ScriptedClient(Paid))
    val (a, h) = handlers(Map(Plan -> plan, Paid -> paid), reads); seed(h)
    staleReading()
    val run = startAndRun(a)
    assertEquals(run.status, "paused", run.error)
    assert(run.error.exists(e => e.startsWith("plan-quota:") && e.contains("stale")), run.error)
    assert(
      run.resumeAt.exists(_ > java.time.Instant.now().plusSeconds(3600).toString),
      s"it waits for the reset: ${run.resumeAt}"
    )
    assertEquals(reads.probes.get(), 1)
    assertEquals(
      Transactor(ds).read(PlanQuotaRepo.refreshes(PoolId.ClaudePlan, 0))._2.map(r => (r.ok, r.mode)),
      Some((false, "probe"))
    )
    // the check a minute later: still within stale-minutes of the last attempt — no second probe, still paused
    reads.probeOut = List(event(20, 30), Result)
    val soon = java.time.Instant.now().plusSeconds(60)
    assertEquals(a.engine.resumeFreed(soon), Nil)
    assertEquals(reads.probes.get(), 1, "throttled to once per stale-minutes")
    // after stale-minutes: the check takes a fresh reading, sees headroom, and queues the run
    val later = java.time.Instant.now().plusSeconds(11 * 60)
    assertEquals(a.engine.resumeFreed(later), List(run.id))
    assertEquals(reads.probes.get(), 2)
    val queued = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(queued.status, "queued")
    assert(queued.warnings.exists(_.contains("resumed before its reset")), queued.warnings)
    assert(queued.warnings.exists(_.contains("fresh reading (probe: five_hour 20% used")), queued.warnings)
    enrichScripts(plan)
    a.engine.execute(run.id)
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    assertEquals(paid.steps.size, 0)
    assertEquals(Transactor(ds).read(PlanQuotaRepo.refreshes(PoolId.ClaudePlan, 0))._1, 2)

  test("#989: a run paused on a pool still below its floor stays paused; nothing is read without such a run"):
    val reads = Reads(); reads.probeOut = List(event(95, 30), Result)
    val (a, h) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads); seed(h)
    assertEquals(a.engine.resumeFreed(java.time.Instant.now()), Nil)
    assertEquals(reads.probes.get(), 0, "an idle server reads nothing")
    staleReading()
    val run = startAndRun(a)
    assertEquals(run.status, "paused", "the fresh reading shows 5% left: below the floor")
    assertEquals(reads.probes.get(), 1)
    assertEquals(a.engine.resumeFreed(java.time.Instant.now().plusSeconds(11 * 60)), Nil)
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.status, "paused")

  test("#989: no reading yet — the pool is read before its first use"):
    val reads = Reads(); reads.probeOut = List(event(10, 10), Result)
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads)
    val picked = a.picker.pick(Role.Extractor, None).fold(f => fail(f.toString), identity)
    assertEquals(picked.client.model, Plan)
    assertEquals(reads.probes.get(), 1)
    assertEquals(
      Transactor(ds).read(PlanQuotaRepo.readings(PoolId.ClaudePlan)).map(r => (r.kind, r.source)).toSet,
      Set(("five_hour", PlanQuota.Source.Probe), ("seven_day", PlanQuota.Source.Probe))
    )
    // refresh off: today's behaviour — usable, unknown, nothing read
    connect(ds)(sql"DELETE FROM plan_quota_readings".update.run())
    val off = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.quota).setTo(baseCfg.agents.quota.modify(_.claudePlan).setTo(baseCfg.agents.quota.claudePlan.modify(_.refresh).setTo("off"))
      )
    )
    val reads2 = Reads()
    val (b, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads2, off)
    assertEquals(b.picker.pick(Role.Extractor, None).map(_.client.model), Right(Plan))
    assertEquals(reads2.probes.get(), 0)
    assertEquals(b.quota.plans.find(_.pool == "claude-plan").get.refresh, None)

  test(
    "#4035: on its schedule a pool is read while the autopilot is on and work wants it, once per interval; a run's reading counts"
  ):
    val reads = Reads(); reads.probeOut = List(event(20, 30), Result)
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads)
    val at = java.time.Instant.now()
    def onSchedule(autopilotOn: Boolean = true, wanted: Boolean = true, when: java.time.Instant = at) =
      a.pools.refreshOnSchedule(PoolId.ClaudePlan, autopilotOn, wanted, when)
    assertEquals(onSchedule(autopilotOn = false), Left("the autopilot is off"))
    assertEquals(onSchedule(wanted = false), Left("no queued work wants the pool"))
    assertEquals(reads.probes.get(), 0)
    assertEquals(onSchedule().map(_.ok), Right(true))
    assertEquals(reads.probes.get(), 1)
    assert(reads.probeArgs.containsSlice(List("--model", "haiku")), reads.probeArgs)
    val pool = a.quota.plans.find(_.pool == "claude-plan").get
    assertEquals(pool.lastRefresh.map(r => (r.trigger, r.mode, r.ok)), Some(("schedule", "probe", true)))
    assert(pool.refresh.exists(_.contains("every 180 min while the autopilot has queued work for it")), pool.refresh)
    assert(onSchedule(when = at.plusSeconds(179 * 60)).left.exists(_.startsWith("read 179 min ago")))
    assertEquals(onSchedule(when = at.plusSeconds(181 * 60)).map(_.ok), Right(true))
    assertEquals(reads.probes.get(), 2)
    // a reading a run recorded since counts like the schedule's own
    Transactor(ds).transact(
      PlanQuotaRepo.record(
        PoolId.ClaudePlan,
        List(
          Reading(
            "claude",
            "five_hour",
            40,
            Some(now() + 3600000L),
            rejected = false,
            at.plusSeconds(300 * 60).toEpochMilli,
            PlanQuota.Source.Run
          )
        )
      )
    )
    assert(onSchedule(when = at.plusSeconds(330 * 60)).left.exists(_.startsWith("read 30 min ago")))
    assertEquals(reads.probes.get(), 2)

  test(
    "#4035: no scheduled read without a login, with the schedule off, or before the scheduler has left work waiting"
  ):
    val reads = Reads(); reads.probeOut = List(event(20, 30), Result)
    reads.auth = """{"loggedIn":false}"""
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads)
    assert(
      a.pools
        .refreshOnSchedule(PoolId.ClaudePlan, autopilotOn = true, wanted = true, java.time.Instant.now())
        .left
        .exists(_.startsWith("no login"))
    )
    val off = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.quota).setTo(baseCfg.agents.quota.modify(
          _.claudePlan).setTo(baseCfg.agents.quota.claudePlan.modify(_.refreshEveryMinutes).setTo(ScheduledRefresh.Off)
        )
      )
    )
    val (b, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), Reads(), off)
    assertEquals(
      b.pools.refreshOnSchedule(PoolId.ClaudePlan, autopilotOn = true, wanted = true, java.time.Instant.now()),
      Left("the schedule is off")
    )
    val reads3 = Reads(); reads3.probeOut = List(event(20, 30), Result)
    val (c, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads3)
    try
      assert(
        transact(ds)(AutopilotRepo.set(enabled = true, "zzrefresh", Nil)) || transact(ds)(AutopilotRepo.get()).enabled
      )
      assertEquals(c.scheduler.refreshPlans(java.time.Instant.now()), Nil, "no pass yet: no work waits on a pool")
      assertEquals(reads.probes.get() + reads3.probes.get(), 0)
    finally { val _ = transact(ds)(AutopilotRepo.set(enabled = false, "zzrefresh", Nil)) }

  test("#1827: a model parked after its plan's limit comes back once a reading taken SINCE says the pool is usable"):
    val reads = Reads(); reads.probeOut = List(event(0, 0), Result)
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan), Paid -> ScriptedClient(Paid)), reads)
    // a fresh reading with headroom from BEFORE the limit was reported: it says nothing about a reset
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 10, Some(now() + 3600000L), false, now() - Min, PlanQuota.Source.Run))
    )
    val since = now()
    val until = a.picker.cool(Plan, Some(java.time.Instant.ofEpochMilli(since + 6 * 86400000L)), since)
    val head = a.models.chains.find(_.role == "extractor").get.models.head
    assert(!head.usable && head.cooling && head.problem.exists(_.contains(ModelPicker.CoolingWhy)), head)
    val listed = AgentsMarkdown.roleChain(a.models.chains.find(_.role == "extractor").get)
    assert(listed.contains("cooling down: its plan reported its usage limit"), listed)
    assertEquals(a.quota.cooldowns.map(c => (c.model, c.until)), List((Plan.wire, until.toString)))
    assert(AgentsMarkdown.quota(a.quota).contains("## Cooling down"), AgentsMarkdown.quota(a.quota))
    assert(a.picker.pick(Role.Extractor, None).isLeft, "still parked: and no fall to the paid key")
    // a reading taken since, still below its floor, keeps it parked
    Thread.sleep(5)
    a.pools.record(
      PoolId.ClaudePlan,
      List(Reading("claude", "five_hour", 99, Some(now() + 3600000L), false, now(), PlanQuota.Source.Run))
    )
    assert(a.models.chains.find(_.role == "extractor").get.models.head.cooling)
    // the person reset the plan: `agent quota refresh claude-plan` reads 0% used — the model is back
    Thread.sleep(5)
    val refreshed = a.refreshQuota(QuotaRefresh(Some("claude-plan"))).fold(e => fail(e.message), identity)
    assertEquals(refreshed.lifted, List(Plan.wire))
    assert(AgentsMarkdown.refreshed(refreshed).contains("Back in use"), AgentsMarkdown.refreshed(refreshed))
    assertEquals(a.quota.cooldowns, Nil)
    assertEquals(a.picker.pick(Role.Extractor, None).map(_.client.model), Right(Plan))

  test("#989: single flight — concurrent picks wanting the same pool take ONE fresh reading"):
    val reads = Reads(); reads.probeOut = List(event(10, 10), Result)
    val (in, out) = (CountDownLatch(1), CountDownLatch(1))
    reads.hold = Some((in, out))
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan)), reads)
    val first = java.util.concurrent.CompletableFuture.supplyAsync(() =>
      a.pools.decide(PoolId.ClaudePlan, None, RefreshTrigger.Pick)
    )
    assert(in.await(10, TimeUnit.SECONDS), "the first probe started")
    val second = java.util.concurrent.CompletableFuture.supplyAsync(() =>
      a.pools.decide(PoolId.ClaudePlan, None, RefreshTrigger.Pick)
    )
    Thread.sleep(300)
    assert(!second.isDone, "the second waits for the first")
    out.countDown()
    val (v1, _, r1) = first.get(10, TimeUnit.SECONDS)
    val (v2, _, r2) = second.get(10, TimeUnit.SECONDS)
    assertEquals((v1.usable, r1.map(_.ok)), (true, Some(true)))
    assertEquals((v2.usable, r2), (true, None), "the second used the first one's reading")
    assertEquals(reads.probes.get(), 1)

  test("#989: no login — the pool is unavailable and its models never offered (and nothing is probed)"):
    val reads = Reads(); reads.auth = """{"loggedIn":false}"""; reads.probeOut = List(event(10, 10), Result)
    val only = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.roles).setTo(baseCfg.agents.roles.modify(_.extractor).setTo(Plan.wire)))
    val (a, _) = handlers(Map(Plan -> ScriptedClient(Plan)), reads, only)
    a.picker.pick(Role.Extractor, None) match
      case Left(PickFailure.Unavailable(m)) => assert(m.contains("unavailable: no login"), m)
      case other                            => fail(s"not unavailable: $other")
    assertEquals(reads.probes.get(), 0)
    val pool = a.quota.plans.find(_.pool == "claude-plan").get
    assert(!pool.available && !pool.usable && pool.reason.exists(_.startsWith("unavailable: no login")), pool)
    assert(AgentsMarkdown.pool(pool).contains("**unavailable: no login"), AgentsMarkdown.pool(pool))
    val entry = a.models.chains.find(_.role == "extractor").get.models.head
    assert(!entry.usable && entry.problem.exists(_.contains("no login")), entry)
    val forced = a.refreshQuota(QuotaRefresh(Some("claude-plan"))).fold(e => fail(e.message), identity)
    assert(!forced.pools.head.refreshed && forced.pools.head.detail.contains("no login"), forced)
    assertEquals(reads.probes.get(), 0)
    // logged in with an API key where the plan is wanted is no plan login either
    reads.auth = """{"loggedIn":true,"authMethod":"api_key"}"""
    assert(a.pools.login(PoolId.ClaudePlan, force = true).isInstanceOf[Login.Missing])

  test("#989: the ChatGPT plan — a stale reading is refreshed through the app-server; no codex login is unavailable"):
    val home = Files.createTempDirectory("hm-codex-refresh-")
    val reads = Reads()
    reads.codex = parser
      .parse(s"""{"rateLimits":{"limitId":"codex","primary":{"usedPercent":5,"windowDurationMins":300,
      |"resetsAt":${(now() + 3600000L) / 1000}},"secondary":{"usedPercent":10,"windowDurationMins":10080,"resetsAt":${(now() + 86400000L) / 1000}},
      |"planType":"plus","rateLimitReachedType":null}}""".stripMargin)
      .left
      .map(_.getMessage)
    // the polling every poll-seconds never comes around in this test: only the refresh reads
    val cfg = baseCfg.agents.modify(
      _.quota).setTo(baseCfg.agents.quota.modify(_.pollSeconds).setTo(2000000000)).modify(_.providers).setTo(baseCfg.agents.providers.modify(_.codex).setTo(baseCfg.agents.providers.codex.modify(_.appServer).setTo(true))
    )
    val pools = PlanPools(ds, cfg, () => Some(home), reads.seams)
    Transactor(ds).transact(
      PlanQuotaRepo.record(
        PoolId.ChatgptPlan,
        List(
          Reading(
            "codex",
            "300m",
            72,
            Some(now() + 3600000L),
            rejected = false,
            now() - 60 * Min,
            PlanQuota.Source.AppServer
          )
        )
      )
    )
    // no login in the runs' CODEX_HOME: unavailable, not read
    assert(pools.unavailable(ModelRef(ProviderKind.Codex, "gpt-5")).exists(_.contains("codex login")))
    assertEquals(pools.decide(PoolId.ChatgptPlan, None, RefreshTrigger.Pick)._3, None)
    assertEquals(reads.codexReads.get(), 0)
    Files.writeString(home.resolve("auth.json"), "") // a stand-in: only its presence is looked at
    val _ = pools.login(PoolId.ChatgptPlan, force = true)
    assertEquals(pools.unavailable(ModelRef(ProviderKind.Codex, "gpt-5")), None)
    assert(!pools.verdict(PoolId.ChatgptPlan, None)._1.usable, "28% left, stale: needs 35%")
    val (v, states, r) = pools.decide(PoolId.ChatgptPlan, None, RefreshTrigger.Pick)
    assertEquals((v.usable, r.map(x => (x.mode, x.ok))), (true, Some(("app-server", true))))
    assertEquals(reads.codexReads.get(), 1)
    assert(states.forall(s => !s.stale && s.reading.source == PlanQuota.Source.AppServer), states)
    // opencode on the ChatGPT login signs in by itself: the codex login does not gate it
    assertEquals(PlanPools.loginGates(PoolId.ChatgptPlan, ModelRef(ProviderKind.Opencode, "openai/gpt-5")), false)

  test("#989: `agent quota refresh` over HTTP — the API key, a FAKE claude probed, the count in `agent quota`"):
    val fake = FakeCli("claude", List(event(12, 42), Result))
    val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    def post(url: String, body: String, key: Option[String]) =
      val r = http.send(
        key
          .foldLeft(HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json"))((b, k) =>
            b.header("authorization", s"Bearer $k")
          )
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build(),
        HttpResponse.BodyHandlers.ofString()
      )
      (r.statusCode, r.body)
    val keyed = baseCfg.modify(
      _.auth).setTo(baseCfg.auth.modify(_.apiKey).setTo("zz-key")).modify(_.agents).setTo(baseCfg.agents.modify(
        _.enabled).setTo(false).modify(_.providers).setTo(baseCfg.agents.providers.modify(
          _.claudeCode).setTo(baseCfg.agents.providers.claudeCode.modify(_.command).setTo(fake.script.toString)).modify(_.codex).setTo(baseCfg.agents.providers.codex.modify(_.home).setTo(Files.createTempDirectory("hm-codex-none-").toString)
        )
      )
    )
    ox.supervised {
      val b =
        Composition
          .server(keyed, "127.0.0.1", 0, com.histmersion.server.ConverterWiring.client(keyed.converter))
          .start()
      try
        val url = s"http://127.0.0.1:${b.port}/api/agents/quota"
        assertEquals(
          post(s"$url/refresh", """{"pool":"claude-plan"}""", None)._1,
          401,
          "a refresh is a write: it needs the key"
        )
        val (s, body) = post(s"$url/refresh", """{"pool":"claude-plan"}""", Some("zz-key"))
        assertEquals(s, 200, body)
        val j = parser.parse(body).toOption.get.hcursor.downField("pools").downArray
        assertEquals(j.downField("refreshed").as[Boolean], Right(true), body)
        assert(j.downField("detail").as[String].exists(_.contains("five_hour 12% used")), body)
        assertEquals(j.downField("status").downField("refreshesToday").as[Int], Right(1), body)
        assertEquals((fake.after("--max-turns"), fake.after("--model"), fake.stdin), ("1", "haiku", "quota"))
        assertEquals(fake.env("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")
        val q = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body
        assert(q.contains("\"refreshesToday\":1") && q.contains("\"source\":\"probe\""), q.take(800))
        assertEquals(post(s"$url/refresh", """{"pool":"nobody-plan"}""", Some("zz-key"))._1, 400)
        // every pool: the ChatGPT plan's app-server reads are off here, and it says so
        val (sa, all) = post(s"$url/refresh", "{}", Some("zz-key"))
        assertEquals(sa, 200, all)
        assert(all.contains("\"chatgpt-plan\"") && all.contains("refresh is off for this pool"), all)
      finally b.stop()
    }
