package com.histmersion.server.agents

import com.histmersion.core.{AppConfig, ClaudeCodeConfig}
import com.histmersion.core.Agents.{ModelRef, ProbeAnswer, ProviderKind}
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The `claude-code` provider without a model: the argument vector, the environment, the stream-json
  * output read back, the plan's usage limit — and the whole client against a FAKE `claude` executable
  * (a shell script that records what it was given and prints a canned run). No network, no login,
  * nothing spent. */
class ClaudeCodeSuite extends munit.FunSuite:

  private val Cfg = ClaudeCodeConfig(command = "claude", configDir = "", extraArgs = "--restricted", useApiKey = false,
    allowNonLoopback = false, mcpUrl = "", timeoutSeconds = 30, limitCooldownMinutes = 60)
  private val Env = ClaudeCodeEnv("http://127.0.0.1:8000/mcp/agent", None, loopback = true)
  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val AllTools = ToolName.values.toList.map(_.wire)

  test("argv: print mode, stream-json, the schema, no built-in tools, our MCP only, the step's allowlist and nothing else"):
    val a = ClaudeCode.argv(Cfg, "sonnet", 4, """{"type":"object"}""", "/t/system.md", Some("/t/mcp.json"),
      List("get_source", "search"), AllTools)
    assertEquals(a.take(2), List("claude", "-p"))
    def after(flag: String) = a.drop(a.indexOf(flag) + 1).head
    assertEquals(after("--output-format"), "stream-json")
    assertEquals(after("--model"), "sonnet")
    assertEquals(after("--max-turns"), "4")
    assertEquals(after("--json-schema"), """{"type":"object"}""")
    assertEquals(after("--system-prompt-file"), "/t/system.md")
    assertEquals(after("--tools"), "", "built-in tools are off")
    assertEquals(after("--permission-mode"), "dontAsk")
    assertEquals(after("--mcp-config"), "/t/mcp.json")
    assertEquals(after("--allowedTools"), "mcp__histmersion__get_source,mcp__histmersion__search")
    val denied = after("--disallowedTools").split(',').toSet
    assertEquals(denied, AllTools.filterNot(Set("get_source", "search")).map(ClaudeCode.toolId).toSet)
    assert(a.contains("--strict-mcp-config") && a.contains("--no-session-persistence") && a.contains("--disable-slash-commands"))
    assertEquals(after("--settings"), """{"disableAllHooks":true}""")
    assertEquals(a.last, "--restricted", "extra-args go last")
    assert(!a.contains("--bare"), "bare mode never reads the subscription login")

  test("argv: a step with no tools gets no MCP server at all, and no allow or deny list"):
    val a = ClaudeCode.argv(Cfg.copy(extraArgs = ""), "opus", 1, "{}", "/s", None, Nil, AllTools)
    assert(!a.contains("--mcp-config") && !a.contains("--allowedTools") && !a.contains("--disallowedTools"), a)
    assert(a.contains("--strict-mcp-config"), "the user's own MCP servers stay out")

  test("environment: an Anthropic key is REMOVED unless use-api-key (in -p a present key wins and bills the API)"):
    val env = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x", "ANTHROPIC_AUTH_TOKEN", "t", "PATH", "/bin"))
    ClaudeCode.environment(Cfg, 2048, env)
    assert(!env.containsKey("ANTHROPIC_API_KEY") && !env.containsKey("ANTHROPIC_AUTH_TOKEN"), env)
    assertEquals(env.get("PATH"), "/bin")
    assertEquals(env.get("CLAUDE_CODE_MAX_OUTPUT_TOKENS"), "2048")
    assert(!env.containsKey("CLAUDE_CONFIG_DIR"))
    val keep = new java.util.HashMap[String, String](java.util.Map.of("ANTHROPIC_API_KEY", "sk-x"))
    ClaudeCode.environment(Cfg.copy(useApiKey = true, configDir = "/c/hm"), 10, keep)
    assertEquals(keep.get("ANTHROPIC_API_KEY"), "sk-x")
    assertEquals(keep.get("CLAUDE_CONFIG_DIR"), "/c/hm")

  test("mcp config: http to the agent endpoint; the server's key as a bearer header only when there is one"):
    val plain = ClaudeCode.mcpConfig(Env).noSpaces
    assertEquals(plain, """{"mcpServers":{"histmersion":{"type":"http","url":"http://127.0.0.1:8000/mcp/agent"}}}""")
    val keyed = ClaudeCode.mcpConfig(Env.copy(bearer = Some("k1"))).noSpaces
    assert(keyed.contains(""""headers":{"Authorization":"Bearer k1"}"""), keyed)

  test("the schema handed to --json-schema carries no 2020-12 marker (Claude Code validates draft-07)"):
    val s = ClaudeCode.schemaJson(ResponseSchema.derived[ProbeAnswer]())
    assert(!s.contains("$schema"), s)
    assert(s.contains("\"citekey\"") && s.contains("\"title\""), s)

  test("loopback: 127.x, ::1 and localhost are; 0.0.0.0 and a LAN address are not — and claude-code is refused there"):
    assert(ClaudeCodeEnv.isLoopback("127.0.0.1") && ClaudeCodeEnv.isLoopback("localhost") && ClaudeCodeEnv.isLoopback("[::1]"))
    assert(!ClaudeCodeEnv.isLoopback("0.0.0.0") && !ClaudeCodeEnv.isLoopback("192.168.1.5"))
    val lan = Env.copy(loopback = false)
    assert(ClaudeCode.client(Cfg, lan, Sonnet).left.exists(_.contains("beyond loopback")))
    assert(ClaudeCode.client(Cfg.copy(allowNonLoopback = true), lan, Sonnet).isRight)
    assert(ClaudeCode.client(Cfg, Env, ModelRef(ProviderKind.Local, "x")).isLeft)

  test("env of a server: the MCP URL follows public-url unless set; the API key becomes the bearer"):
    val base = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_PUBLIC_URL", "http://localhost:8123/", "HISTMERSION_API_KEY", "key9", "HISTMERSION_BIND", "127.0.0.1")))
    val e = ClaudeCodeEnv.of(base)
    assertEquals(e.mcpUrl, "http://localhost:8123/mcp/agent")
    assertEquals(e.bearer, Some("key9"))
    assert(e.loopback)

  // ── the stream-json output ─────────────────────────────────────────────────────────────────────

  private val Init = """{"type":"system","subtype":"init","model":"claude-sonnet-4-5","mcp_servers":[{"name":"histmersion","status":"connected"}]}"""
  private val Turn1 = """{"type":"assistant","message":{"id":"m1","content":[{"type":"text","text":"Looking it up."},{"type":"tool_use","id":"toolu_1","name":"mcp__histmersion__get_source","input":{"citekey":"x2020"}}],"usage":{"input_tokens":10,"output_tokens":5}},"parent_tool_use_id":null}"""
  private val Tool1 = """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"toolu_1","content":[{"type":"text","text":"# X 2020"}]}]},"parent_tool_use_id":null}"""
  private val Turn2 = """{"type":"assistant","message":{"id":"m2","content":[{"type":"text","text":"Done."}]},"parent_tool_use_id":null}"""
  private val Success = """{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":"Done.","structured_output":{"citekey":"x2020","title":"X","year":2020},"total_cost_usd":0.0123,"usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":300,"cache_creation_input_tokens":50},"modelUsage":{"claude-sonnet-4-5":{"inputTokens":100,"outputTokens":20,"cacheReadInputTokens":300,"cacheCreationInputTokens":50,"costUSD":0.0123}},"permission_denials":[]}"""

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
    assert(r.history.entries.head == ConversationEntry.UserPrompt("the prompt"))

  test("parse: an answer that does not fit the schema, or none at all, is a failure the validator sees"):
    val bad = Success.replace(""""structured_output":{"citekey":"x2020","title":"X","year":2020}""", """"structured_output":{"citekey":1}""")
    val r1 = ClaudeCode.result(ClaudeCode.parse(List(bad)), "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get
    assert(r1.finalAnswer.left.exists(_.isInstanceOf[AgentParseError]), r1.finalAnswer)
    val turns = """{"type":"result","subtype":"error_max_turns","is_error":true,"num_turns":4,"result":"","usage":{"input_tokens":5,"output_tokens":1}}"""
    val r2 = ClaudeCode.result(ClaudeCode.parse(List(turns)), "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get
    assertEquals(r2.finishReason, FinishReason.MaxIterations)
    assert(r2.finalAnswer.isLeft)
    assertEquals(r2.usage.inputTokens, Tokens(5), "a failed run's usage still counts")

  test("the plan's usage limit is told apart from an error, with its reset time when the CLI gives one"):
    val lim = """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val l = ClaudeCode.result(ClaudeCode.parse(List(lim)), "u", ResponseSchema.derived[ProbeAnswer]()).left.toOption.get
    assertEquals(l.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    val noTime = lim.replace("Claude AI usage limit reached|1759683600", "You've hit your limit · resets 3pm")
    assertEquals(ClaudeCode.result(ClaudeCode.parse(List(noTime)), "u", ResponseSchema.derived[ProbeAnswer]()).left.toOption.map(_.until), Some(None))
    val other = lim.replace("Claude AI usage limit reached|1759683600", "API Error: 500")
    assert(ClaudeCode.result(ClaudeCode.parse(List(other)), "u", ResponseSchema.derived[ProbeAnswer]()).isRight, "an error is not a limit")

  test("a denied tool and an MCP server that did not connect are named in the failure"):
    val init = Init.replace("connected", "failed")
    val res = """{"type":"result","subtype":"error_during_execution","is_error":true,"num_turns":1,"result":"","permission_denials":[{"tool_name":"mcp__histmersion__query"}],"usage":{}}"""
    val r = ClaudeCode.result(ClaudeCode.parse(List(init, res)), "u", ResponseSchema.derived[ProbeAnswer]()).toOption.get
    r.finishReason match
      case FinishReason.Error(m) => assert(m.contains("agent MCP server is failed") && m.contains("mcp__histmersion__query"), m)
      case other => fail(s"not an error: $other")

  // ── the client against a fake executable ──────────────────────────────────────────────────────

  /** A fake `claude`: records its arguments (one per line), its stdin and two environment values,
    * prints `out` and exits with `code`. */
  private def fake(out: List[String], code: Int = 0): (Path, Path) =
    val dir = Files.createTempDirectory("hm-fake-claude-")
    val outFile = Files.write(dir.resolve("out.jsonl"), out.asJava)
    val script = dir.resolve("claude")
    Files.writeString(script,
      s"""#!/bin/bash
         |for a in "$$@"; do printf '%s\\n' "$$a"; done > '${dir}/args'
         |cat > '${dir}/stdin'
         |echo "max=$${CLAUDE_CODE_MAX_OUTPUT_TOKENS:-unset} cwd=$$(pwd)" > '${dir}/env'
         |cat '$outFile'
         |exit $code
         |""".stripMargin)
    script.toFile.setExecutable(true)
    (script, dir)

  private def call(tools: List[AgentTool[Identity, ?]], interceptors: List[AgentInterceptor[Identity]] = Nil) =
    LlmCall[ProbeAnswer]("SYSTEM PROMPT", "USER <source-text>data</source-text>", tools,
      ResponseSchema.derived[ProbeAnswer](), maxIterations = 3, maxTokens = 777, interceptors)

  private val getSource = AgentTool.fromFunction[FollowupsInput]("get_source", "read a source")(_ => "")

  test("the client end to end against a fake claude: stdin is the user turn, the answer is typed, usage recorded, the work dir removed"):
    val (script, dir) = fake(List(Init, Turn1, Tool1, Turn2, Success))
    @volatile var calls = 0 // counted by the interceptor below
    val counting = new AgentInterceptor[Identity]:
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
        calls += 1; next
    val c = ClaudeCodeClient(Sonnet, Cfg.copy(command = script.toString, extraArgs = ""), Env)
    val r = c.run(call(List(getSource), List(counting)))
    assertEquals(r.finalAnswer, Right(ProbeAnswer("x2020", "X", Some(2020))))
    assertEquals(r.usage.outputTokens, Tokens(20))
    assertEquals(calls, 1, "the interceptors (the LLM-slot semaphore, latency) see the process as one call")
    assertEquals(Files.readString(dir.resolve("stdin")), "USER <source-text>data</source-text>", "untrusted data on stdin, never in argv")
    val args = Files.readAllLines(dir.resolve("args")).asScala.toList
    assert(!args.exists(_.contains("<source-text>")), args)
    assertEquals(args(args.indexOf("--allowedTools") + 1), "mcp__histmersion__get_source")
    val sysFile = Path.of(args(args.indexOf("--system-prompt-file") + 1))
    val env = Files.readString(dir.resolve("env"))
    assert(env.contains("max=777"), env)
    assert(!Files.exists(sysFile), "the per-call work dir (system prompt, MCP config) is deleted")
    assert(env.contains(sysFile.getParent.getFileName.toString), s"the CLI runs in the empty work dir: $env")

  test("the client: a usage limit is ModelLimitReached; no result line is a provider error with the CLI's stderr"):
    val lim = """{"type":"result","subtype":"success","is_error":true,"num_turns":0,"result":"Claude AI usage limit reached|1759683600","usage":{}}"""
    val (s1, _) = fake(List(lim))
    val e = intercept[ModelLimitReached](ClaudeCodeClient(Sonnet, Cfg.copy(command = s1.toString, extraArgs = ""), Env).run(call(Nil)))
    assertEquals(e.until, Some(java.time.Instant.ofEpochSecond(1759683600L)))
    assertEquals(e.model, Sonnet)
    val (s2, _) = fake(Nil, code = 3)
    val io = intercept[java.io.IOException](ClaudeCodeClient(Sonnet, Cfg.copy(command = s2.toString, extraArgs = ""), Env).run(call(Nil)))
    assert(io.getMessage.contains("exited 3 without a result"), io.getMessage)
    val missing = intercept[java.io.IOException](
      ClaudeCodeClient(Sonnet, Cfg.copy(command = "/nonexistent/claude", extraArgs = ""), Env).run(call(Nil)))
    assert(missing.getMessage.contains("HISTMERSION_AGENTS_CLAUDE_COMMAND"), missing.getMessage)
