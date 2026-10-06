package com.histmersion.server.agents

import com.histmersion.core.ClaudeCodeConfig
import com.histmersion.core.Agents.Role
import com.histmersion.core.PlanQuota.*

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

/** A fresh reading of a plan pool (#989) without a database or a real CLI: the probe's argument
  * vector and environment, its output read into readings, the single-flight gate, the login checks
  * — and the probe end to end against a FAKE `claude`. Nothing is spent. */
class PlanRefreshSuite extends munit.FunSuite:

  private val Cfg = ClaudeCodeConfig(command = "claude", configDir = "", extraArgs = "", useApiKey = false,
    allowNonLoopback = false, mcpUrl = "", timeoutSeconds = 30, limitCooldownMinutes = 60)

  /** The `rate_limit_event` of §3a P1 (2026-10-05). */
  val Event: String =
    """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791236400,"rateLimitType":"five_hour",
      |"overageStatus":"rejected","isUsingOverage":false,"unifiedWindows":{"five_hour":{"utilization":0.12,"resetsAt":1791236400},
      |"seven_day":{"utilization":0.42,"resetsAt":1791522000}}},"uuid":"u","session_id":"s"}""".stripMargin.replace("\n", "")
  val Init   = """{"type":"system","subtype":"init","model":"claude-haiku-4-5-20251001","apiKeySource":"none","mcp_servers":[]}"""
  val Answer = """{"type":"assistant","message":{"content":[{"type":"text","text":"Quota."}]},"parent_tool_use_id":null}"""
  val Result = """{"type":"result","subtype":"success","is_error":false,"result":"Quota.","num_turns":1,"usage":{"input_tokens":9,"output_tokens":3}}"""

  test("the probe's argv: the probe model, ONE turn, no tools, no MCP server, no skills or settings, no session"):
    val a = ClaudeCode.probeArgv(Cfg, "haiku")
    def after(flag: String) = a(a.indexOf(flag) + 1)
    assertEquals(a.take(2), List("claude", "-p"))
    assertEquals(after("--model"), "haiku")
    assertEquals(after("--max-turns"), "1")
    assertEquals(after("--tools"), "", "no built-in tool")
    assertEquals(after("--setting-sources"), "", "the person's settings and hooks stay out; the login stays (§3a P2)")
    assertEquals(after("--output-format"), "stream-json")
    assert(a.contains("--strict-mcp-config") && !a.contains("--mcp-config"), "no MCP server at all")
    assert(a.contains("--disable-slash-commands") && a.contains("--no-session-persistence"), a)
    assert(!a.contains("--plugin-dir") && !a.contains("--agent") && !a.contains("--json-schema"), "no plugin, no mod, no schema turn")
    assertEquals(after("--system-prompt"), "Reply with one word.", "Claude Code's long system prompt is replaced")
    assertEquals(ClaudeCode.probeArgv(Cfg.copy(extraArgs = " --effort low "), "haiku").takeRight(2), List("--effort", "low"))

  test("the probe's environment: output capped, no API key, no run token or quota URL"):
    val env = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x", "HISTMERSION_RUN_TOKEN", "t"))
    ClaudeCode.probeEnvironment(Cfg, 16, env)
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")
    assert(!env.containsKey("ANTHROPIC_API_KEY") && !env.containsKey("HISTMERSION_RUN_TOKEN") && !env.containsKey("HISTMERSION_QUOTA_URL"), env)
    assertEquals(env.get("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")

  test("the probe's output: its rate_limit_event is the reading (source probe, observed now)"):
    val rs = PlanPools.probeReadings(CliProcess.Run(List(Init, Answer, Event, Result), 0, "", None), 5000L, 60)
      .fold(fail(_), identity)
    assertEquals(rs.map(r => (r.kind, r.usedPct, r.source, r.observedAt, r.rejected)).toSet,
      Set(("five_hour", 12.0, Source.Probe, 5000L, false), ("seven_day", 42.0, Source.Probe, 5000L, false)))
    assertEquals(rs.find(_.kind == "seven_day").flatMap(_.resetsAt), Some(1791522000000L))
    assertEquals(PlanPools.summary(rs, 5000L), "five_hour 12% used, seven_day 42% used")

  test("a probe the plan refuses records a refused window; a rejected rate_limit_event is read as it is"):
    val limit = """{"type":"result","subtype":"error_during_execution","is_error":true,"result":"Claude AI usage limit reached|1791236400"}"""
    val rs = PlanPools.probeReadings(CliProcess.Run(List(Init, limit), 1, "", None), 5000L, 60).fold(fail(_), identity)
    assertEquals(rs.map(r => (r.kind, r.rejected, r.resetsAt)), List(("usage_limit", true, Some(1791236400000L))))
    val rejected = Event.replace("\"status\":\"allowed\"", "\"status\":\"rejected\"")
    val rr = PlanPools.probeReadings(CliProcess.Run(List(Init, rejected, limit), 1, "", None), 5000L, 60).fold(fail(_), identity)
    assertEquals(rr.filter(_.rejected).map(_.kind), List("five_hour"))
    // no reset in the message: the cool-down
    val noReset = limit.replace("|1791236400", "")
    assertEquals(PlanPools.probeReadings(CliProcess.Run(List(noReset), 1, "", None), 5000L, 60).toOption.get.head.resetsAt,
      Some(5000L + 3600000L))

  test("a probe that says nothing about the plan is no reading (and says why)"):
    val r = PlanPools.probeReadings(CliProcess.Run(List(Init, Result), 0, "", None), 5000L, 60)
    assert(r.left.exists(_.contains("no rate_limit_event")), r)
    val e = PlanPools.probeReadings(CliProcess.Run(Nil, 1, "Not logged in", None), 5000L, 60)
    assert(e.left.exists(_.contains("Not logged in")), e)

  test("single flight: a second refresh of the same pool WAITS for the first and then takes none"):
    val gate = RefreshGate()
    val calls = AtomicInteger(0)
    val inside = CountDownLatch(1)
    val release = CountDownLatch(1)
    def body(last: Option[Long]): Either[String, Int] =
      if throttled(last, 600000L, 1000L) then Left("throttled")
      else
        val n = calls.incrementAndGet()
        inside.countDown()
        release.await(10, TimeUnit.SECONDS)
        Right(n)
    val first  = java.util.concurrent.CompletableFuture.supplyAsync(() => gate.exclusive(PoolId.ClaudePlan, 1000L)(body))
    assert(inside.await(10, TimeUnit.SECONDS))
    val second = java.util.concurrent.CompletableFuture.supplyAsync(() => gate.exclusive(PoolId.ClaudePlan, 1000L)(body))
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
    assertEquals(gate.exclusive(PoolId.ClaudePlan, 5L)(_ => Left[String, Int]("not now")), Left("not now"))
    assertEquals(gate.lastAttempt(PoolId.ClaudePlan), None)

  test("logins: claude auth status read like the doctor; a codex home's login is a file that exists (never read)"):
    assertEquals(PlanPools.claudeLogin("""{"loggedIn":true,"authMethod":"claude.ai","subscriptionType":"max"}""", false),
      Login.Present("logged in (claude.ai, plan max)"))
    assert(PlanPools.claudeLogin("""{"loggedIn":false}""", false).isInstanceOf[Login.Missing])
    assert(PlanPools.claudeLogin("""{"loggedIn":true,"authMethod":"api_key"}""", false).isInstanceOf[Login.Missing])
    assert(PlanPools.claudeLogin("Error: timeout", false).isInstanceOf[Login.Unknown], "not an answer: not known")
    val home = Files.createTempDirectory("hm-codex-home-")
    assert(PlanPools.codexLogin(Some(home), "codex") match
      case Login.Missing(w) => w.contains("codex login") && w.contains(home.toString)
      case _                => false)
    assert(PlanPools.codexLogin(None, "codex").isInstanceOf[Login.Missing])
    Files.writeString(home.resolve("auth.json"), "") // an empty stand-in: only its presence is looked at
    assert(PlanPools.codexLogin(Some(home), "codex").isInstanceOf[Login.Present])

  test("the role a plan-quota pause names, so a paused run is asked about that role again"):
    assertEquals(ModelPicker.pausedRole("plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …"),
      Some(Role.Extractor))
    assertEquals(ModelPicker.pausedRole("plan-quota: review: all models for role cold-reader are below their allowance"), Some(Role.ColdReader))
    assertEquals(ModelPicker.pausedRole("plan-quota: something else"), None)

  test("the probe end to end against a FAKE claude: argv, stdin 'quota', capped output, the reading"):
    val fake = FakeCli("claude", List(Init, Answer, Event, Result))
    val cc = Cfg.copy(command = fake.script.toString)
    val run = PlanPools.runProbe(ClaudeCode.probeArgv(cc, "haiku"), env => ClaudeCode.probeEnvironment(cc, 16, env),
      ClaudeCode.ProbePrompt, 30).fold(fail(_), identity)
    assertEquals(fake.stdin, "quota")
    assertEquals(fake.after("--max-turns"), "1")
    assertEquals(fake.after("--model"), "haiku")
    assert(fake.cwdWasEmpty, "an empty working directory: no project CLAUDE.md or .claude/")
    assertEquals(fake.env("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")
    assertEquals(fake.env("HISTMERSION_RUN_TOKEN"), "<unset>")
    assertEquals(PlanPools.probeReadings(run, 1L, 60).map(_.map(_.kind).toSet), Right(Set("five_hour", "seven_day")))
    // a command that cannot start is a reason, not an exception
    val gone = PlanPools.runProbe(List("/nonexistent/claude", "-p"), _ => (), "quota", 5)
    assert(gone.left.exists(_.contains("cannot run")), gone)
