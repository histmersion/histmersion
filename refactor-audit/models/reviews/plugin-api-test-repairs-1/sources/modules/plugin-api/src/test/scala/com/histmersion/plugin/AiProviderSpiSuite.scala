package com.histmersion.plugin

/** The AI-provider part of the SPI (docs/research-plugins.md §22). */
class AiProviderSpiSuite extends munit.FunSuite:

  private object Echo extends AiProvider:
    val title = _root_.com.histmersion.plugin.AiProvider.TitleResult("Echo")
    def turn(r: AiTurn.Request, http: AiHttp) =
      r.messages
        .collectFirst { case AiMessage.User(t) => t }
        .toRight(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message("no user message")))
        .map(t => AiTurn(t, usage = Some(AiUsage(10, 2))))

  test("a provider's defaults: tools, no native structured output, no personal plan, nothing to check or list"):
    assertEquals(Echo.capabilities, AiCapabilities(tools = true, structuredOutput = false))
    assert(!(Echo.spendsPersonalPlan).value)
    assertEquals(Echo.check(_root_.com.histmersion.plugin.AiProvider.CheckModel("any")), Right(()))
    assertEquals(Echo.models(AiHttp.Offline), Right(Nil))
    assertEquals(Echo.doctor(online = _root_.com.histmersion.plugin.AiProvider.DoctorOnline(true), AiHttp.Offline), Nil)
    assertEquals(((Echo.description).value: String), "")

  test("one turn: the request in, the answer out; a turn's defaults"):
    val req = AiTurn.Request("m", "sys", List(AiMessage.User(_root_.com.histmersion.plugin.AiMessage.User.Text("hi"))), Nil, None, 16000, 1, 4)
    val t = Echo.turn(req, AiHttp.Offline).toOption.get
    assertEquals((t.text, t.toolCalls, t.stop, t.servedBy), ("hi", Nil, AiStop.EndTurn, None))
    assertEquals(t.usage, Some(AiUsage(10, 2, 0, 0)))
    assertEquals(Echo.turn(req.copy(messages = Nil), AiHttp.Offline), Left(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message("no user message"))))
    assertEquals(AiFailure.LimitReached(_root_.com.histmersion.plugin.AiFailure.LimitReached.Message("limit")), AiFailure.LimitReached(_root_.com.histmersion.plugin.AiFailure.LimitReached.Message("limit"), None))

  test("the offline client sends nothing"):
    assertEquals(AiHttp.Offline.post(_root_.com.histmersion.plugin.AiHttp.PostUrl("https://x.example"), Map.empty, _root_.com.histmersion.plugin.AiHttp.PostBody("{}"), _root_.com.histmersion.plugin.AiHttp.PostTimeoutSeconds(5)), Left("offline"))
    assertEquals(AiHttp.Offline.get(_root_.com.histmersion.plugin.AiHttp.GetUrl("https://x.example"), Map.empty, _root_.com.histmersion.plugin.AiHttp.GetTimeoutSeconds(5)), Left("offline"))

  test("a context written before AI providers existed still compiles and refuses one; the SPI is still 1"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http { def get(url: String, headers: Map[String, String]) = Left("offline") }
      def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = None
      def secret(s: Secret) = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))
    assert(ctx.aiProviders.add(_root_.com.histmersion.plugin.Registry.AddId("echo"), Echo).left.exists(_.contains("no AI providers")))
    assertEquals(Spi.Version, 2)
