package com.histmersion
package server.agents

import com.histmersion.api.Problem
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ModelPicker
import com.histmersion.domain.automation.agents.PlanQuotaRepo

import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.api.automation.Agents.Role
import com.histmersion.api.automation.PlanQuota.*

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.config.ClaudePlanConfig
import com.histmersion.api.automation.PlanQuota
import com.histmersion.config.CodexConfig
import com.histmersion.api.Problem as ApiProblem

/** A fresh reading of a plan pool (#989) without a database or a real CLI: the probe's argument vector and environment,
  * its output read into readings, the single-flight gate, the login checks — and the probe end to end against a FAKE
  * `claude`. Nothing is spent.
  */
class PlanRefreshSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val Cfg = ClaudeCodeConfig(
    command = ClaudeCodeConfig.Command("claude"),
    configDir = ClaudeCodeConfig.ConfigDir(""),
    extraArgs = ClaudeCodeConfig.ExtraArgs(""),
    useApiKey = ClaudeCodeConfig.UseApiKey(false),
    allowNonLoopback = ClaudeCodeConfig.AllowNonLoopback(false),
    mcpUrl = ClaudeCodeConfig.McpUrl(""),
    timeoutSeconds = ClaudeCodeConfig.TimeoutSeconds(30),
    limitCooldownMinutes = ClaudeCodeConfig.LimitCooldownMinutes(60),
    disableSlashCommands = ClaudeCodeConfig.DisableSlashCommands(false),
    env = Map.empty
  )

  /** The `rate_limit_event` of §3a P1 (2026-10-05). */
  val Event: String =
    """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791236400,"rateLimitType":"five_hour",
      |"overageStatus":"rejected","isUsingOverage":false,"unifiedWindows":{"five_hour":{"utilization":0.12,"resetsAt":1791236400},
      |"seven_day":{"utilization":0.42,"resetsAt":1791522000}}},"uuid":"u","session_id":"s"}""".stripMargin.replace(
      "\n",
      ""
    )
  val Init =
    """{"type":"system","subtype":"init","model":"claude-haiku-4-5-20251001","apiKeySource":"none","mcp_servers":[]}"""
  val Answer =
    """{"type":"assistant","message":{"content":[{"type":"text","text":"Quota."}]},"parent_tool_use_id":null}"""
  val Result =
    """{"type":"result","subtype":"success","is_error":false,"result":"Quota.","num_turns":1,"usage":{"input_tokens":9,"output_tokens":3}}"""

  test("the probe's argv: the probe model, ONE turn, no tools, no MCP server, no skills or settings, no session"):
    val a = ClaudeCode.probeArgv(Cfg, ClaudePlanConfig.ProbeModel("haiku")).map(_.value)
    def after(flag: String) = a(a.indexOf(flag) + 1)
    assertEquals(a.take(2), List("claude", "-p"))
    assertEquals(after("--model"), "haiku")
    assertEquals(after("--max-turns"), "1")
    assertEquals(after("--tools"), "", "no built-in tool")
    assertEquals(after("--setting-sources"), "", "the person's settings and hooks stay out; the login stays (§3a P2)")
    assertEquals(after("--output-format"), "stream-json")
    assert(a.contains("--strict-mcp-config") && !a.contains("--mcp-config"), "no MCP server at all")
    assert(a.contains("--disable-slash-commands") && a.contains("--no-session-persistence"), a)
    assert(
      !a.contains("--plugin-dir") && !a.contains("--agent") && !a.contains("--json-schema"),
      "no plugin, no mod, no schema turn"
    )
    assertEquals(after("--system-prompt"), "Reply with one word.", "Claude Code's long system prompt is replaced")
    assertEquals(
      ClaudeCode
        .probeArgv(
          Cfg.modify(_.extraArgs).setTo(ClaudeCodeConfig.ExtraArgs(" --effort low ")),
          ClaudePlanConfig.ProbeModel("haiku")
        )
        .takeRight(2),
      List("--effort", "low")
    )

  test("the probe's environment: output capped, no API key, no run token or quota URL"):
    val env =
      new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x", "HISTMERSION_RUN_TOKEN", "t"))
    ClaudeCode.probeEnvironment(Cfg, ClaudePlanConfig.ProbeMaxTokens(16), env)
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")
    assert(
      !env.containsKey("ANTHROPIC_API_KEY") && !env.containsKey("HISTMERSION_RUN_TOKEN") && !env.containsKey(
        "HISTMERSION_QUOTA_URL"
      ),
      env
    )
    assertEquals(env.get("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")

  test("the probe's output: its rate_limit_event is the reading (source probe, observed now)"):
    val rs = PlanPools
      .probeReadings(
        CliProcess.Run(
          List(Init, Answer, Event, Result).map(CliProcess.Run.Lines(_)),
          CliProcess.Run.Exit(0),
          CliProcess.Run.Stderr(""),
          None
        ),
        PlanQuota.Now(5000L),
        ClaudeCodeConfig.LimitCooldownMinutes(60)
      )
      .fold(e => fail(e.value), identity)
    assertEquals(
      rs.map(r => (r.kind, r.usedPct, r.source, r.observedAt, r.rejected)).toSet,
      Set(("five_hour", 12.0, Source.Probe, 5000L, false), ("seven_day", 42.0, Source.Probe, 5000L, false))
    )
    assertEquals(rs.find(_.kind.value == "seven_day").flatMap(_.resetsAt), Some(1791522000000L))
    assertEquals(PlanPools.summary(rs, PlanQuota.Now(5000L)), "five_hour 12% used, seven_day 42% used")

  test("a probe the plan refuses records a refused window; a rejected rate_limit_event is read as it is"):
    val limit =
      """{"type":"result","subtype":"error_during_execution","is_error":true,"result":"Claude AI usage limit reached|1791236400"}"""
    val rs = PlanPools
      .probeReadings(
        CliProcess
          .Run(List(Init, limit).map(CliProcess.Run.Lines(_)), CliProcess.Run.Exit(1), CliProcess.Run.Stderr(""), None),
        PlanQuota.Now(5000L),
        ClaudeCodeConfig.LimitCooldownMinutes(60)
      )
      .fold(e => fail(e.value), identity)
    assertEquals(rs.map(r => (r.kind, r.rejected, r.resetsAt)), List(("usage_limit", true, Some(1791236400000L))))
    val rejected = Event.replace("\"status\":\"allowed\"", "\"status\":\"rejected\"")
    val rr = PlanPools
      .probeReadings(
        CliProcess.Run(
          List(Init, rejected, limit).map(CliProcess.Run.Lines(_)),
          CliProcess.Run.Exit(1),
          CliProcess.Run.Stderr(""),
          None
        ),
        PlanQuota.Now(5000L),
        ClaudeCodeConfig.LimitCooldownMinutes(60)
      )
      .fold(e => fail(e.value), identity)
    assertEquals(rr.filter(_.rejected.value).map(_.kind), List("five_hour"))
    // no reset in the message: the cool-down
    val noReset = limit.replace("|1791236400", "")
    assertEquals(
      PlanPools
        .probeReadings(
          CliProcess
            .Run(List(noReset).map(CliProcess.Run.Lines(_)), CliProcess.Run.Exit(1), CliProcess.Run.Stderr(""), None),
          PlanQuota.Now(5000L),
          ClaudeCodeConfig.LimitCooldownMinutes(60)
        )
        .toOption
        .get
        .head
        .resetsAt,
      Some(5000L + 3600000L)
    )

  test("a probe that says nothing about the plan is no reading (and says why)"):
    val r = PlanPools.probeReadings(
      CliProcess
        .Run(List(Init, Result).map(CliProcess.Run.Lines(_)), CliProcess.Run.Exit(0), CliProcess.Run.Stderr(""), None),
      PlanQuota.Now(5000L),
      ClaudeCodeConfig.LimitCooldownMinutes(60)
    )
    assert(r.left.exists(_.value.contains("no rate_limit_event")), r)
    val e = PlanPools.probeReadings(
      CliProcess.Run(Nil, CliProcess.Run.Exit(1), CliProcess.Run.Stderr("Not logged in"), None),
      PlanQuota.Now(5000L),
      ClaudeCodeConfig.LimitCooldownMinutes(60)
    )
    assert(e.left.exists(_.value.contains("Not logged in")), e)

  test("single flight: a second refresh of the same pool WAITS for the first and then takes none"):
    val gate = RefreshGate()
    val calls = AtomicInteger(0)
    val inside = CountDownLatch(1)
    val release = CountDownLatch(1)
    def body(last: Option[RefreshGate.Instant]): Either[Problem, Int] =
      if throttled(
          last.map(l => RefreshInfo.LastAttempt(l.value)),
          PlanQuota.Rules.MinGap(600000L),
          PlanQuota.Now(1000L)
        )
      then Left(Problem("throttled"))
      else
        val n = calls.incrementAndGet()
        inside.countDown()
        release.await(10, TimeUnit.SECONDS)
        Right(n)
    val first = java.util.concurrent.CompletableFuture.supplyAsync(() => gate.exclusive(PoolId.ClaudePlan, 1000L)(body))
    assert(inside.await(10, TimeUnit.SECONDS))
    val second =
      java.util.concurrent.CompletableFuture.supplyAsync(() => gate.exclusive(PoolId.ClaudePlan, 1000L)(body))
    Thread.sleep(200)
    assert(!second.isDone, "the second waits while the first reads")
    // another pool is not held up
    assertEquals(gate.exclusive(PoolId.ChatgptPlan, 1000L)(_ => Right(7)), Right(7))
    release.countDown()
    assertEquals(first.get(10, TimeUnit.SECONDS), Right(1))
    assertEquals(second.get(10, TimeUnit.SECONDS), Left("throttled"))
    assertEquals(calls.get(), 1)
    assertEquals(gate.lastAttempt(PoolId.ClaudePlan), Some(1000L))

  test("the gate stamps an attempt only when one was made"):
    val gate = RefreshGate()
    assertEquals(
      gate.exclusive(PoolId.ClaudePlan, 5L)(_ => Left[Problem, Int](Problem("not now"))),
      Left("not now")
    )
    assertEquals(gate.lastAttempt(PoolId.ClaudePlan), None)

  test("logins: claude auth status read like the doctor; a codex home's login is a file that exists (never read)"):
    assertEquals(
      PlanPools.claudeLogin(
        AgentDoctor.Output("""{"loggedIn":true,"authMethod":"claude.ai","subscriptionType":"max"}"""),
        ClaudeCodeConfig.UseApiKey(false)
      ),
      Login.Present(PlanQuota.Login.Present.Detail("logged in (claude.ai, plan max)"))
    )
    assert(
      PlanPools
        .claudeLogin(AgentDoctor.Output("""{"loggedIn":false}"""), ClaudeCodeConfig.UseApiKey(false))
        .isInstanceOf[Login.Missing]
    )
    assert(
      PlanPools
        .claudeLogin(
          AgentDoctor.Output("""{"loggedIn":true,"authMethod":"api_key"}"""),
          ClaudeCodeConfig.UseApiKey(false)
        )
        .isInstanceOf[Login.Missing]
    )
    assert(
      PlanPools
        .claudeLogin(AgentDoctor.Output("Error: timeout"), ClaudeCodeConfig.UseApiKey(false))
        .isInstanceOf[Login.Unknown],
      "not an answer: not known"
    )
    val home = Files.createTempDirectory("hm-codex-home-")
    assert(PlanPools.codexLogin(Some(home), CodexConfig.Command("codex")) match
      case Login.Missing(w) => w.value.contains("codex login") && w.value.contains(home.toString)
      case _                => false)
    assert(PlanPools.codexLogin(None, CodexConfig.Command("codex")).isInstanceOf[Login.Missing])
    Files.writeString(home.resolve("auth.json"), "") // an empty stand-in: only its presence is looked at
    assert(PlanPools.codexLogin(Some(home), CodexConfig.Command("codex")).isInstanceOf[Login.Present])

  test("the role a plan-quota pause names, so a paused run is asked about that role again"):
    assertEquals(
      ModelPicker.pausedRole(
        ApiProblem(
          "plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …"
        )
      ),
      Some(Role.Extractor)
    )
    assertEquals(
      ModelPicker.pausedRole(
        ApiProblem("plan-quota: review: all models for role cold-reader are below their allowance")
      ),
      Some(Role.ColdReader)
    )
    assertEquals(ModelPicker.pausedRole(ApiProblem("plan-quota: something else")), None)

  // 2026-10-08: the Claude plan's five_hour window at its floor (reset 00:20Z) and the ChatGPT plan
  // held read as "no window frees by itself", and the run kept no resume time
  test("#1827: only a reading OBSERVED after the reported limit, with a usable verdict, lifts a cool-down"):
    assert(
      ModelPicker.lifts(
        since = PlanQuotaRepo.EpochMillis(1000L),
        observed = Some(PlanQuotaRepo.EpochMillis(1001L)),
        usable = ModelPicker.PoolUsable(true)
      )
    )
    assert(
      !ModelPicker.lifts(
        PlanQuotaRepo.EpochMillis(1000L),
        Some(PlanQuotaRepo.EpochMillis(1000L)),
        usable = ModelPicker.PoolUsable(true)
      ),
      "the reading the limit came with says nothing about a reset"
    )
    assert(
      !ModelPicker.lifts(
        PlanQuotaRepo.EpochMillis(1000L),
        Some(PlanQuotaRepo.EpochMillis(999L)),
        usable = ModelPicker.PoolUsable(true)
      )
    )
    assert(
      !ModelPicker.lifts(
        PlanQuotaRepo.EpochMillis(1000L),
        Some(PlanQuotaRepo.EpochMillis(2000L)),
        usable = ModelPicker.PoolUsable(false)
      ),
      "still below its floor"
    )
    assert(
      !ModelPicker.lifts(PlanQuotaRepo.EpochMillis(1000L), None, usable = ModelPicker.PoolUsable(true)),
      "no reading at all"
    )

  test("a held pool in the chain does not hide another pool's reset: the earliest one counts, the held one is named"):
    import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
    val (opus, gpt) = (
      ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("opus")),
      ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-6-luna"))
    )
    val reset = PlanQuotaRepo.EpochMillis(java.time.Instant.parse("2026-10-09T00:20:00Z").toEpochMilli)
    val (at, text) = ModelPicker.whenFrees(List((opus, Some(reset), None), (gpt, None, Some(PoolId.ChatgptPlan))))
    assertEquals(at, Some(reset))
    assert(text.contains("next reset at 2026-10-09T00:20:00Z (claude-code:opus)"), text)
    assert(text.contains("the ChatGPT plan is held") && text.contains("agent quota release chatgpt-plan"), text)
    assert(!text.contains("no window frees by itself"), text)
    // the earliest of several resets; a model that never frees (a reserve as large as its limit) does not hide them
    val later = PlanQuotaRepo.EpochMillis(reset.value + 3600000L)
    assertEquals(
      ModelPicker.whenFrees(List((gpt, Some(later), None), (opus, None, None), (opus, Some(reset), None)))._1,
      Some(reset)
    )
    // only held pools: nothing frees by itself, and it says which pool to release
    val (none, heldOnly) = ModelPicker.whenFrees(List((gpt, None, Some(PoolId.ChatgptPlan))))
    assertEquals(none, None)
    assert(heldOnly.startsWith("no window frees by itself: the ChatGPT plan is held"), heldOnly)
    assertEquals(ModelPicker.whenFrees(List((opus, None, None)))._1, None)

  test("the probe end to end against a FAKE claude: argv, stdin 'quota', capped output, the reading"):
    val fake = FakeCli("claude", List(Init, Answer, Event, Result))
    val cc = Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(fake.script.toString))
    val run = PlanPools
      .runProbe(
        ClaudeCode.probeArgv(cc, ClaudePlanConfig.ProbeModel("haiku")),
        env => ClaudeCode.probeEnvironment(cc, ClaudePlanConfig.ProbeMaxTokens(16), env),
        CliProcess.RunStdin(ClaudeCode.ProbePrompt),
        CliProcess.RunTimeoutSeconds(30)
      )
      .fold(e => fail(e.value), identity)
    assertEquals(fake.stdin, "quota")
    assertEquals(fake.after("--max-turns"), "1")
    assertEquals(fake.after("--model"), "haiku")
    assert(fake.cwdWasEmpty, "an empty working directory: no project CLAUDE.md or .claude/")
    assertEquals(fake.env("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")
    assertEquals(fake.env("HISTMERSION_RUN_TOKEN"), "<unset>")
    assertEquals(
      PlanPools
        .probeReadings(run, PlanQuota.Now(1L), ClaudeCodeConfig.LimitCooldownMinutes(60))
        .map(_.map(_.kind).toSet),
      Right(Set("five_hour", "seven_day"))
    )
    // a command that cannot start is a reason, not an exception
    val gone = PlanPools.runProbe(
      List(ClaudeCode.Argument("/nonexistent/claude"), ClaudeCode.Argument("-p")),
      _ => (),
      CliProcess.RunStdin("quota"),
      CliProcess.RunTimeoutSeconds(5)
    )
    assert(gone.left.exists(_.value.contains("cannot run")), gone)
