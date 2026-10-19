package com.histmersion.server.agents

import com.histmersion.core.{AppConfig, PlanQuota}
import com.histmersion.core.Agents.*
import com.histmersion.plugin.*
import com.histmersion.server.plugins.{Generation, PluginHost, Plugins}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import org.ekrich.config.ConfigFactory
import sttp.ai.core.agent.*

import scala.collection.immutable.ListMap

final case class Ping(word: String) derives KindlingsCodecAsObject
object Ping:
  given sttp.tapir.Schema[Ping] = KindlingsSchema.derived[Ping].schema

/** AI providers as plugins (docs/research-plugins.md §22–§23), no database and no network: the six
  * built-ins registered and switched off like any plugin; a third-party provider registered beside them
  * and refused under a host name; the factory resolving through the registry; and a plugin provider
  * driven through the host's own tool loop — the host's tool runs, the typed answer is decoded, every
  * turn's usage is counted, a plan limit becomes a chain switch. */
class AiProvidersSuite extends munit.FunSuite:

  private val offline = new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") }

  private def build(plugins: List[Plugin], user: String = "", env: String = ""): Generation =
    PluginHost.build(plugins, ConfigFactory.parseString(user), ConfigFactory.parseString(env),
      Plugins.services(() => offline, ConfigFactory.parseString(env)))

  /** What a scripted provider was asked, turn by turn. */
  private final class Scripted(answers: List[AiTurnRequest => Either[AiFailure, AiTurn]],
      override val spendsPersonalPlan: Boolean = false, checked: Either[String, Unit] = Right(())) extends AiProvider:
    val title = "Scripted"
    val asked = scala.collection.mutable.ArrayBuffer.empty[AiTurnRequest]
    override def check(model: String) = checked
    def turn(r: AiTurnRequest, http: AiHttp) =
      asked += r
      answers.lift(asked.size - 1).getOrElse((_: AiTurnRequest) => Left(AiFailure.Error("script exhausted")))(r)

  /** A third-party plugin registering `provider` under `name`. */
  private final class AiPlugin(name: String, provider: AiProvider, id: String = "zq-ai") extends Plugin:
    type Config = com.histmersion.server.acquire.fetchers.BuiltinConfig
    val info = PluginInfo(id, "0.1.0", Spi.Version)
    override def defaultsResource = "zq-none.conf"
    def register(c: Config, ctx: PluginContext) = ctx.aiProviders.add(name, provider)

  private def on(id: String = "zq-ai") = s"histmersion.plugins.$id.enabled = true"

  private val app = AppConfig.load()
  private def rt(host: String = "127.0.0.1") = CliRuntime(app.copy(server = app.server.copy(host = host)))

  // ── the built-ins ──────────────────────────────────────────────────────────────────────────────

  test("the six host providers register as built-in plugins, enabled by default, in their order"):
    val g = Plugins.offline
    assert(g.report.ok, g.report.toString)
    assertEquals(g.registries.aiProviders.keySet, ProviderKind.values.map(_.wire).toSet)
    assertEquals(g.report.plugins.find(_.id == "ai-claude-code").map(_.provides), Some(List("ai-provider:claude-code")))
    assertEquals(BuiltinAi.pluginIds, Set("ai-anthropic", "ai-openai", "ai-local", "ai-claude-code", "ai-codex", "ai-opencode"))
    assert(g.registries.aiProviders.values.forall(_.isInstanceOf[HostAiProvider]))
    assertEquals(BuiltinAi.known(g.registries.aiProviders), Set.empty[String])

  test("a built-in switched off by config or by environment is out of the registry, and the lookup names its switch"):
    val byFile = build(Plugins.providers, "histmersion.plugins.ai-codex.enabled = false")
    assertEquals(byFile.report.plugins.find(_.id == "ai-codex").map(_.state), Some("disabled"))
    assert(!byFile.registries.aiProviders.contains("codex"))
    assertEquals(BuiltinAi.lookup(ProviderKind.Codex, byFile.registries.aiProviders),
      Left("the codex provider is switched off (histmersion.plugins.ai-codex.enabled = false)"))
    val byEnv = build(Plugins.providers, env = "HISTMERSION_PLUGIN_AI_CLAUDE_CODE_ENABLED = false")
    assert(!byEnv.registries.aiProviders.contains("claude-code") && byEnv.registries.aiProviders.contains("codex"))
    assert(BuiltinAi.lookup(ProviderKind.Plugin("gemini"), byEnv.registries.aiProviders).left.exists(_.contains("no AI provider 'gemini'")))

  test("a third-party provider registers beside the built-ins; a host provider's name is refused"):
    val p = Scripted(Nil)
    val g = build(Plugins.providers :+ AiPlugin("gemini", p), on())
    assertEquals(g.report.plugins.find(_.id == "zq-ai").map(_.provides), Some(List("ai-provider:gemini")))
    assertEquals(g.registries.aiProviders.keys.toList.last, "gemini")
    assertEquals(BuiltinAi.known(g.registries.aiProviders), Set("gemini"))
    val taken = build(Plugins.providers :+ AiPlugin("local", p), on())
    assert(taken.report.plugins.find(_.id == "zq-ai").flatMap(_.error).exists(_.contains("'local' is already registered")),
      taken.report.toString)
    assertEquals(taken.registries.aiProviders("local").getClass, classOf[HostAiProvider])
    // nor once the host's own is switched off: the name stays the host's
    val free = build(Plugins.providers :+ AiPlugin("local", p), on() + "\nhistmersion.plugins.ai-local.enabled = false")
    assert(free.report.plugins.find(_.id == "zq-ai").flatMap(_.error).exists(_.contains("one of Histmersion's own AI providers")),
      free.report.toString)
    assert(!free.registries.aiProviders.contains("local"))
    val bad = build(List(AiPlugin("Not_Kebab", p)), on())
    assert(bad.report.plugins.head.error.exists(_.contains("an AI provider name is")), bad.report.toString)

  test("reload: a host key rebuilds no AI built-in; its own block rebuilds it alone"):
    val g1 = build(Plugins.providers :+ AiPlugin("gemini", Scripted(Nil)), on())
    assert(BuiltinAi.pluginIds.subsetOf(Plugins.carried(g1, List("histmersion.acquire.chain")).keySet))
    assert(!Plugins.carried(g1, List("histmersion.acquire.chain")).contains("zq-ai"))
    val own = Plugins.carried(g1, List("histmersion.plugins.ai-codex.enabled")).keySet
    assert(!own("ai-codex") && own("ai-local") && own("zq-ai"))
    // an agents.* setting is no plugin key: nothing is rebuilt (the clients read it at each call)
    assert(Plugins.carried(g1, List("histmersion.agents.providers.codex.command")).keySet("zq-ai"))

  // ── the factory ────────────────────────────────────────────────────────────────────────────────

  test("the factory: a host provider runs today's client; a switched-off one or an unknown one is refused"):
    val reg = Plugins.offline.registries.aiProviders
    val f = LlmClient.factory(rt(), () => reg, AiHttp.Offline)
    assert(f(app.agents, ModelRef(ProviderKind.Local, "qwen")).isRight)
    // exactly what the host's own factory says for every host provider (no key here: the same refusal)
    val noKeys = app.agents.copy(providers = app.agents.providers.copy(
      anthropic = app.agents.providers.anthropic.copy(apiKey = ""), openai = app.agents.providers.openai.copy(apiKey = "")))
    ProviderKind.values.foreach { k =>
      val r = ModelRef(k, "m/x")
      assertEquals(f(noKeys, r).left.toOption, LlmClient.host(rt())(noKeys, r).left.toOption, k.wire)
    }
    val off = LlmClient.factory(rt(), () => reg - "local", AiHttp.Offline)
    assertEquals(off(app.agents, ModelRef(ProviderKind.Local, "qwen")).left.toOption,
      Some("local:qwen: the local provider is switched off (histmersion.plugins.ai-local.enabled = false)"))

  test("the factory: a plugin provider becomes its client; its own check and the loopback rule apply"):
    val ref = ModelRef(ProviderKind.Plugin("gemini"), "pro")
    def f(p: AiProvider, host: String = "127.0.0.1") =
      LlmClient.factory(rt(host), () => Plugins.offline.registries.aiProviders + ("gemini" -> p), AiHttp.Offline)(app.agents, ref)
    assert(f(Scripted(Nil)).exists(_.isInstanceOf[PluginAiClient]))
    assertEquals(f(Scripted(Nil, checked = Left("no GEMINI key"))).left.toOption, Some("gemini:pro: no GEMINI key"))
    assert(f(Scripted(Nil, spendsPersonalPlan = true), "0.0.0.0").left.exists(_.contains("beyond loopback")))
    assert(f(Scripted(Nil, spendsPersonalPlan = true)).isRight)
    // not registered (its plugin switched off since): refused, naming it
    assert(LlmClient.factory(rt(), () => Plugins.offline.registries.aiProviders, AiHttp.Offline)(app.agents, ref)
      .left.exists(_.contains("no AI provider 'gemini' is registered")))

  // ── the host's loop over a plugin's turns ──────────────────────────────────────────────────────

  private val ping = AgentTool.fromFunction[Ping]("ping", "answers pong")(p => s"pong ${p.word}")
  private val ref = ModelRef(ProviderKind.Plugin("gemini"), "pro")
  private def call(tools: List[AgentTool[sttp.shared.Identity, ?]] = List(ping), iterations: Int = 4) =
    LlmCall[ProbeAnswer]("SYSTEM", "USER <article>data</article>", tools, AnswerSchemas.probe,
      maxIterations = iterations, maxTokens = 16000)
  private val u = Some(AiUsage(input = 1000, output = 50, cacheRead = 400))

  test("a plugin's turns run in the host's loop: the host's tool runs, the answer is decoded, each turn counted"):
    val p = Scripted(List(
      _ => Right(AiTurn("", List(AiToolCall("c1", "ping", """{"word":"hi"}""")), AiStop.ToolUse, u)),
      _ => Right(AiTurn("""{"citekey":"x2020","title":"X","year":2020}""", usage = u, servedBy = Some("pro-002")))))
    val r = PluginAiClient(ref, p, AiHttp.Offline).run(call())
    assertEquals(r.finalAnswer, Right(ProbeAnswer("x2020", "X", Some(2020))))
    assertEquals(r.toolCalls.map(c => (c.toolName, c.output)).toList, List(("ping", "pong hi")))
    assertEquals(r.usage, TokenUsage(Tokens(2000), Tokens(100), Tokens(800), Tokens.Zero, Tokens.Zero))
    assertEquals(LlmClient.servedBy(r.llmCalls), Some("pro + pro-002")) // each turn names who served it
    // what the provider was told: the model id, the host's prompt, the user's message, the tool, the schema
    val first = p.asked.head
    assertEquals((first.model, first.system, first.maxTokens, first.iteration), ("pro", "SYSTEM", 16000, 1))
    assertEquals(first.messages, List(AiMessage.User("USER <article>data</article>")))
    assertEquals(first.tools.map(_.name), List("ping"))
    assert(first.tools.head.inputSchema.contains("\"word\""), first.tools.head.inputSchema)
    assert(first.answerSchema.exists(s => s.contains("\"citekey\"") && !s.contains("$schema")), first.answerSchema)
    val second = p.asked(1)
    assertEquals(second.messages.collect { case m: AiMessage.ToolResult => m }, List(AiMessage.ToolResult("c1", "ping", "pong hi")))
    assertEquals(second.messages.collect { case AiMessage.Assistant(_, cs) => cs }.flatten,
      List(AiToolCall("c1", "ping", """{"word":"hi"}""")))

  test("a tool the step does not allow is never run for a plugin either; the last iteration offers no tools"):
    val p = Scripted(List(
      _ => Right(AiTurn("", List(AiToolCall("c1", "query", """{"sql":"DELETE FROM sources"}""")), AiStop.ToolUse, u)),
      _ => Right(AiTurn("""{"citekey":"x","title":"X","year":null}""", usage = u))))
    val r = PluginAiClient(ref, p, AiHttp.Offline).run(call(iterations = 2))
    assertEquals(r.finalAnswer, Right(ProbeAnswer("x", "X", None)))
    assert(r.toolCalls.forall(_.output.toLowerCase.contains("not found")), r.toolCalls.toString)
    assertEquals(p.asked(1).tools, Nil, "the last allowed iteration must answer")

  test("an interceptor sees every turn of a plugin provider, as it does for the HTTP providers"):
    val seen = java.util.concurrent.atomic.AtomicInteger(0)
    val count = new AgentInterceptor[sttp.shared.Identity]:
      override def aroundLlmCall(ctx: LlmCallContext)(next: => AgentResponse): AgentResponse =
        seen.incrementAndGet(); next
    val p = Scripted(List(
      _ => Right(AiTurn("", List(AiToolCall("c1", "ping", """{"word":"a"}""")), AiStop.ToolUse, u)),
      _ => Right(AiTurn("""{"citekey":"x","title":"X","year":1}""", usage = u))))
    val _ = PluginAiClient(ref, p, AiHttp.Offline).run(call().copy(interceptors = List(count)))
    assertEquals(seen.get, 2)

  test("failures: the plan's limit is ModelLimitReached (the chain moves on); an error, a throw, a null are provider errors"):
    val limited = Scripted(List(_ => Left(AiFailure.LimitReached("usage limit reached", Some(4102444800000L)))))
    val e = intercept[ModelLimitReached](PluginAiClient(ref, limited, AiHttp.Offline).run(call()))
    assertEquals((e.model, e.until), (ref, Some(java.time.Instant.ofEpochMilli(4102444800000L))))
    val err = intercept[java.io.IOException](PluginAiClient(ref, Scripted(List(_ => Left(AiFailure.Error("HTTP 500")))),
      AiHttp.Offline).run(call()))
    assertEquals(err.getMessage, "gemini:pro: HTTP 500")
    val thrown = intercept[java.io.IOException](PluginAiClient(ref, Scripted(List(_ => throw IllegalStateException("boom"))),
      AiHttp.Offline).run(call()))
    assert(thrown.getMessage.contains("IllegalStateException: boom"), thrown.getMessage)
    val nul = intercept[java.io.IOException](PluginAiClient(ref, Scripted(List(_ => null)), AiHttp.Offline).run(call()))
    assert(nul.getMessage.contains("null"), nul.getMessage)

  test("pure: an answer becomes sttp-ai's — stop reasons, usage never negative, the configured model when none is named"):
    val t = PluginAiClient.response(AiTurn("t", List(AiToolCall("1", "x", "{}")), AiStop.EndTurn, Some(AiUsage(-5, 3))), ref)
    assertEquals((t.stopReason, t.model, t.usage.map(_.inputTokens.value)), (StopReason.ToolUse, Some("pro"), Some(0L)))
    assertEquals(PluginAiClient.response(AiTurn("t", stop = AiStop.MaxTokens), ref).stopReason, StopReason.MaxTokens)
    assertEquals(PluginAiClient.messages(ConversationHistory.withInitialPrompt("u").addIterationMarker(1, 2)
      .addAssistantResponse("a", Nil)), List(AiMessage.User("u"), AiMessage.Assistant("a", Nil)))

  test("billing and quota: a plugin provider's model is metered, on no plan pool"):
    val m = ModelRef(ProviderKind.Plugin("gemini"), "pro")
    assertEquals(PlanQuota.poolOf(m, app.agents.logins), None)
    assert(Prices.billing(Map("pro" -> Price(BigDecimal(0), BigDecimal(0))), m, None).isRight)
