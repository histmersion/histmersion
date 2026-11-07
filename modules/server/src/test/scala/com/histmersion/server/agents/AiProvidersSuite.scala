package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.Plugins

import com.histmersion.pluginhost.{FirstPartySettings, Generation, PluginHost}

import com.histmersion.config.AppConfig
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.Agents.*
import com.histmersion.plugin.*
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import org.ekrich.config.ConfigFactory
import sttp.ai.core.agent.*

import scala.collection.immutable.ListMap

final case class Ping(word: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


/** AI providers as plugins (docs/research-plugins.md §22–§23), no database and no network: the six built-ins registered
  * and switched off like any plugin; a third-party provider registered beside them and refused under a host name; the
  * factory resolving through the registry; and a plugin provider driven through the host's own tool loop — the host's
  * tool runs, the typed answer is decoded, every turn's usage is counted, a plan limit becomes a chain switch.
  */
class AiProvidersSuite extends munit.FunSuite:

  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val offline = new Plugin.Http:
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
      Left(Plugin.Http.GetResultPart1("offline"))

  /** The registry's providers as the engine reads them: keyed by plugin id. */
  private def providersOf(
      registered: ListMap[com.histmersion.pluginhost.Registries.AiProvidersKey, AiProvider]
  ): ListMap[com.histmersion.util.plugins.Plugin.Id, AiProvider] =
    registered.map((k, p) => com.histmersion.util.plugins.Plugin.Id.assume(k.value) -> p)

  /** A generation of `plugins` as the server builds one: the first-party plugins supplied the host's settings
    * (`reference.conf` under `env`) and allowed their reserved names.
    */
  private def build(plugins: List[Plugin], user: String = "", env: String = ""): Generation =
    val (u, e) = (ConfigFactory.parseString(user), ConfigFactory.parseString(env))
    val reference = AppConfig.loadWithUserFile(e, None).fold(m => fail(m.value), _.merged)
    val host = FirstPartySettings.hostView(reference, u, e, plugins)
    PluginHost.build(
      plugins,
      u,
      e,
      Plugins.services(() => offline, e),
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = Plugins.firstParty(None)
    )

  /** The six built-in providers' plugins: the HTTP APIs' found on the class path, the command-line runtimes the host's.
    */
  private val builtins: List[Plugin] =
    PluginHost
      .discover(getClass.getClassLoader)
      ._1
      .filter(p => com.histmersion.util.plugins.Plugin.Id.option(p.info.id.value).exists(BuiltinAi.pluginIds)) ++ ServerPlugins.providers

  /** What a scripted provider was asked, turn by turn. */
  final private class Scripted(
      answers: List[AiTurn.Request => Either[AiFailure, AiTurn]],
      override val spendsPersonalPlan: AiProvider.SpendsPersonalPlanResult = AiProvider.SpendsPersonalPlanResult(false),
      checked: Either[AiProvider.CheckResultPart1, Unit] = Right(())
  ) extends AiProvider:
    val title = com.histmersion.plugin.AiProvider.TitleResult("Scripted")
    val asked = scala.collection.mutable.ArrayBuffer.empty[AiTurn.Request]
    override def check(model: AiProvider.CheckModel) = checked
    def turn(r: AiTurn.Request, http: AiHttp) =
      asked += r
      answers.lift(asked.size - 1).getOrElse((_: AiTurn.Request) => Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("script exhausted"))))(r)

  /** A third-party plugin registering `provider` under `name`. */
  final private class AiPlugin(name: Registry.AddId, provider: AiProvider, id: Plugin.Info.Id = Plugin.Info.Id("zq-ai")) extends Plugin:
    type Config = com.histmersion.util.plugins.HostProvided.NoSettings
    val info = Plugin.Info(id, com.histmersion.plugin.Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = com.histmersion.plugin.Plugin.DefaultsResourceResult("zq-none.conf")
    def register(c: Config, ctx: Plugin.Context) =
      ctx.aiProviders.add(name, provider).left.map(m => Plugin.RegisterResultPart1(m.value))

  private def on(id: String = "zq-ai") = s"histmersion.plugins.$id.enabled = true"

  private val app = AppConfig.load()
  private def rt(host: String = "127.0.0.1") =
    CliRuntime(app.modify(_.server).setTo(app.server.modify(_.host).setTo(com.histmersion.config.ServerConfig.Host(host))), EngineContributions.roster)

  // ── the built-ins ──────────────────────────────────────────────────────────────────────────────

  test("the six host providers register as built-in plugins, enabled by default, in their order"):
    val g = ServerPlugins.offline
    assert(g.report.ok, g.report.toString)
    assertEquals(g.registries.aiProviders.keySet, ProviderKind.values.map(_.wire).toSet)
    assertEquals(g.report.plugins.find(_.id.value == "ai-claude-code").map(_.provides), Some(List("ai-provider:claude-code")))
    assertEquals(
      BuiltinAi.pluginIds,
      Set("ai-anthropic", "ai-openai", "ai-local", "ai-claude-code", "ai-codex", "ai-opencode")
    )
    // the command-line runtimes are the host's own; the HTTP APIs are the first-party plugins' providers
    val hostRun = g.registries.aiProviders.collect { case (n, _: HostAiProvider) => n }.toSet
    assertEquals(hostRun, Set("claude-code", "codex", "opencode"))
    // in the generation's order (by plugin id), as before
    assertEquals(
      g.registries.aiProviders.keys.toList,
      List("anthropic", "claude-code", "codex", "local", "openai", "opencode")
    )
    assertEquals(
      g.report.plugins
        .filter(p => com.histmersion.util.plugins.Plugin.Id.option(p.id.value).exists(BuiltinAi.pluginIds))
        .map(p => (p.id, p.version, p.provides)),
      List(
        ("ai-anthropic", "0.1.0", List("ai-provider:anthropic")),
        ("ai-claude-code", "0.1.0", List("ai-provider:claude-code")),
        ("ai-codex", "0.1.0", List("ai-provider:codex")),
        ("ai-local", "0.1.0", List("ai-provider:local")),
        ("ai-openai", "0.1.0", List("ai-provider:openai")),
        ("ai-opencode", "0.1.0", List("ai-provider:opencode"))
      )
    )
    assertEquals(BuiltinAi.known(providersOf(g.registries.aiProviders)), Set.empty[String])

  test("a built-in switched off by config or by environment is out of the registry, and the lookup names its switch"):
    val byFile = build(builtins, "histmersion.plugins.ai-codex.enabled = false")
    assertEquals(byFile.report.plugins.find(_.id.value == "ai-codex").map(_.state), Some("disabled"))
    assert(!byFile.registries.aiProviders.contains(com.histmersion.pluginhost.Registries.AiProvidersKey("codex")))
    assertEquals(
      BuiltinAi.lookup(ProviderKind.Codex, providersOf(byFile.registries.aiProviders)),
      Left("the codex provider is switched off (histmersion.plugins.ai-codex.enabled = false)")
    )
    val byEnv = build(builtins, env = "HISTMERSION_PLUGIN_AI_CLAUDE_CODE_ENABLED = false")
    assert(!byEnv.registries.aiProviders.contains(com.histmersion.pluginhost.Registries.AiProvidersKey("claude-code")) && byEnv.registries.aiProviders.contains(com.histmersion.pluginhost.Registries.AiProvidersKey("codex")))
    assert(
      BuiltinAi
        .lookup(ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")), providersOf(byEnv.registries.aiProviders))
        .left
        .exists(_.value.contains("no AI provider 'gemini'"))
    )

  test("a third-party provider registers beside the built-ins; a host provider's name is refused"):
    val p = Scripted(Nil)
    val g = build(builtins :+ AiPlugin(Registry.AddId("gemini"), p), on())
    assertEquals(g.report.plugins.find(_.id.value == "zq-ai").map(_.provides), Some(List("ai-provider:gemini")))
    assertEquals(g.registries.aiProviders.keys.toList.last, "gemini")
    assertEquals(BuiltinAi.known(providersOf(g.registries.aiProviders)), Set("gemini"))
    val taken = build(builtins :+ AiPlugin(Registry.AddId("local"), p), on())
    assert(
      taken.report.plugins.find(_.id.value == "zq-ai").flatMap(_.error).exists(_.value.contains("'local' is already registered")),
      taken.report.toString
    )
    assert(taken.registries.aiProviders(com.histmersion.pluginhost.Registries.AiProvidersKey("local")) ne p, "the name stays the built-in plugin's")
    // nor once the host's own is switched off: the name stays the host's
    val free = build(builtins :+ AiPlugin(Registry.AddId("local"), p), on() + "\nhistmersion.plugins.ai-local.enabled = false")
    assert(
      free.report.plugins
        .find(_.id.value == "zq-ai")
        .flatMap(_.error)
        .exists(_.value.contains("one of Histmersion's own AI providers")),
      free.report.toString
    )
    assert(!free.registries.aiProviders.contains(com.histmersion.pluginhost.Registries.AiProvidersKey("local")))
    val bad = build(List(AiPlugin(Registry.AddId("Not_Kebab"), p)), on())
    assert(bad.report.plugins.head.error.exists(_.value.contains("an AI provider name is")), bad.report.toString)

  test("reload: only an HTTP provider's own agents.providers.<name> rebuilds an AI built-in; so does its own block"):
    val g1 = build(builtins :+ AiPlugin(Registry.AddId("gemini"), Scripted(Nil)), on())
    assert(BuiltinAi.pluginIds.map(i => com.histmersion.plugin.Plugin.Info.Id(i.value)).subsetOf(Plugins.carried(g1, List(com.histmersion.config.ConfigReload.ChangedKey("histmersion.acquire.chain"))).keySet))
    assert(!Plugins.carried(g1, List(com.histmersion.config.ConfigReload.ChangedKey("histmersion.acquire.chain"))).contains(com.histmersion.plugin.Plugin.Info.Id("zq-ai")))
    val own = Plugins.carried(g1, List(com.histmersion.config.ConfigReload.ChangedKey("histmersion.plugins.ai-codex.enabled"))).keySet
    assert(!own(com.histmersion.plugin.Plugin.Info.Id("ai-codex")) && own(com.histmersion.plugin.Plugin.Info.Id("ai-local")) && own(com.histmersion.plugin.Plugin.Info.Id("zq-ai")))
    // a command-line runtime's agents.* setting is no plugin key: nothing is rebuilt (its client reads it at each call)
    assertEquals(
      Plugins.carried(g1, List(com.histmersion.config.ConfigReload.ChangedKey("histmersion.agents.providers.codex.command"))).keySet,
      Plugins.carried(g1, Nil).keySet
    )
    // an HTTP provider's own agents.providers.<name> rebuilds its plugin alone
    val keyed = Plugins.carried(g1, List(com.histmersion.config.ConfigReload.ChangedKey("histmersion.agents.providers.openai.api-key"))).keySet
    assert(!keyed(com.histmersion.plugin.Plugin.Info.Id("ai-openai")) && keyed(com.histmersion.plugin.Plugin.Info.Id("ai-anthropic")) && keyed(com.histmersion.plugin.Plugin.Info.Id("ai-local")) && keyed(com.histmersion.plugin.Plugin.Info.Id("zq-ai")), keyed.toString)

  // ── the factory ────────────────────────────────────────────────────────────────────────────────

  test("the factory: a built-in provider runs its client; a switched-off one or an unknown one is refused"):
    val reg = providersOf(ServerPlugins.offline.registries.aiProviders)
    val f = LlmClient.factory(rt(), () => reg, AiHttp.Offline)
    assert(f(app.agents, ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("qwen"))).exists(_.isInstanceOf[PluginAiClient]))
    // the HTTP providers' own checks over the settings the host supplied them (no key in reference.conf), as they read
    assertEquals(
      f(app.agents, ModelRef(ProviderKind.Anthropic, com.histmersion.api.automation.Agents.ModelRef.Model("m/x"))).left.toOption,
      Some("no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment")
    )
    assertEquals(
      f(app.agents, ModelRef(ProviderKind.OpenAI, com.histmersion.api.automation.Agents.ModelRef.Model("m/x"))).left.toOption,
      Some("no OpenAI API key — set OPENAI_API_KEY in the server's environment")
    )
    // the command-line runtimes: exactly what the host's own factory says
    BuiltinAi.runtimes.foreach { k =>
      val r = ModelRef(k, com.histmersion.api.automation.Agents.ModelRef.Model("m/x"))
      assertEquals(f(app.agents, r).left.toOption, LlmClient.host(rt())(app.agents, r).left.toOption, k.wire)
    }
    val off = LlmClient.factory(rt(), () => reg - com.histmersion.util.plugins.Plugin.Id("local"), AiHttp.Offline)
    assertEquals(
      off(app.agents, ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("qwen"))).left.toOption,
      Some("local:qwen: the local provider is switched off (histmersion.plugins.ai-local.enabled = false)")
    )

  test("the factory: a plugin provider becomes its client; its own check and the loopback rule apply"):
    val ref = ModelRef(ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")), com.histmersion.api.automation.Agents.ModelRef.Model("pro"))
    def f(p: AiProvider, host: String = "127.0.0.1") =
      LlmClient.factory(
        rt(host),
        () => providersOf(ServerPlugins.offline.registries.aiProviders + (com.histmersion.pluginhost.Registries.AiProvidersKey("gemini") -> p)),
        AiHttp.Offline
      )(
        app.agents,
        ref
      )
    assert(f(Scripted(Nil)).exists(_.isInstanceOf[PluginAiClient]))
    assertEquals(f(Scripted(Nil, checked = Left(AiProvider.CheckResultPart1("no GEMINI key")))).left.toOption, Some("gemini:pro: no GEMINI key"))
    assert(f(Scripted(Nil, spendsPersonalPlan = AiProvider.SpendsPersonalPlanResult(true)), "0.0.0.0").left.exists(_.value.contains("beyond loopback")))
    assert(f(Scripted(Nil, spendsPersonalPlan = AiProvider.SpendsPersonalPlanResult(true))).isRight)
    // not registered (its plugin switched off since): refused, naming it
    assert(
      LlmClient
        .factory(rt(), () => providersOf(ServerPlugins.offline.registries.aiProviders), AiHttp.Offline)(app.agents, ref)
        .left
        .exists(_.value.contains("no AI provider 'gemini' is registered"))
    )

  // ── the host's loop over a plugin's turns ──────────────────────────────────────────────────────

  private val ping = AgentTool.fromFunction[Ping]("ping", "answers pong")(p => s"pong ${p.word}")
  private val ref = ModelRef(ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")), com.histmersion.api.automation.Agents.ModelRef.Model("pro"))
  private def call(
      tools: List[AgentTool[sttp.shared.Identity, ?]] = List(ping),
      iterations: com.histmersion.domain.automation.agents.LlmCall.MaxIterations = com.histmersion.domain.automation.agents.LlmCall.MaxIterations(4)
  ) =
    LlmCall[ProbeAnswer](
      com.histmersion.domain.automation.agents.LlmCall.System("SYSTEM"),
      com.histmersion.domain.automation.agents.LlmCall.User("USER <article>data</article>"),
      tools,
      AnswerSchemas.probe,
      maxIterations = iterations,
      maxTokens = com.histmersion.domain.automation.agents.LlmCall.MaxTokens(16000)
    )
  private val u = Some(AiUsage(input = com.histmersion.plugin.AiUsage.Input(1000), output = com.histmersion.plugin.AiUsage.Output(50), cacheRead = com.histmersion.plugin.AiUsage.CacheRead(400)))

  test("a plugin's turns run in the host's loop: the host's tool runs, the answer is decoded, each turn counted"):
    val p = Scripted(
      List(
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text(""), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("ping"), com.histmersion.plugin.AiTool.Call.Input("""{"word":"hi"}"""))), AiStop.ToolUse, u)),
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text("""{"citekey":"x2020","title":"X","year":2020}"""), usage = u, servedBy = Some(com.histmersion.plugin.AiTurn.ServedBy("pro-002"))))
      )
    )
    val r = PluginAiClient(ref, p, AiHttp.Offline).run(call())
    assertEquals(r.finalAnswer, Right(ProbeAnswer(com.histmersion.api.library.Citekey("x2020"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020)))))
    assertEquals(r.toolCalls.map(c => (c.toolName, c.output)).toList, List(("ping", "pong hi")))
    assertEquals(r.usage, TokenUsage(Tokens(2000), Tokens(100), Tokens(800), Tokens.Zero, Tokens.Zero))
    assertEquals(LlmClient.servedBy(r.llmCalls), Some("pro + pro-002")) // each turn names who served it
    // what the provider was told: the model id, the host's prompt, the user's message, the tool, the schema
    val first = p.asked.head
    assertEquals((first.model, first.system, first.maxTokens, first.iteration), ("pro", "SYSTEM", 16000, 1))
    assertEquals(first.messages, List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("USER <article>data</article>"))))
    assertEquals(first.tools.map(_.name), List("ping"))
    assert(first.tools.head.inputSchema.value.contains("\"word\""), first.tools.head.inputSchema)
    assert(first.answerSchema.exists(s => s.value.contains("\"citekey\"") && !s.value.contains("$schema")), first.answerSchema)
    val second = p.asked(1)
    assertEquals(
      second.messages.collect { case m: AiMessage.ToolResult => m },
      List(AiMessage.ToolResult(com.histmersion.plugin.AiMessage.ToolResult.CallId("c1"), com.histmersion.plugin.AiMessage.ToolResult.Tool("ping"), com.histmersion.plugin.AiMessage.ToolResult.Output("pong hi")))
    )
    assertEquals(
      second.messages.collect { case AiMessage.Assistant(_, cs) => cs }.flatten,
      List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("ping"), com.histmersion.plugin.AiTool.Call.Input("""{"word":"hi"}""")))
    )

  test("a tool the step does not allow is never run for a plugin either; the last iteration offers no tools"):
    val p = Scripted(
      List(
        _ =>
          Right(AiTurn(com.histmersion.plugin.AiTurn.Text(""), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("query"), com.histmersion.plugin.AiTool.Call.Input("""{"sql":"DELETE FROM sources"}"""))), AiStop.ToolUse, u)),
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text("""{"citekey":"x","title":"X","year":null}"""), usage = u))
      )
    )
    val r = PluginAiClient(ref, p, AiHttp.Offline).run(call(iterations = com.histmersion.domain.automation.agents.LlmCall.MaxIterations(2)))
    assertEquals(r.finalAnswer, Right(ProbeAnswer(com.histmersion.api.library.Citekey("x"), com.histmersion.api.library.Title("X"), None)))
    assert(r.toolCalls.forall(_.output.toLowerCase.contains("not found")), r.toolCalls.toString)
    assertEquals(p.asked(1).tools, Nil, "the last allowed iteration must answer")

  test("an interceptor sees every turn of a plugin provider, as it does for the HTTP providers"):
    val seen = java.util.concurrent.atomic.AtomicInteger(0)
    val count = new AgentInterceptor[sttp.shared.Identity]:
      override def aroundLlmCall(ctx: LlmCallContext)(next: => AgentResponse): AgentResponse =
        seen.incrementAndGet(); next
    val p = Scripted(
      List(
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text(""), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("ping"), com.histmersion.plugin.AiTool.Call.Input("""{"word":"a"}"""))), AiStop.ToolUse, u)),
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text("""{"citekey":"x","title":"X","year":1}"""), usage = u))
      )
    )
    val _ = PluginAiClient(ref, p, AiHttp.Offline).run(call().copy(interceptors = List(count)))
    assertEquals(seen.get, 2)

  test(
    "failures: the plan's limit is ModelLimitReached (the chain moves on); an error, a throw, a null are provider errors"
  ):
    val limited = Scripted(List(_ => Left(AiFailure.LimitReached(com.histmersion.plugin.AiFailure.LimitReached.Message("usage limit reached"), Some(com.histmersion.plugin.AiFailure.LimitReached.UntilEpochMs(4102444800000L))))))
    val e = intercept[ModelLimitReached](PluginAiClient(ref, limited, AiHttp.Offline).run(call()))
    assertEquals((e.model, e.until), (ref, Some(java.time.Instant.ofEpochMilli(4102444800000L))))
    val err = intercept[java.io.IOException](
      PluginAiClient(ref, Scripted(List(_ => Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("HTTP 500"))))), AiHttp.Offline).run(call())
    )
    assertEquals(err.getMessage, "gemini:pro: HTTP 500")
    val thrown = intercept[java.io.IOException](
      PluginAiClient(ref, Scripted(List(_ => throw IllegalStateException("boom"))), AiHttp.Offline).run(call())
    )
    assert(thrown.getMessage.contains("IllegalStateException: boom"), thrown.getMessage)
    val nul = intercept[java.io.IOException](PluginAiClient(ref, Scripted(List(_ => null)), AiHttp.Offline).run(call()))
    assert(nul.getMessage.contains("null"), nul.getMessage)

  test(
    "pure: an answer becomes sttp-ai's — stop reasons, usage never negative, the configured model when none is named"
  ):
    val t =
      PluginAiClient.response(AiTurn(com.histmersion.plugin.AiTurn.Text("t"), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("1"), com.histmersion.plugin.AiTool.Call.Tool("x"), com.histmersion.plugin.AiTool.Call.Input("{}"))), AiStop.EndTurn, Some(AiUsage(com.histmersion.plugin.AiUsage.Input(-5), com.histmersion.plugin.AiUsage.Output(3)))), ref)
    assertEquals((t.stopReason, t.model, t.usage.map(_.inputTokens.value)), (StopReason.ToolUse, Some("pro"), Some(0L)))
    assertEquals(PluginAiClient.response(AiTurn(com.histmersion.plugin.AiTurn.Text("t"), stop = AiStop.MaxTokens), ref).stopReason, StopReason.MaxTokens)
    assertEquals(
      PluginAiClient.messages(
        ConversationHistory
          .withInitialPrompt("u")
          .addIterationMarker(1, 2)
          .addAssistantResponse("a", Nil)
      ),
      List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("u")), AiMessage.Assistant(com.histmersion.plugin.AiMessage.Assistant.Text("a"), Nil))
    )
    assertEquals(
      PluginAiClient.messages(ConversationHistory.withInitialPrompt("u").addIterationMarker(2, 3), markers = com.histmersion.plugin.AiCapabilities.IterationMarkers(true)),
      List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("u")), AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("[Iteration 2 of 3]")))
    )

  /** A provider that asks for the loop's notes and verbatim failures, as the HTTP built-ins do. */
  final private class Wired(answers: List[AiTurn.Request => Either[AiFailure, AiTurn]], checked: Either[AiProvider.CheckResultPart1, Unit])
      extends AiProvider:
    val title = com.histmersion.plugin.AiProvider.TitleResult("Wired")
    val asked = scala.collection.mutable.ArrayBuffer.empty[AiTurn.Request]
    override def capabilities =
      AiCapabilities(iterationMarkers = AiCapabilities.IterationMarkers(true), verbatimFailures = AiCapabilities.VerbatimFailures(true))
    override def check(model: AiProvider.CheckModel) = checked
    def turn(r: AiTurn.Request, http: AiHttp) =
      asked += r
      answers.lift(asked.size - 1).getOrElse((_: AiTurn.Request) => Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("script exhausted"))))(r)

  test("the request options a provider asks for: the loop's notes, the rendered schema, reasoning, verbatim failures"):
    val p = Wired(
      List(
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text(""), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("ping"), com.histmersion.plugin.AiTool.Call.Input("""{"word":"hi"}"""))), AiStop.ToolUse, u)),
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text("""{"word":"pong"}"""), usage = Some(AiUsage(com.histmersion.plugin.AiUsage.Input(10), com.histmersion.plugin.AiUsage.Output(5), reasoning = com.histmersion.plugin.AiUsage.Reasoning(3)))))
      ),
      Right(())
    )
    val described = ResponseSchema.derived[Ping](Some("one word back"))
    val r = PluginAiClient(ref, p, AiHttp.Offline).run(call().copy(schema = described))
    assertEquals(r.finalAnswer, Right(Ping("pong")))
    assertEquals(r.usage.reasoningTokens, Tokens(3))
    // the second turn ends with the loop's note, as sttp-ai's own providers send it
    assertEquals(p.asked(1).messages.last, AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("[Iteration 2 of 4]")))
    assertEquals(p.asked.head.messages, List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("USER <article>data</article>"))))
    assertEquals(p.asked.head.answerDescription, Some("one word back"))
    assert(p.asked.head.answerSchemaRendered.exists(_.value.startsWith("{\"$schema\":")), p.asked.head.answerSchemaRendered)
    assert(!p.asked.head.answerSchema.exists(_.value.contains("$schema")))
    // a third-party provider that asks for nothing sees neither the notes nor anything else new
    val plain = Scripted(
      List(
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text(""), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("ping"), com.histmersion.plugin.AiTool.Call.Input("""{"word":"hi"}"""))), AiStop.ToolUse, u)),
        _ => Right(AiTurn(com.histmersion.plugin.AiTurn.Text("""{"citekey":"x","title":"X","year":1}"""), usage = u))
      )
    )
    val _ = PluginAiClient(ref, plain, AiHttp.Offline).run(call())
    assert(!plain.asked(1).messages.exists { case AiMessage.User(t) => t.value.startsWith("[Iteration"); case _ => false })
    // a verbatim failure is the run's whole error text; its check's refusal is not prefixed with the model
    val failing = Wired(List(_ => Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("RuntimeException: Claude API error: Overloaded")))), Right(()))
    assertEquals(
      AgentsControllers.attempt(ref)(PluginAiClient(ref, failing, AiHttp.Offline).run(call())).left.map(_.message),
      Left("the call to gemini:pro failed: RuntimeException: Claude API error: Overloaded")
    )
    val refused = PluginAiClient.client(ref, Wired(Nil, Left(AiProvider.CheckResultPart1("no Gemini key — set GEMINI_KEY"))), AiHttp.Offline, com.histmersion.domain.automation.agents.PluginAiClient.Loopback(true))
    assertEquals(refused.left.toOption, Some("no Gemini key — set GEMINI_KEY"))
    // any other provider's error names the model and the exception, as before
    val other = Scripted(List(_ => Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("HTTP 500")))))
    assertEquals(
      AgentsControllers.attempt(ref)(PluginAiClient(ref, other, AiHttp.Offline).run(call())).left.map(_.message),
      Left("the call to gemini:pro failed: IOException: gemini:pro: HTTP 500")
    )

  test("billing and quota: a plugin provider's model is metered, on no plan pool"):
    val m = ModelRef(ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")), com.histmersion.api.automation.Agents.ModelRef.Model("pro"))
    assertEquals(PlanQuota.poolOf(m, app.agents.logins), None)
    assert(Prices.billing(Map(com.histmersion.api.automation.Agents.ModelRef.Model("pro") -> Price(BigDecimal(0), BigDecimal(0))), m, None).isRight)
