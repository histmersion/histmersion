package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.{
  AgentDef,
  AgentRuntime,
  FirstCallProbe,
  LlmCall,
  LlmClient,
  ModelLimitReached,
  RunScope,
  RunTokens,
  StepAgent,
  ToolUse
}
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.ClaudeCodeConfig
import com.histmersion.api.automation.Agents.{ModelRef, ProbeAnswer, ProviderKind}
import com.histmersion.api.automation.PlanQuota.PoolId
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The `claude-code` provider without a model: the argument vector, the environment, the stream-json output read back
  * (rate-limit readings, the quota guard's abort, the last of two results), the plan's usage limit — and the whole
  * client against a FAKE `claude` replaying recorded streams on the GENERATED plugin. No network, no login, nothing
  * spent.
  */
class ClaudeCodeSuite extends munit.FunSuite:

  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val Cfg = ClaudeCodeConfig(
    command = com.histmersion.config.ClaudeCodeConfig.Command("claude"),
    configDir = com.histmersion.config.ClaudeCodeConfig.ConfigDir(""),
    extraArgs = com.histmersion.config.ClaudeCodeConfig.ExtraArgs(""),
    useApiKey = com.histmersion.config.ClaudeCodeConfig.UseApiKey(false),
    allowNonLoopback = com.histmersion.config.ClaudeCodeConfig.AllowNonLoopback(false),
    mcpUrl = com.histmersion.config.ClaudeCodeConfig.McpUrl(""),
    timeoutSeconds = com.histmersion.config.ClaudeCodeConfig.TimeoutSeconds(30),
    limitCooldownMinutes = com.histmersion.config.ClaudeCodeConfig.LimitCooldownMinutes(60),
    disableSlashCommands = com.histmersion.config.ClaudeCodeConfig.DisableSlashCommands(false),
    env = Map.empty
  )
  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, com.histmersion.api.automation.Agents.ModelRef.Model("sonnet"))
  private val AllTools = ToolboxOrder.names.map(t => ToolUse.Tool(t.value))
  private val Offered = List(ToolUse.Tool("get_source"), ToolUse.Tool("search"))
  private def stream(lines: String*): List[CliProcess.Run.Lines] = lines.toList.map(CliProcess.Run.Lines(_))
  private val Launch =
    ClaudeCode.Launch.Agent(com.histmersion.server.agents.ClaudeCode.Launch.Agent.PluginDir("/g/headless"), com.histmersion.server.agents.ClaudeCode.Launch.Agent.Name("article-skeptic"), com.histmersion.server.agents.ClaudeCode.Launch.Agent.Settings("/g/roles/article-skeptic/settings.json"))

  test("argv: the generated plugin's agent and role settings; no hooks switch, no slash-command switch, our MCP only"):
    val a = ClaudeCode.argv(
      Cfg,
      com.histmersion.api.automation.Agents.ModelRef.Model("sonnet"),
      com.histmersion.domain.automation.agents.AgentDef.MaxTurns(4),
      ClaudeCode.SchemaJson("""{"type":"object"}"""),
      Launch,
      Some(Path.of("/t/mcp.json")),
      Offered,
      AllTools
    ).map(_.value)
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
    assertEquals(
      after("--allowedTools"),
      "mcp__histmersion__get_source,mcp__histmersion__search,StructuredOutput",
      "without StructuredOutput the model writes its JSON as text and structured_output stays empty"
    )
    assertEquals(
      after("--disallowedTools").split(',').toSet,
      AllTools.filterNot(Offered.toSet).map(ClaudeCode.toolId).toSet
    )
    assert(a.contains("--strict-mcp-config") && a.contains("--no-session-persistence"))
    assert(!a.contains("--disable-slash-commands"), "switched off in this config")
    assert(!a.exists(_.contains("disableAllHooks")), "it turns the quota-guard mod off (§3a P5)")
    assert(!a.contains("--system-prompt-file") && !a.contains("--bare"))

  /** The provider as reference.conf configures it. */
  private val Ref =
    com.histmersion.config.AppConfig.load(org.ekrich.config.ConfigFactory.empty).agents.providers.claudeCode

  test(
    "argv: --disable-slash-commands by default (a role's skills are in its prompt), never --bare or the dynamic-sections flag"
  ):
    val a = ClaudeCode.argv(Ref, ModelRef.Model("sonnet"), AgentDef.MaxTurns(4), ClaudeCode.SchemaJson("{}"), Launch, Some(Path.of("/t/mcp.json")), List(ToolUse.Tool("get_source")), AllTools).map(_.value)
    assert(a.contains("--disable-slash-commands"), a)
    assertEquals(
      a.slice(a.indexOf("--setting-sources"), a.indexOf("--setting-sources") + 3),
      List("--setting-sources", "", "--disable-slash-commands"),
      "before the launch arguments, after the fixed ones"
    )
    // --bare never reads the plan login, drops the plugin's agents and its quota-guard mod; the other
    // flag only moves sections of the DEFAULT system prompt, which --agent replaces
    assert(!a.contains("--bare") && !a.contains("--exclude-dynamic-system-prompt-sections"), a)
    assert(
      !ClaudeCode
        .argv(Ref.modify(_.disableSlashCommands).setTo(ClaudeCodeConfig.DisableSlashCommands(false)), ModelRef.Model("sonnet"), AgentDef.MaxTurns(4), ClaudeCode.SchemaJson("{}"), Launch, None, Nil, AllTools)
        .map(_.value)
        .contains("--disable-slash-commands")
    )

  test(
    "environment: the configured env is applied — defaults set, \"\" removes an inherited variable, the fixed ones still win"
  ):
    val env = new java.util.HashMap[String, String](
      java.util.Map.of("PATH", "/bin", "CLAUDE_CODE_DISABLE_CRON", "1", "ANTHROPIC_API_KEY", "sk-x")
    )
    ClaudeCode.environment(Ref, com.histmersion.domain.automation.agents.LlmCall.MaxTokens(2048), Some(com.histmersion.domain.automation.agents.RunGrant.Token("hmrt_1")), com.histmersion.domain.automation.agents.GenSettings.QuotaUrl("http://q"), env)
    Ref.envChanges.collect { case (k, Some(v)) => k -> v }.foreach((k, v) => assertEquals(env.get(k.value), v, k))
    assertEquals(env.get("CLAUDE_CODE_DISABLE_ARTIFACT"), "1")
    assertEquals(env.get("CLAUDE_CODE_DISABLE_ADVISOR_TOOL"), "1")
    assert(
      !env.containsKey("CLAUDE_CODE_DISABLE_THINKING") && !env.containsKey("CLAUDE_CODE_DISABLE_STRUCTURED_OUTPUTS") &&
        !env.containsKey("CLAUDE_CODE_DISABLE_WEB_FETCH"),
      env
    )
    // the person's file: one default off, one variable of the server's own removed, one added — and an
    // attempt to set what the provider owns (the output cap, the run token, an API key) loses
    val mine = Ref.modify(_.env).setTo(Ref.env ++ Map(
        ClaudeCodeConfig.EnvKey("CLAUDE_CODE_DISABLE_ARTIFACT") -> None,
        ClaudeCodeConfig.EnvKey("PATH") -> Some(ClaudeCodeConfig.EnvValue("")),
        ClaudeCodeConfig.EnvKey("MY_FLAG") -> Some(ClaudeCodeConfig.EnvValue("x")),
        ClaudeCodeConfig.EnvKey("CLAUDE_CODE_MAX_OUTPUT_TOKENS") -> Some(ClaudeCodeConfig.EnvValue("99999")),
        ClaudeCodeConfig.EnvKey("HISTMERSION_RUN_TOKEN") -> Some(ClaudeCodeConfig.EnvValue("forged")),
        ClaudeCodeConfig.EnvKey("ANTHROPIC_API_KEY") -> Some(ClaudeCodeConfig.EnvValue("sk-y"))
      )
    )
    val env2 =
      new java.util.HashMap[String, String](java.util.Map.of("PATH", "/bin", "CLAUDE_CODE_DISABLE_ARTIFACT", "1"))
    ClaudeCode.environment(mine, com.histmersion.domain.automation.agents.LlmCall.MaxTokens(2048), Some(com.histmersion.domain.automation.agents.RunGrant.Token("hmrt_1")), com.histmersion.domain.automation.agents.GenSettings.QuotaUrl("http://q"), env2)
    assert(!env2.containsKey("CLAUDE_CODE_DISABLE_ARTIFACT") && !env2.containsKey("PATH"), env2)
    assertEquals(env2.get("MY_FLAG"), "x")
    assertEquals(env2.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "2048")
    assertEquals(env2.get("HISTMERSION_RUN_TOKEN"), "hmrt_1")
    assert(!env2.containsKey("ANTHROPIC_API_KEY"), "no key unless use-api-key")

  test("probe environment: the configured env too (the probe stays a minimal call)"):
    val env = new java.util.HashMap[String, String]()
    ClaudeCode.probeEnvironment(Ref, com.histmersion.config.ClaudePlanConfig.ProbeMaxTokens(16), env)
    assertEquals(env.get("CLAUDE_CODE_DISABLE_BUNDLED_SKILLS"), "1")
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "16")

  test("argv: without a generated plugin the prompt goes as a file; a web role keeps the web tools"):
    val a = ClaudeCode.argv(
      Cfg,
      com.histmersion.api.automation.Agents.ModelRef.Model("opus"),
      com.histmersion.domain.automation.agents.AgentDef.MaxTurns(1),
      com.histmersion.server.agents.ClaudeCode.SchemaJson("{}"),
      ClaudeCode.Launch.SystemPrompt(com.histmersion.server.agents.ClaudeCode.Launch.SystemPrompt.File("/s")),
      None,
      Nil,
      AllTools,
      web = List("WebSearch", "WebFetch")
    ).map(_.value)
    assertEquals(a(a.indexOf("--system-prompt-file") + 1), "/s")
    assertEquals(a(a.indexOf("--tools") + 1), "WebSearch,WebFetch")
    assertEquals(a(a.indexOf("--allowedTools") + 1), "WebSearch,WebFetch,StructuredOutput")
    assert(!a.contains("--mcp-config") && !a.contains("--disallowedTools") && !a.contains("--plugin-dir"), a)

  test("environment: no Anthropic key unless use-api-key, no claude.ai connectors, the run token and where to report"):
    val env = new java.util.HashMap[String, String](
      java.util.Map.of("ANTHROPIC_API_KEY", "sk-x", "ANTHROPIC_AUTH_TOKEN", "t", "PATH", "/bin")
    )
    ClaudeCode.environment(Cfg, com.histmersion.domain.automation.agents.LlmCall.MaxTokens(2048), Some(com.histmersion.domain.automation.agents.RunGrant.Token("hmrt_1")), com.histmersion.domain.automation.agents.GenSettings.QuotaUrl("http://h/api/agents/quota/claude"), env)
    assert(!env.containsKey("ANTHROPIC_API_KEY") && !env.containsKey("ANTHROPIC_AUTH_TOKEN"), env)
    assertEquals(env.get("PATH"), "/bin")
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "2048")
    assertEquals(env.get("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")
    assertEquals(env.get("HISTMERSION_RUN_TOKEN"), "hmrt_1")
    assertEquals(env.get("HISTMERSION_QUOTA_URL"), "http://h/api/agents/quota/claude")
    val keep = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x"))
    ClaudeCode.environment(Cfg.modify(_.useApiKey).setTo(ClaudeCodeConfig.UseApiKey(true)).modify(_.configDir).setTo(ClaudeCodeConfig.ConfigDir("/c/hm")), com.histmersion.domain.automation.agents.LlmCall.MaxTokens(10), None, com.histmersion.domain.automation.agents.GenSettings.QuotaUrl("u"), keep)
    assertEquals(keep.get("ANTHROPIC_API_KEY"), "sk-x")
    assertEquals(keep.get("CLAUDE_CONFIG_DIR"), "/c/hm")

  test("mcp config: http to the agent endpoint, the step's run token as the bearer"):
    assertEquals(
      ClaudeCode.mcpConfig(com.histmersion.domain.automation.agents.GenSettings.AgentMcpUrl("http://127.0.0.1:18000/mcp/agent"), Some(com.histmersion.domain.automation.agents.RunGrant.Token("hmrt_9"))).noSpaces,
      """{"mcpServers":{"histmersion":{"type":"http","url":"http://127.0.0.1:18000/mcp/agent","headers":{"Authorization":"Bearer hmrt_9"}}}}"""
    )

  test("the schema handed to --json-schema carries no 2020-12 marker (Claude Code validates draft-07)"):
    val s = ClaudeCode.schemaJson(ResponseSchema.derived[ProbeAnswer]())
    assert(!s.value.contains("$schema"), s)
    assert(s.value.contains("\"citekey\"") && s.value.contains("\"title\""), s)

  test("loopback: 127.x, ::1 and localhost are; 0.0.0.0 and a LAN address are not — and claude-code is refused there"):
    assert(
      ClaudeCode.Env.isLoopback(com.histmersion.config.ServerConfig.Host("127.0.0.1")) && ClaudeCode.Env.isLoopback(com.histmersion.config.ServerConfig.Host("localhost")) && ClaudeCode.Env.isLoopback(
        com.histmersion.config.ServerConfig.Host("[::1]")
      )
    )
    assert(!ClaudeCode.Env.isLoopback(com.histmersion.config.ServerConfig.Host("0.0.0.0")) && !ClaudeCode.Env.isLoopback(com.histmersion.config.ServerConfig.Host("192.168.1.5")))
    val dir = Files.createTempDirectory("hm-rt-")
    val lan = CliRuntime(FakeCli.app(dir, "HISTMERSION_BIND" -> "0.0.0.0"), EngineContributions.roster)
    assert(ClaudeCode.client(Cfg, lan, Sonnet).left.exists(_.value.contains("beyond loopback")))
    assert(ClaudeCode.client(Cfg.modify(_.allowNonLoopback).setTo(ClaudeCodeConfig.AllowNonLoopback(true)), lan, Sonnet).isRight)
    assert(
      ClaudeCode
        .client(Cfg, CliRuntime(FakeCli.app(dir), EngineContributions.roster), ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("x")))
        .isLeft
    )

  // ── the stream-json output ─────────────────────────────────────────────────────────────────────

  import ClaudeStreams.*

  test("parse: the trace (text, tool call, tool result), the typed answer, usage with cache tokens counted as input"):
    val o = ClaudeCode.parse(stream(Init, "not json", Turn1, Tool1, Turn2, Success))
    assertEquals(o.modelId, Some("claude-sonnet-4-5"))
    assertEquals(
      o.toolCalls.map(c => (c.toolName, c.input, c.output)),
      Vector(("get_source", """{"citekey":"x2020"}""", "# X 2020"))
    )
    assertEquals(o.usage, TokenUsage(Tokens(450), Tokens(20), Tokens(300), Tokens.Zero, Tokens(50)))
    assertEquals(o.mcpProblem, None)
    val r = ClaudeCode.result(o, com.histmersion.domain.automation.agents.LlmCall.User("the prompt"), ResponseSchema.derived[ProbeAnswer]()).toOption.get
    assertEquals(r.finalAnswer, Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020)))))
    assertEquals(r.finishReason, FinishReason.NaturalStop)
    assertEquals(r.iterations, 2)
    val roles = r.history.entries.map(_.getClass.getSimpleName).toList
    assertEquals(roles, List("UserPrompt", "AssistantResponse", "ToolResult", "AssistantResponse", "AssistantResponse"))

  test("parse: every rate_limit_event window becomes a reading of the Claude plan (§3a P1)"):
    val o = ClaudeCode.parse(stream(Init, RateLimit, Turn2, Success))
    val rs = o.readings.map(r =>
      s"${r.limit} ${r.kind} ${math.round(r.usedPct.value)} ${r.resetsAt.getOrElse(0L)} ${r.rejected} ${r.source.wire}"
    )
    assertEquals(
      rs.toSet,
      Set("claude five_hour 12 1791236400000 false run", "claude seven_day 42 1791522000000 false run")
    )

  test("parse: a rejected representative window without unifiedWindows is a refused reading"):
    val rej =
      """{"type":"rate_limit_event","rate_limit_info":{"status":"rejected","resetsAt":1791236400,"rateLimitType":"five_hour"}}"""
    val r = ClaudeCode.parse(stream(rej)).readings
    assertEquals(r.map(x => (x.kind, x.rejected, x.usedPct)), List(("five_hour", true, 100.0)))

  test("parse: of two result events (a backgrounded subagent) the LAST is the answer (§3a)"):
    val interim = Success
      .replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace("\"result\":\"Done.\"", "\"result\":\"working on it\"")
    val o = ClaudeCode.parse(stream(Init, interim, Success))
    assertEquals(
      ClaudeCode.result(o, com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer,
      Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020))))
    )

  test("result: a JSON answer written as text (no structured_output) is still the answer"):
    val fenced = Success
      .replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace(
        "\"result\":\"Done.\"",
        "\"result\":\"Here it is:\\n```json\\n{\\\"citekey\\\":\\\"x2020\\\",\\\"title\\\":\\\"X\\\",\\\"year\\\":2020}\\n```\""
      )
    val o = ClaudeCode.parse(stream(Init, fenced))
    assertEquals(
      ClaudeCode.result(o, com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer,
      Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020))))
    )

  test("result: a step cut off by max turns is not rescued from its text"):
    val cut = Success
      .replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":null""")
      .replace("\"subtype\":\"success\"", "\"subtype\":\"error_max_turns\"")
      .replace("\"result\":\"Done.\"", "\"result\":\"{\\\"citekey\\\":\\\"x2020\\\",\\\"title\\\":\\\"X\\\"}\"")
    val o = ClaudeCode.parse(stream(Init, cut))
    assert(ClaudeCode.result(o, com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).toOption.get.finalAnswer.isLeft)

  test("jsonInText: a fence wins, else first brace to last; prose and arrays are not answers"):
    assertEquals(
      ClaudeCode.jsonInText(com.histmersion.server.agents.ClaudeCode.Result.Text("a\n```json\n{\"x\":1}\n```\nb {not}")).flatMap(_.hcursor.get[Int]("x").toOption),
      Some(1)
    )
    assertEquals(
      ClaudeCode.jsonInText(com.histmersion.server.agents.ClaudeCode.Result.Text("so: {\"x\": {\"y\": 2}} done")).flatMap(_.hcursor.downField("x").get[Int]("y").toOption),
      Some(2)
    )
    assertEquals(ClaudeCode.jsonInText(com.histmersion.server.agents.ClaudeCode.Result.Text("no json here")), None)
    assertEquals(ClaudeCode.jsonInText(com.histmersion.server.agents.ClaudeCode.Result.Text("[1, 2]")), None)
    assertEquals(ClaudeCode.jsonInText(com.histmersion.server.agents.ClaudeCode.Result.Text("{broken")), None)

  test("parse: the quota-guard mod's abort is a success with an empty answer and terminal_reason aborted_streaming"):
    val o = ClaudeCode.parse(stream(Init, Aborted))
    assert(o.result.exists(ClaudeCode.abortedByGuard), o.result)
    assert(!ClaudeCode.parse(stream(Success)).result.exists(ClaudeCode.abortedByGuard))

  test("the plan's usage limit is told apart from an error, with its reset time when the CLI gives one"):
    val lim =
      """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val l = ClaudeCode.result(ClaudeCode.parse(stream(lim)), com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).left.toOption.get
    assertEquals(l.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    val other = lim.replace("Claude AI usage limit reached|1759683600", "API Error: 500")
    assert(
      ClaudeCode.result(ClaudeCode.parse(stream(other)), com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).isRight,
      "an error is not a limit"
    )

  test("a denied tool and an MCP server that did not connect are named in the failure"):
    val init = Init.replace("connected", "failed")
    val res =
      """{"type":"result","subtype":"error_during_execution","is_error":true,"num_turns":1,"result":"","permission_denials":[{"tool_name":"mcp__histmersion__query"}],"usage":{}}"""
    val r =
      ClaudeCode.result(ClaudeCode.parse(stream(init, res)), com.histmersion.domain.automation.agents.LlmCall.User("u"), ResponseSchema.derived[ProbeAnswer]()).toOption.get
    r.finishReason match
      case FinishReason.Error(m) =>
        assert(m.contains("agent MCP server is failed") && m.contains("mcp__histmersion__query"), m)
      case other => fail(s"not an error: $other")

  test("#992 the model that served: the modelUsage entry with the most output, every one as its own call"):
    val two = Success.replace(
      """"modelUsage":{"claude-sonnet-4-5":{"inputTokens":100,"outputTokens":20,"cacheReadInputTokens":300,"cacheCreationInputTokens":50,"costUSD":0.0123}}""",
      """"modelUsage":{"claude-haiku-4-5":{"inputTokens":40,"outputTokens":3},"claude-haiku-5-5":{"inputTokens":60,"outputTokens":17}}"""
    )
    val o = ClaudeCode.parse(stream(Init, two))
    assertEquals(o.modelId, Some("claude-haiku-5-5"), "not the first key, nor the init line's model")
    assertEquals(o.served.map(_._1), List("claude-haiku-5-5", "claude-haiku-4-5"))
    val r = ClaudeCode.result(o, com.histmersion.domain.automation.agents.LlmCall.User("p"), ResponseSchema.derived[ProbeAnswer]()).toOption.get
    assertEquals(
      r.llmCalls.map(c => (c.model, c.usage.outputTokens.value)),
      Seq((Some("claude-haiku-5-5"), 17L), (Some("claude-haiku-4-5"), 3L))
    )
    assertEquals(LlmClient.servedBy(r.llmCalls), Some("claude-haiku-5-5 + claude-haiku-4-5"))
    // no modelUsage: the init line's model, one call
    val bare = ClaudeCode.parse(stream(Init, Aborted))
    assertEquals((bare.modelId, bare.served), (Some("claude-sonnet-4-5"), Nil))
    assertEquals(LlmClient.servedBy(Nil), None)
    assertEquals(
      LlmClient.servedBy(
        Seq(
          LlmCallUsage(Some("a"), TokenUsage.Zero),
          LlmCallUsage(Some(" a "), TokenUsage.Zero),
          LlmCallUsage(None, TokenUsage.Zero)
        )
      ),
      Some("a")
    )

  test("first call: the first top-level assistant event's usage, input + cache read + cache write (2.1.294 shapes)"):
    val o = ClaudeCode.parse(stream(Init294, Synthetic294, Sub294, First294a, First294b, Tool294, Second294, Success))
    assertEquals(
      o.firstCall,
      Some(3L + 5120L + 1840L),
      "not the synthetic message, not the subagent's, not the second call"
    )
    assertEquals(o.toolCalls.map(_.toolName), Vector("get_source"))
    // a stream with no assistant event (the plan's limit at once), or only zeros, says nothing
    assertEquals(ClaudeCode.parse(stream(Init294, Success)).firstCall, None)
    assertEquals(ClaudeCode.parse(stream(Init, Turn1, Tool1, Turn2, Success)).firstCall, Some(10L), "2.1.289's shape")
    assertEquals(
      ClaudeCode.firstCallTokens(
        io.circe.parser.parse("""{"usage":{"input_tokens":0,"output_tokens":0}}""").toOption.get
      ),
      None
    )

  test("first call probe: an HTTP loop's first response; a CLI's report wins over the whole process's usage"):
    def resp(in: Long) = AgentResponse(
      "",
      Nil,
      StopReason.EndTurn,
      Some(TokenUsage(Tokens(in), Tokens(1), Tokens.Zero, Tokens.Zero)),
      None
    )
    val ctx = LlmCallContext(ConversationHistory.withInitialPrompt("u"), false, IterationInfo(1, 1))
    val http = FirstCallProbe()
    List(4200L, 6100L, 8000L).foreach(n => http.aroundLlmCall(ctx)(resp(n)))
    assertEquals(http.tokens, Some(4200L))
    val cli = FirstCallProbe()
    cli.aroundLlmCall(ctx) { FirstCallProbe.report(List(cli), Some(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.FirstCallTokens(5000L))); resp(90000L) }
    assertEquals(cli.tokens, Some(5000L), "the process's total is never taken for its first call")
    val unknown = FirstCallProbe()
    unknown.aroundLlmCall(ctx) { FirstCallProbe.report(List(unknown), None); resp(90000L) }
    assertEquals(unknown.tokens, None)
    assertEquals(FirstCallProbe().tokens, None, "no call, no reading")

  // ── the client against a fake executable, on a generated tree ─────────────────────────────────

  private def runtime(sink: FakeCli.Sink): (CliRuntime, AgentDef) =
    val dir = Files.createTempDirectory("hm-agents-")
    val rt = CliRuntime(FakeCli.app(dir), EngineContributions.roster)
    rt.pools = Some(sink)
    val gen = rt.trees.generate(Generated.known(rt.definitions), List(AgentRuntime.ClaudeCode))
    assert(gen.isRight, gen)
    (rt, rt.definitions.agent(StepAgent.Skeptic).toOption.get)

  private val getSource = AgentTool.fromFunction[FollowupsInput]("get_source", "read a source")(_ => "")

  private def call(agent: AgentDef, interceptors: List[AgentInterceptor[Identity]] = Nil) =
    LlmCall[ProbeAnswer](
      com.histmersion.domain.automation.agents.LlmCall.System("SYSTEM PROMPT"),
      com.histmersion.domain.automation.agents.LlmCall.User("USER <source-text>data</source-text>"),
      List(getSource),
      ResponseSchema.derived[ProbeAnswer](),
      maxIterations = com.histmersion.domain.automation.agents.LlmCall.MaxIterations(3),
      maxTokens = com.histmersion.domain.automation.agents.LlmCall.MaxTokens(777),
      interceptors,
      Some(agent),
      Some(RunScope(com.histmersion.domain.automation.agents.RunScope.Run("run-1"), com.histmersion.domain.automation.agents.RunScope.Step(4)))
    )

  test(
    "the client end to end: the generated agent and settings, the run token (revoked after), stdin, an empty cwd, readings recorded"
  ):
    val fake = FakeCli("claude", List(Init, Turn1, Tool1, RateLimit, Turn2, Success))
    val sink = FakeCli.Sink()
    val (rt, skeptic) = runtime(sink)
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    val counting = new AgentInterceptor[Identity]:
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
        val _ = calls.incrementAndGet(); next
    val r = ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(fake.script.toString)), rt).run(call(skeptic, List(counting)))
    assertEquals(r.finalAnswer, Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020)))))
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
    assertEquals(rt.tokens.check(RunTokens.Token(token)), None, "the token dies with the step")
    assertEquals(fake.env("ANTHROPIC_API_KEY"), "<unset>")
    assertEquals(fake.env("ENABLE_CLAUDEAI_MCP_SERVERS"), "false")
    assert(fake.cwdWasEmpty, "the CLI runs in an empty work dir")
    val kinds = sink.recorded.asScala.toList.filter(_._1 == PoolId.ClaudePlan).map(_._2.kind).toSet
    assertEquals(kinds, Set("five_hour", "seven_day"))

  test(
    "the client on the reference config: the env defaults and --disable-slash-commands reach the CLI, the first call is reported"
  ):
    val fake = FakeCli("claude", List(Init294, First294a, First294b, Tool294, Second294, Success))
    val (rt, skeptic) = runtime(FakeCli.Sink())
    val first = FirstCallProbe()
    val r = ClaudeCodeClient(Sonnet, Ref.modify(_.command).setTo(ClaudeCodeConfig.Command(fake.script.toString)).modify(_.timeoutSeconds).setTo(ClaudeCodeConfig.TimeoutSeconds(30)), rt)
      .run(call(skeptic, List(first)))
    assertEquals(r.finalAnswer, Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020)))))
    assert(fake.args.contains("--disable-slash-commands"), fake.args)
    assertEquals(fake.env("CLAUDE_CODE_DISABLE_ARTIFACT"), "1")
    assertEquals(first.tokens, Some(6963L), "the first call's input, not the run's total")
    assertEquals(r.iterations, 2, "num_turns")

  test("the client: a pool below its floor mid-step STOPS the process (no waiting for the answer) — the plan's limit"):
    val fake = FakeCli("claude", List(Init, Turn1, RateLimit), sleepAfter = 60)
    val sink = FakeCli.Sink(block = Some((com.histmersion.domain.automation.agents.PoolSink.BlockedReason("five_hour: 8% left, floor 25%"), Some(com.histmersion.domain.automation.agents.PlanQuotaRepo.EpochMillis(1791236400000L)))))
    val (rt, skeptic) = runtime(sink)
    val t0 = System.nanoTime()
    val e = intercept[ModelLimitReached](
      ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(fake.script.toString)), rt).run(call(skeptic))
    )
    assert((System.nanoTime() - t0) / 1000000000L < 25, "stopped, not timed out")
    assert(e.getMessage.contains("quota guard") && e.getMessage.contains("floor 25%"), e.getMessage)
    assertEquals(e.until, Some(java.time.Instant.ofEpochMilli(1791236400000L)))

  test("the client: the mod's abort (aborted_streaming, empty answer) ends the step as the plan's limit"):
    val fake = FakeCli("claude", List(Init, Turn1, Aborted))
    val (rt, skeptic) = runtime(FakeCli.Sink())
    val e = intercept[ModelLimitReached](
      ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(fake.script.toString)), rt).run(call(skeptic))
    )
    assert(e.getMessage.contains("quota-guard mod"), e.getMessage)

  test(
    "the client: a usage limit is ModelLimitReached; no result line is a provider error; a missing executable says how to fix it"
  ):
    val lim =
      """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val f1 = FakeCli("claude", List(lim))
    val (rt, skeptic) = runtime(FakeCli.Sink())
    val e = intercept[ModelLimitReached](
      ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(f1.script.toString)), rt).run(call(skeptic))
    )
    assertEquals(e.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    val f2 = FakeCli("claude", Nil, code = 3)
    val io = intercept[java.io.IOException](
      ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command(f2.script.toString)), rt).run(call(skeptic))
    )
    assert(io.getMessage.contains("exited 3 without a result"), io.getMessage)
    val missing = intercept[java.io.IOException](
      ClaudeCodeClient(Sonnet, Cfg.modify(_.command).setTo(ClaudeCodeConfig.Command("/nonexistent/claude")), rt).run(call(skeptic))
    )
    assert(missing.getMessage.contains("HISTMERSION_AGENTS_CLAUDE_COMMAND"), missing.getMessage)

