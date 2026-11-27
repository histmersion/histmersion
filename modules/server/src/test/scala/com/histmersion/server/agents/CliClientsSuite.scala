package com.histmersion
package server.agents

import com.histmersion.domain.automation.agents.SttpAiJson
import com.histmersion.domain.automation.agents.SttpAiJson.given
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.{LlmCall, ModelLimitReached, RunScope, StepAgent}
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.config.{CodexConfig, OpencodeConfig}
import com.histmersion.api.automation.Agents.{ModelRef, ProbeAnswer, ProviderKind}
import com.histmersion.api.automation.PlanQuota.PoolId
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*

import java.nio.file.{Files, Path}
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{tolerated, LintRule}

/** The `codex` and `opencode` providers against FAKE executables replaying recorded event streams on the generated
  * trees: the argument vector, the isolation environment (no API key, the run token, the generated homes), the empty
  * working directory, the typed answer, usage, the trace, a plan limit.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CliClientsSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val CodexCfg = CodexConfig(
    CodexConfig.Command("codex"),
    CodexConfig.Home(""),
    useApiKey = CodexConfig.UseApiKey(false),
    timeoutSeconds = CodexConfig.TimeoutSeconds(30),
    appServer = CodexConfig.AppServer(false)
  )
  private val OcCfg = OpencodeConfig(
    OpencodeConfig.Command("opencode"),
    timeoutSeconds = OpencodeConfig.TimeoutSeconds(30),
    chatgptLogin = OpencodeConfig.ChatgptLogin(true)
  )

  private def runtime(sink: FakeCli.Sink = FakeCli.Sink()): (CliRuntime, Path) =
    val dir = Files.createTempDirectory("hm-cli-")
    val rt = CliRuntime(FakeCli.app(dir), EngineContributions.roster)
    rt.pools = Some(sink)
    assert(rt.trees.generate(Generated.known(rt.definitions)).isRight)
    (rt, dir)

  private def call(rt: CliRuntime, s: StepAgent) =
    val a = rt.definitions.agent(s).toOption.get
    LlmCall[ProbeAnswer](
      LlmCall.System("SYSTEM"),
      LlmCall.User("USER <article>data</article>"),
      Nil,
      ResponseSchema.derived[ProbeAnswer](),
      maxIterations = LlmCall.MaxIterations(3),
      maxTokens = LlmCall.MaxTokens(500),
      agent = Some(a),
      scope = Some(RunScope(RunScope.Run("r"), RunScope.Step(2)))
    )

  // ── codex ──────────────────────────────────────────────────────────────────────────────────────

  private val CodexOk = List(
    """{"type":"thread.started","thread_id":"t1"}""",
    """{"type":"turn.started"}""",
    """{"type":"item.completed","item":{"id":"i1","type":"reasoning","text":"thinking"}}""",
    """{"type":"item.completed","item":{"id":"i2","type":"mcp_tool_call","server":"histmersion","tool":"get_source","arguments":{"citekey":"x2020"},"result":{"content":[{"type":"text","text":"# X 2020"}],"structured_content":null},"error":null,"status":"completed"}}""",
    """{"type":"item.completed","item":{"id":"i3","type":"agent_message","text":"{\"citekey\":\"x2020\",\"title\":\"X\",\"year\":2020}"}}""",
    """{"type":"turn.completed","usage":{"input_tokens":1200,"cached_input_tokens":800,"output_tokens":60,"reasoning_output_tokens":10}}"""
  )

  test(
    "codex: the role's profile, the schema, an empty cwd, CODEX_HOME = the generated home, no API key, the run token"
  ):
    val fake = FakeCli("codex", CodexOk)
    val sink = FakeCli.Sink()
    val (rt, dir) = runtime(sink)
    val r = CodexExecClient(
      ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5.6")),
      CodexCfg.modify(_.command).setTo(CodexConfig.Command(fake.script.toString)),
      rt
    )
      .run(call(rt, StepAgent.Skeptic))
    assertEquals(r.finalAnswer, Right(ProbeAnswer(Citekey("x2020"), Title("X"), Some(Year(2020)))))
    assertEquals(r.usage, TokenUsage(Tokens(1200), Tokens(60), Tokens(800), Tokens.Zero, Tokens.Zero))
    assertEquals(r.toolCalls.map(c => (c.toolName, c.output)).toList, List(("get_source", "# X 2020")))
    assertEquals(fake.args.take(4), List("exec", "--json", "-p", "article-skeptic"))
    assertEquals(fake.after("-m"), "gpt-5.6")
    assert(
      fake.args.contains("--output-schema") && fake.args.contains("--skip-git-repo-check") && fake.args.last == "-"
    )
    // the schema file is the strict form: Codex sends it with `strict: true`, and refuses an open object
    assertEquals(fake.outputSchema, Some(Codex.outputSchemaJson(ResponseSchema.derived[ProbeAnswer]())))
    assert(fake.outputSchema.exists(_.contains("\"additionalProperties\":false")), fake.outputSchema)
    assert(!fake.args.contains("--ephemeral"), "the rollout keeps the run's rate-limit snapshot")
    assertEquals(fake.env("CODEX_HOME"), dir.resolve("codex/home").toString)
    assertEquals(fake.env("OPENAI_API_KEY"), "<unset>")
    assert(fake.env("HISTMERSION_RUN_TOKEN").startsWith("hmrt_"))
    assert(fake.cwdWasEmpty, "no project AGENTS.md or .codex/ can be read")
    assertEquals(fake.stdin, "USER <article>data</article>")
    assertEquals(sink.refreshed, 1, "the ChatGPT plan is read after the step")

  test("codex: a usage limit is the plan's limit; a missing profile says to generate"):
    val lim =
      List("""{"type":"turn.failed","error":{"message":"You've hit your usage limit. Try again at 9:45 AM."}}""")
    val fake = FakeCli("codex", lim)
    val (rt, _) = runtime()
    val e = intercept[ModelLimitReached](
      CodexExecClient(
        ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5.6")),
        CodexCfg.modify(_.command).setTo(CodexConfig.Command(fake.script.toString)),
        rt
      )
        .run(call(rt, StepAgent.Skeptic))
    )
    assert(e.getMessage.contains("usage limit"), e.getMessage)
    val bare = CliRuntime(FakeCli.app(Files.createTempDirectory("hm-empty-")), EngineContributions.roster)
    val io = intercept[java.io.IOException](
      CodexExecClient(ModelRef(ProviderKind.Codex, ModelRef.Model("x")), CodexCfg, bare)
        .run(call(bare, StepAgent.Skeptic))
    )
    assert(io.getMessage.contains("histmersion agent generate"), io.getMessage)

  test("codex: an answer that is not the schema's JSON is a failure the validator sees, not a crash"):
    val fake = FakeCli(
      "codex",
      List("""{"type":"item.completed","item":{"id":"i","type":"agent_message","text":"I could not do it"}}""")
    )
    val (rt, _) = runtime()
    val r = CodexExecClient(
      ModelRef(ProviderKind.Codex, ModelRef.Model("m")),
      CodexCfg.modify(_.command).setTo(CodexConfig.Command(fake.script.toString)),
      rt
    )
      .run(call(rt, StepAgent.Skeptic))
    assert(r.finalAnswer.isLeft)

  test("codex: refused beyond loopback (it spends a plan)"):
    val lan = CliRuntime(
      FakeCli.app(Files.createTempDirectory("hm-lan-"), "HISTMERSION_BIND" -> "0.0.0.0"),
      EngineContributions.roster
    )
    assert(Codex.client(CodexCfg, lan, ModelRef(ProviderKind.Codex, ModelRef.Model("m"))).isLeft)

  // ── opencode ───────────────────────────────────────────────────────────────────────────────────

  private val OcOk = List(
    """{"type":"step_start","timestamp":1,"sessionID":"s","part":{"type":"step-start"}}""",
    """{"type":"tool_use","timestamp":2,"sessionID":"s","part":{"type":"tool","tool":"histmersion_get_source","callID":"c1","state":{"status":"completed","input":{"citekey":"x2020"},"output":"# X 2020"}}}""",
    """{"type":"text","timestamp":3,"sessionID":"s","part":{"type":"text","text":"```json\n{\"citekey\":\"x2020\",\"title\":\"X\",\"year\":2020}\n```"}}""",
    """{"type":"step_finish","timestamp":4,"sessionID":"s","part":{"type":"step-finish","tokens":{"input":100,"output":20,"reasoning":5,"cache":{"read":300,"write":0}},"cost":0.01}}"""
  )

  test("opencode: the generated agent and config, isolation env, the schema in the message, the answer parsed"):
    val fake = FakeCli("opencode", OcOk)
    val (rt, dir) = runtime()
    val r =
      OpencodeRunClient(
        ModelRef(ProviderKind.Opencode, ModelRef.Model("openai/gpt-5")),
        OcCfg.modify(_.command).setTo(OpencodeConfig.Command(fake.script.toString)),
        rt
      )
        .run(call(rt, StepAgent.Skeptic))
    assertEquals(r.finalAnswer, Right(ProbeAnswer(Citekey("x2020"), Title("X"), Some(Year(2020)))))
    assertEquals(r.usage, TokenUsage(Tokens(400), Tokens(25), Tokens(300), Tokens.Zero, Tokens.Zero))
    assertEquals(r.toolCalls.map(_.toolName).toList, List("get_source"))
    assertEquals(fake.args.take(5), List("run", "--agent", "article-skeptic", "--format", "json"))
    assertEquals(fake.after("-m"), "openai/gpt-5")
    assertEquals(fake.env("XDG_CONFIG_HOME"), dir.resolve("opencode/xdg").toString)
    assertEquals(fake.env("OPENCODE_CONFIG_DIR"), dir.resolve("opencode/config").toString)
    assertEquals(fake.env("OPENCODE_DISABLE_CLAUDE_CODE"), "1")
    assert(fake.cwdWasEmpty)
    assert(fake.stdin.startsWith("USER <article>data</article>") && fake.stdin.contains("JSON Schema"), fake.stdin)

  test("opencode: an error event that is a usage limit is the plan's limit; a model without provider/ is refused"):
    val fake = FakeCli(
      "opencode",
      List(
        """{"type":"error","error":{"name":"APIError","data":{"message":"Rate limit reached for your ChatGPT plan"}}}"""
      )
    )
    val (rt, _) = runtime()
    intercept[ModelLimitReached](
      OpencodeRunClient(
        ModelRef(ProviderKind.Opencode, ModelRef.Model("openai/gpt-5")),
        OcCfg.modify(_.command).setTo(OpencodeConfig.Command(fake.script.toString)),
        rt
      )
        .run(call(rt, StepAgent.Skeptic))
    )
    assert(
      Opencode
        .client(OcCfg, rt, ModelRef(ProviderKind.Opencode, ModelRef.Model("gpt-5")))
        .left
        .exists(_.sentence.contains("<provider>/<model>"))
    )

  test("the fence around a JSON answer is tolerated; plan limit wording is recognised"):
    assertEquals(CliOutcomes.stripFence("```json\n{\"a\":1}\n```"), "{\"a\":1}")
    assertEquals(CliOutcomes.stripFence("{\"a\":1}"), "{\"a\":1}")
    assert(
      CliOutcomes.isLimit(ClaudeCode.Limit.Sentence("You've hit your usage limit")) &&
        !CliOutcomes.isLimit(ClaudeCode.Limit.Sentence("connection refused"))
    )
    assertEquals(PoolId.ChatgptPlan.wire, "chatgpt-plan")
