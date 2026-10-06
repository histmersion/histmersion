package com.histmersion.server.agents

import com.histmersion.core.ClaudeCodeConfig
import com.histmersion.core.Agents.{ModelRef, ProbeAnswer, ProviderKind}
import com.histmersion.core.PlanQuota.PoolId
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The `claude-code` provider without a model: the argument vector, the environment, the stream-json
  * output read back (rate-limit readings, the quota guard's abort, the last of two results), the
  * plan's usage limit — and the whole client against a FAKE `claude` replaying recorded streams on the
  * GENERATED plugin. No network, no login, nothing spent. */
class ClaudeCodeSuite extends munit.FunSuite:

  private val Cfg = ClaudeCodeConfig(command = "claude", configDir = "", extraArgs = "", useApiKey = false,
    allowNonLoopback = false, mcpUrl = "", timeoutSeconds = 30, limitCooldownMinutes = 60)
  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val AllTools = ToolName.values.toList.map(_.wire)
  private val Launch = ClaudeCode.Launch.Agent("/g/headless", "article-skeptic", "/g/roles/article-skeptic/settings.json")

  test("argv: the generated plugin's agent and role settings; no hooks switch, no slash-command switch, our MCP only"):
    val a = ClaudeCode.argv(Cfg, "sonnet", 4, """{"type":"object"}""", Launch, Some("/t/mcp.json"),
      List("get_source", "search"), AllTools)
    assertEquals(a.take(2), List("claude", "-p"))
    def after(flag: String) = a(a.indexOf(flag) + 1)
    assertEquals(after("--output-format"), "stream-json")
    assertEquals(after("--model"), "sonnet")
    assertEquals(after("--max-turns"), "5", "one turn more than the agent's, for the StructuredOutput call")
    assertEquals(after("--plugin-dir"), "/g/headless")
    assertEquals(after("--agent"), "histmersion-agents:article-skeptic")
    assertEquals(after("--settings"), "/g/roles/article-skeptic/settings.json")
    assertEquals(after("--setting-sources"), "", "the person's settings files and hooks stay out")
    assertEquals(after("--json-schema"), """{"type":"object"}""")
    assertEquals(after("--tools"), "", "built-in tools are off")
    assertEquals(after("--permission-mode"), "dontAsk")
    assertEquals(after("--mcp-config"), "/t/mcp.json")
    assertEquals(after("--allowedTools"), "mcp__histmersion__get_source,mcp__histmersion__search,StructuredOutput",
      "without StructuredOutput the model writes its JSON as text and structured_output stays empty")
    assertEquals(after("--disallowedTools").split(',').toSet, AllTools.filterNot(Set("get_source", "search")).map(ClaudeCode.toolId).toSet)
    assert(a.contains("--strict-mcp-config") && a.contains("--no-session-persistence"))
    assert(!a.contains("--disable-slash-commands"), "it blocks every skill (§3a P4)")
    assert(!a.exists(_.contains("disableAllHooks")), "it turns the quota-guard mod off (§3a P5)")
    assert(!a.contains("--system-prompt-file") && !a.contains("--bare"))

  test("argv: without a generated plugin the prompt goes as a file; a web role keeps the web tools"):
    val a = ClaudeCode.argv(Cfg, "opus", 1, "{}", ClaudeCode.Launch.SystemPrompt("/s"), None, Nil, AllTools,
      web = List("WebSearch", "WebFetch"))
    assertEquals(a(a.indexOf("--system-prompt-file") + 1), "/s")
    assertEquals(a(a.indexOf("--tools") + 1), "WebSearch,WebFetch")
    assertEquals(a(a.indexOf("--allowedTools") + 1), "WebSearch,WebFetch,StructuredOutput")
    assert(!a.contains("--mcp-config") && !a.contains("--disallowedTools") && !a.contains("--plugin-dir"), a)

  test("environment: no Anthropic key unless use-api-key, no claude.ai connectors, the run token and where to report"):
    val env = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x", "ANTHROPIC_AUTH_TOKEN", "t", "PATH", "/bin"))
    ClaudeCode.environment(Cfg, 2048, Some("hmrt_1"), "http://h/api/agents/quota/claude", env)
    assert(!env.containsKey("ANTHROPIC_API_KEY") && !env.containsKey("ANTHROPIC_AUTH_TOKEN"), env)
    assertEquals(env.get("PATH"), "/bin")
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "2048")
    assertEquals(env.get("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")
    assertEquals(env.get("HISTMERSION_RUN_TOKEN"), "hmrt_1")
    assertEquals(env.get("HISTMERSION_QUOTA_URL"), "http://h/api/agents/quota/claude")
    val keep = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x"))
    ClaudeCode.environment(Cfg.copy(useApiKey = true, configDir = "/c/hm"), 10, None, "u", keep)
    assertEquals(keep.get("ANTHROPIC_API_KEY"), "sk-x")
    assertEquals(keep.get("CLAUDE_CONFIG_DIR"), "/c/hm")

  test("mcp config: http to the agent endpoint, the step's run token as the bearer"):
    assertEquals(ClaudeCode.mcpConfig("http://127.0.0.1:18000/mcp/agent", Some("hmrt_9")).noSpaces,
      """{"mcpServers":{"histmersion":{"type":"http","url":"http://127.0.0.1:18000/mcp/agent","headers":{"Authorization":"Bearer hmrt_9"}}}}""")

  test("the schema handed to --json-schema carries no 2020-12 marker (Claude Code validates draft-07)"):
    val s = ClaudeCode.schemaJson(ResponseSchema.derived[ProbeAnswer]())
    assert(!s.contains("$schema"), s)
    assert(s.contains("\"citekey\"") && s.contains("\"title\""), s)

  test("loopback: 127.x, ::1 and localhost are; 0.0.0.0 and a LAN address are not — and claude-code is refused there"):
    assert(ClaudeCodeEnv.isLoopback("127.0.0.1") && ClaudeCodeEnv.isLoopback("localhost") && ClaudeCodeEnv.isLoopback("[::1]"))
    assert(!ClaudeCodeEnv.isLoopback("0.0.0.0") && !ClaudeCodeEnv.isLoopback("192.168.1.5"))
    val dir = Files.createTempDirectory("hm-rt-")
    val lan = CliRuntime(FakeCli.app(dir, "HISTMERSION_BIND" -> "0.0.0.0"))
    assert(ClaudeCode.client(Cfg, lan, Sonnet).left.exists(_.contains("beyond loopback")))
    assert(ClaudeCode.client(Cfg.copy(allowNonLoopback = true), lan, Sonnet).isRight)
    assert(ClaudeCode.client(Cfg, CliRuntime(FakeCli.app(dir)), ModelRef(ProviderKind.Local, "x")).isLeft)

  // ── the stream-json output ─────────────────────────────────────────────────────────────────────

  import ClaudeStreams.*

  test("parse: the trace (text, tool call, tool result), the typed answer, usage with cache tokens counted as input"):
    val o = ClaudeCode.parse(List(Init, "not json", Turn1, Tool1, Turn2, Success))
    assertEquals(o.modelId, Some("claude-sonnet-4-5"))
    assertEquals(o.toolCalls.map(c => (c.toolName, c.input, c.output)), Vector(("get_source", """{"citekey":"x2020"}""", "# X 2020")))
    assertEquals(o.usage, TokenUsage(Tokens(450), Tokens(20), Tokens(300), Tokens.Zero, Tokens(50)))
    assertEquals(o.mcpProblem, None)
    val r = ClaudeCode.result(o, "the prompt", ResponseSchema.derived[ProbeAnswer]()).toOption.get
    assertEquals(r.finalAnswer, Right(ProbeAnswer("x2020", "X", Some(2020))))
    assertEquals(r.finishReason, FinishReason.NaturalStop)
    assertEquals(r.iterations, 2)
    val roles = r.history.entries.map(_.getClass.getSimpleName).toList
    assertEquals(roles, List("UserPrompt", "AssistantResponse", "ToolResult", "AssistantResponse", "AssistantResponse"))

  test("parse: every rate_limit_event window becomes a reading of the Claude plan (§3a P1)"):
    val o = ClaudeCode.parse(List(Init, RateLimit, Turn2, Success))
    val rs = o.readings.map(r => s"${r.limit} ${r.kind} ${math.round(r.usedPct)} ${r.resetsAt.getOrElse(0L)} ${r.rejected} ${r.source.wire}")
    assertEquals(rs.toSet, Set("claude five_hour 12 1791236400000 false run", "claude seven_day 42 1791522000000 false run"))

  test("parse: a rejected representative window without unifiedWindows is a refused reading"):
    val rej = """{"type":"rate_limit_event","rate_limit_info":{"status":"rejected","resetsAt":1791236400,"rateLimitType":"five_hour"}}"""
    val r = ClaudeCode.parse(List(rej)).readings
    assertEquals(r.map(x => (x.kind, x.rejected, x.usedPct)), List(("five_hour", true, 100.0)))

  test("parse: of two result events (a backgrounded subagent) the LAST is the answer (§3a)"):
    val interim = Success.replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace("\"result\":\"Done.\"", "\"result\":\"working on it\"")
    val o = ClaudeCode.parse(List(Init, interim, Success))
    assertEquals(ClaudeCode.result(o, "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer,
      Right(ProbeAnswer("x2020", "X", Some(2020))))

  test("result: a JSON answer written as text (no structured_output) is still the answer"):
    val fenced = Success.replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace("\"result\":\"Done.\"", "\"result\":\"Here it is:\\n```json\\n{\\\"citekey\\\":\\\"x2020\\\",\\\"title\\\":\\\"X\\\",\\\"year\\\":2020}\\n```\"")
    val o = ClaudeCode.parse(List(Init, fenced))
    assertEquals(ClaudeCode.result(o, "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer,
      Right(ProbeAnswer("x2020", "X", Some(2020))))

  test("result: a step cut off by max turns is not rescued from its text"):
    val cut = Success.replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace("\"subtype\":\"success\"", "\"subtype\":\"error_max_turns\"")
      .replace("\"result\":\"Done.\"", "\"result\":\"{\\\"citekey\\\":\\\"x2020\\\",\\\"title\\\":\\\"X\\\"}\"")
    val o = ClaudeCode.parse(List(Init, cut))
    assert(ClaudeCode.result(o, "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer.isLeft)

  test("jsonInText: a fence wins, else first brace to last; prose and arrays are not answers"):
    assertEquals(ClaudeCode.jsonInText("a\n```json\n{\"x\":1}\n```\nb {not}").flatMap(_.hcursor.get[Int]("x").toOption), Some(1))
    assertEquals(ClaudeCode.jsonInText("so: {\"x\": {\"y\": 2}} done").flatMap(_.hcursor.downField("x").get[Int]("y").toOption), Some(2))
    assertEquals(ClaudeCode.jsonInText("no json here"), None)
    assertEquals(ClaudeCode.jsonInText("[1, 2]"), None)
    assertEquals(ClaudeCode.jsonInText("{broken"), None)

  test("parse: the quota-guard mod's abort is a success with an empty answer and terminal_reason aborted_streaming"):
    val o = ClaudeCode.parse(List(Init, Aborted))
    assert(o.result.exists(ClaudeCode.abortedByGuard), o.result)
    assert(!ClaudeCode.parse(List(Success)).result.exists(ClaudeCode.abortedByGuard))

  test("the plan's usage limit is told apart from an error, with its reset time when the CLI gives one"):
    val lim = """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val l = ClaudeCode.result(ClaudeCode.parse(List(lim)), "u", ResponseSchema.derived[ProbeAnswer]()).left.toOption.get
    assertEquals(l.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    val other = lim.replace("Claude AI usage limit reached|1759683600", "API Error: 500")
    assert(ClaudeCode.result(ClaudeCode.parse(List(other)), "u", ResponseSchema.derived[ProbeAnswer]()).isRight, "an error is not a limit")

  test("a denied tool and an MCP server that did not connect are named in the failure"):
    val init = Init.replace("connected", "failed")
    val res = """{"type":"result","subtype":"error_during_execution","is_error":true,"num_turns":1,"result":"","permission_denials":[{"tool_name":"mcp__histmersion__query"}],"usage":{}}"""
    val r = ClaudeCode.result(ClaudeCode.parse(List(init, res)), "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get
    r.finishReason match
      case FinishReason.Error(m) => assert(m.contains("agent MCP server is failed") && m.contains("mcp__histmersion__query"), m)
      case other => fail(s"not an error: $other")

  // ── the client against a fake executable, on a generated tree ─────────────────────────────────

  private def runtime(sink: FakeCli.Sink): (CliRuntime, AgentDef) =
    val dir = Files.createTempDirectory("hm-agents-")
    val rt  = CliRuntime(FakeCli.app(dir))
    rt.pools = Some(sink)
    val gen = rt.trees.generate(Generated.known(rt.definitions), List(AgentRuntime.ClaudeCode))
    assert(gen.isRight, gen)
    (rt, rt.definitions.agent(StepAgent.Skeptic).toOption.get)

  private val getSource = AgentTool.fromFunction[FollowupsInput]("get_source", "read a source")(_ => "")

  private def call(agent: AgentDef, interceptors: List[AgentInterceptor[Identity]] = Nil) =
    LlmCall[ProbeAnswer]("SYSTEM PROMPT", "USER <source-text>data</source-text>", List(getSource),
      ResponseSchema.derived[ProbeAnswer](), maxIterations = 3, maxTokens = 777, interceptors, Some(agent), Some(RunScope("run-1", 4)))

  test("the client end to end: the generated agent and settings, the run token (revoked after), stdin, an empty cwd, readings recorded"):
    val fake = FakeCli("claude", List(Init, Turn1, Tool1, RateLimit, Turn2, Success))
    val sink = FakeCli.Sink()
    val (rt, skeptic) = runtime(sink)
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    val counting = new AgentInterceptor[Identity]:
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
        val _ = calls.incrementAndGet(); next
    val r = ClaudeCodeClient(Sonnet, Cfg.copy(command = fake.script.toString), rt).run(call(skeptic, List(counting)))
    assertEquals(r.finalAnswer, Right(ProbeAnswer("x2020", "X", Some(2020))))
    assertEquals(calls.get, 1, "the interceptors (the LLM-slot semaphore, latency) see the process as one call")
    assertEquals(fake.stdin, "USER <source-text>data</source-text>", "untrusted data on stdin, never in argv")
    assert(!fake.args.exists(_.contains("<source-text>")))
    val plugin = Path.of(fake.after("--plugin-dir"))
    assert(Files.isRegularFile(plugin.resolve("agents/article-skeptic.md")), plugin)
    assertEquals(fake.after("--agent"), "histmersion-agents:article-skeptic")
    val settings = Files.readString(Path.of(fake.after("--settings")))
    assert(settings.contains("mcp__histmersion__followups") && !settings.contains("disableAllHooks"), settings)
    val token = fake.env("HISTMERSION_RUN_TOKEN")
    assert(token.startsWith("hmrt_"), token)
    assertEquals(rt.tokens.check(token), None, "the token dies with the step")
    assertEquals(fake.env("ANTHROPIC_API_KEY"), "<unset>")
    assertEquals(fake.env("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")
    assert(fake.cwdWasEmpty, "the CLI runs in an empty work dir")
    val kinds = sink.recorded.asScala.toList.filter(_._1 == PoolId.ClaudePlan).map(_._2.kind).toSet
    assertEquals(kinds, Set("five_hour", "seven_day"))

  test("the client: a pool below its floor mid-step STOPS the process (no waiting for the answer) — the plan's limit"):
    val fake = FakeCli("claude", List(Init, Turn1, RateLimit), sleepAfter = 60)
    val sink = FakeCli.Sink(block = Some(("five_hour: 8% left, floor 25%", Some(1791236400000L))))
    val (rt, skeptic) = runtime(sink)
    val t0 = System.nanoTime()
    val e = intercept[ModelLimitReached](ClaudeCodeClient(Sonnet, Cfg.copy(command = fake.script.toString), rt).run(call(skeptic)))
    assert((System.nanoTime() - t0) / 1000000000L < 25, "stopped, not timed out")
    assert(e.getMessage.contains("quota guard") && e.getMessage.contains("floor 25%"), e.getMessage)
    assertEquals(e.until, Some(java.time.Instant.ofEpochMilli(1791236400000L)))

  test("the client: the mod's abort (aborted_streaming, empty answer) ends the step as the plan's limit"):
    val fake = FakeCli("claude", List(Init, Turn1, Aborted))
    val (rt, skeptic) = runtime(FakeCli.Sink())
    val e = intercept[ModelLimitReached](ClaudeCodeClient(Sonnet, Cfg.copy(command = fake.script.toString), rt).run(call(skeptic)))
    assert(e.getMessage.contains("quota-guard mod"), e.getMessage)

  test("the client: a usage limit is ModelLimitReached; no result line is a provider error; a missing executable says how to fix it"):
    val lim = """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val f1 = FakeCli("claude", List(lim))
    val (rt, skeptic) = runtime(FakeCli.Sink())
    val e = intercept[ModelLimitReached](ClaudeCodeClient(Sonnet, Cfg.copy(command = f1.script.toString), rt).run(call(skeptic)))
    assertEquals(e.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    val f2 = FakeCli("claude", Nil, code = 3)
    val io = intercept[java.io.IOException](ClaudeCodeClient(Sonnet, Cfg.copy(command = f2.script.toString), rt).run(call(skeptic)))
    assert(io.getMessage.contains("exited 3 without a result"), io.getMessage)
    val missing = intercept[java.io.IOException](ClaudeCodeClient(Sonnet, Cfg.copy(command = "/nonexistent/claude"), rt).run(call(skeptic)))
    assert(missing.getMessage.contains("HISTMERSION_AGENTS_CLAUDE_COMMAND"), missing.getMessage)

/** Recorded `claude -p --output-format stream-json` lines (2.1.289; the rate-limit event and the abort
  * as the probes of docs/research-agent-runtimes.md §3a saw them). */
object ClaudeStreams:
  val Init = """{"type":"system","subtype":"init","model":"claude-sonnet-4-5","apiKeySource":"none","mcp_servers":[{"name":"histmersion","status":"connected"}]}"""
  val Turn1 = """{"type":"assistant","message":{"id":"m1","content":[{"type":"text","text":"Looking it up."},{"type":"tool_use","id":"toolu_1","name":"mcp__histmersion__get_source","input":{"citekey":"x2020"}}],"usage":{"input_tokens":10,"output_tokens":5}},"parent_tool_use_id":null}"""
  val Tool1 = """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"toolu_1","content":[{"type":"text","text":"# X 2020"}]}]},"parent_tool_use_id":null}"""
  val Turn2 = """{"type":"assistant","message":{"id":"m2","content":[{"type":"text","text":"Done."}]},"parent_tool_use_id":null}"""
  val RateLimit = """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791236400,"rateLimitType":"five_hour","overageStatus":"rejected","overageDisabledReason":"org_level_disabled","isUsingOverage":false,"unifiedWindows":{"five_hour":{"utilization":0.12,"resetsAt":1791236400},"seven_day":{"utilization":0.42,"resetsAt":1791522000}}},"uuid":"u","session_id":"s"}"""
  val Success = """{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":"Done.","structured_output":{"citekey":"x2020","title":"X","year":2020},"total_cost_usd":0.0123,"usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":300,"cache_creation_input_tokens":50},"modelUsage":{"claude-sonnet-4-5":{"inputTokens":100,"outputTokens":20,"cacheReadInputTokens":300,"cacheCreationInputTokens":50,"costUSD":0.0123}},"permission_denials":[]}"""
  val Aborted = """{"type":"result","subtype":"success","is_error":false,"result":"","num_turns":2,"stop_reason":"tool_use","terminal_reason":"aborted_streaming","usage":{"input_tokens":5,"output_tokens":1}}"""