/** Recorded `claude -p --output-format stream-json` lines (2.1.289; the rate-limit event and the abort as the probes of
  * docs/research-agent-runtimes.md §3a saw them).
  */
object ClaudeStreams:
  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  val Init =
    """{"type":"system","subtype":"init","model":"claude-sonnet-4-5","apiKeySource":"none","mcp_servers":[{"name":"histmersion","status":"connected"}]}"""
  val Turn1 =
    """{"type":"assistant","message":{"id":"m1","content":[{"type":"text","text":"Looking it up."},{"type":"tool_use","id":"toolu_1","name":"mcp__histmersion__get_source","input":{"citekey":"x2020"}}],"usage":{"input_tokens":10,"output_tokens":5}},"parent_tool_use_id":null}"""
  val Tool1 =
    """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"toolu_1","content":[{"type":"text","text":"# X 2020"}]}]},"parent_tool_use_id":null}"""
  val Turn2 =
    """{"type":"assistant","message":{"id":"m2","content":[{"type":"text","text":"Done."}]},"parent_tool_use_id":null}"""
  val RateLimit =
    """{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1791236400,"rateLimitType":"five_hour","overageStatus":"rejected","overageDisabledReason":"org_level_disabled","isUsingOverage":false,"unifiedWindows":{"five_hour":{"utilization":0.12,"resetsAt":1791236400},"seven_day":{"utilization":0.42,"resetsAt":1791522000}}},"uuid":"u","session_id":"s"}"""
  val Success =
    """{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":"Done.","structured_output":{"citekey":"x2020","title":"X","year":2020},"total_cost_usd":0.0123,"usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":300,"cache_creation_input_tokens":50},"modelUsage":{"claude-sonnet-4-5":{"inputTokens":100,"outputTokens":20,"cacheReadInputTokens":300,"cacheCreationInputTokens":50,"costUSD":0.0123}},"permission_denials":[]}"""
  val Aborted =
    """{"type":"result","subtype":"success","is_error":false,"result":"","num_turns":2,"stop_reason":"tool_use","terminal_reason":"aborted_streaming","usage":{"input_tokens":5,"output_tokens":1}}"""

  // ── 2.1.294: the event shapes `claude -p --output-format stream-json --verbose` printed for a headless
  // --agent run (recorded offline against a stand-in API, scripts/agents/claude-overhead.sh; usage
  // numbers as a cached second call has them). Every assistant event of one API message repeats the
  // message's `message_start` usage; a `<synthetic>` message is the CLI's own, not a call.
  val Init294 =
    """{"type":"system","subtype":"init","cwd":"/tmp/histmersion-claude-1/work","session_id":"s","tools":["StructuredOutput","mcp__histmersion__get_source"],"mcp_servers":[{"name":"histmersion","status":"connected"}],"model":"claude-sonnet-4-5","permissionMode":"dontAsk","slash_commands":[],"apiKeySource":"none","claude_code_version":"2.1.294","output_style":"default","agents":["claude","general-purpose"],"skills":[],"plugins":[{"name":"histmersion-agents","path":"/g/headless","source":"histmersion-agents@inline","version":"0.3.0"}],"capabilities":["interrupt_receipt_v1"],"analytics_disabled":false,"product_feedback_disabled":false,"uuid":"u","memory_paths":{"auto":"/c/projects/x/memory/"},"messaging_socket_path":null,"fast_mode_state":"off","fast_mode_disabled_reason":"sdk_opt_in_required","per_turn_effort_active":false,"view_mode":"default"}"""
  val Synthetic294 =
    """{"type":"assistant","message":{"id":"x","type":"message","role":"assistant","model":"<synthetic>","content":[{"type":"text","text":"(no content)"}],"stop_reason":"stop_sequence","stop_sequence":"","usage":{"input_tokens":0,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}},"parent_tool_use_id":null,"session_id":"s","uuid":"u0"}"""
  val Sub294 =
    """{"type":"assistant","message":{"id":"msg_sub","type":"message","role":"assistant","model":"claude-haiku-4-5","content":[{"type":"text","text":"sub"}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":900,"output_tokens":1,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}},"parent_tool_use_id":"toolu_sub","session_id":"s","uuid":"u1"}"""
  val First294a =
    """{"type":"assistant","message":{"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[{"type":"text","text":"Looking it up."}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":3,"cache_creation_input_tokens":5120,"cache_read_input_tokens":1840,"cache_creation":{"ephemeral_5m_input_tokens":5120,"ephemeral_1h_input_tokens":0},"output_tokens":8,"service_tier":"standard"},"context_management":null},"parent_tool_use_id":null,"session_id":"s","uuid":"u2","timestamp":"2026-10-08T20:30:19.007Z"}"""
  val First294b =
    """{"type":"assistant","message":{"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[{"type":"tool_use","id":"toolu_01","name":"mcp__histmersion__get_source","input":{"citekey":"x2020"}}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":3,"cache_creation_input_tokens":5120,"cache_read_input_tokens":1840,"cache_creation":{"ephemeral_5m_input_tokens":5120,"ephemeral_1h_input_tokens":0},"output_tokens":8,"service_tier":"standard"},"context_management":null},"parent_tool_use_id":null,"session_id":"s","uuid":"u3","timestamp":"2026-10-08T20:30:19.010Z"}"""
  val Tool294 =
    """{"type":"user","message":{"role":"user","content":[{"tool_use_id":"toolu_01","type":"tool_result","content":[{"type":"text","text":"# X 2020"}]}]},"parent_tool_use_id":null,"session_id":"s","uuid":"u4","timestamp":"2026-10-08T20:30:19.400Z","tool_use_result":[{"type":"text","text":"# X 2020"}]}"""
  val Second294 =
    """{"type":"assistant","message":{"id":"msg_02","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[{"type":"tool_use","id":"toolu_02","name":"StructuredOutput","input":{"citekey":"x2020","title":"X","year":2020}}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":2,"cache_creation_input_tokens":40,"cache_read_input_tokens":6960,"output_tokens":30,"service_tier":"standard"},"context_management":null},"parent_tool_use_id":null,"session_id":"s","uuid":"u5"}"""
