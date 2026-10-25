package com.histmersion.plugin

/** The AI-provider part of the SPI (docs/research-plugins.md §22). */
class AiProviderSpiSuite extends munit.FunSuite:

  private object Echo extends AiProvider:
    val title = "Echo"
    def turn(r: AiTurnRequest, http: AiHttp) =
      r.messages.collectFirst { case AiMessage.User(t) => t }.toRight(AiFailure.Error("no user message"))
        .map(t => AiTurn(t, usage = Some(AiUsage(10, 2))))

  test("a provider's defaults: tools, no native structured output, no personal plan, nothing to check or list"):
    assertEquals(Echo.capabilities, AiCapabilities(tools = true, structuredOutput = false))
    assert(!Echo.spendsPersonalPlan)
    assertEquals(Echo.check("any"), Right(()))
    assertEquals(Echo.models(AiHttp.Offline), Right(Nil))
    assertEquals(Echo.doctor(online = true, AiHttp.Offline), Nil)
    assertEquals(Echo.description, "")

  test("one turn: the request in, the answer out; a turn's defaults"):
    val req = AiTurnRequest("m", "sys", List(AiMessage.User("hi")), Nil, None, 16000, 1, 4)
    val t = Echo.turn(req, AiHttp.Offline).toOption.get
    assertEquals((t.text, t.toolCalls, t.stop, t.servedBy), ("hi", Nil, AiStop.EndTurn, None))
    assertEquals(t.usage, Some(AiUsage(10, 2, 0, 0)))
    assertEquals(Echo.turn(req.copy(messages = Nil), AiHttp.Offline), Left(AiFailure.Error("no user message")))
    assertEquals(AiFailure.LimitReached("limit"), AiFailure.LimitReached("limit", None))

  test("the offline client sends nothing"):
    assertEquals(AiHttp.Offline.post("https://x.example", Map.empty, "{}", 5), Left("offline"))
    assertEquals(AiHttp.Offline.get("https://x.example", Map.empty, 5), Left("offline"))

  test("a context written before AI providers existed still compiles and refuses one; the SPI is still 1"):
    val ctx = new PluginContext:
      def http = new PluginHttp { def get(url: String, headers: Map[String, String]) = Left("offline") }
      def log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = None
      def secret(s: Secret) = Left("none")
      def secretVaults = Registry.refusing("no vaults")
    assert(ctx.aiProviders.add("echo", Echo).left.exists(_.contains("no AI providers")))
    assertEquals(Spi.Version, 2)
