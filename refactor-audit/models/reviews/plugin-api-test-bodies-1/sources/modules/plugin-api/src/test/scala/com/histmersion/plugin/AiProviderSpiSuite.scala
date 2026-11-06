package com.histmersion.plugin

/** The AI-provider part of the SPI (docs/research-plugins.md §22). */
class AiProviderSpiSuite extends munit.FunSuite:

  private object Echo extends AiProvider:
    val title = _root_.com.histmersion.plugin.AiProvider.TitleResult("Echo")
    def turn(r: AiTurn.Request, http: AiHttp) =
      r.messages
        .collectFirst { case AiMessage.User(t) => t }
        .toRight(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message("no user message")))
        .map(t => AiTurn(_root_.com.histmersion.plugin.AiTurn.Text((t).value), usage = Some(AiUsage(_root_.com.histmersion.plugin.AiUsage.Input((10).toLong), _root_.com.histmersion.plugin.AiUsage.Output((2).toLong)))))

  test("a provider's defaults: tools, no native structured output, no personal plan, nothing to check or list"):
    assertEquals(Echo.capabilities, AiCapabilities(tools = _root_.com.histmersion.plugin.AiCapabilities.Tools(true), structuredOutput = _root_.com.histmersion.plugin.AiCapabilities.StructuredOutput(false)))
    assert(!(Echo.spendsPersonalPlan).value)
    assertEquals(Echo.check(_root_.com.histmersion.plugin.AiProvider.CheckModel("any")), Right(()))
    assertEquals(Echo.models(AiHttp.Offline), Right(Nil))
    assertEquals(Echo.doctor(online = _root_.com.histmersion.plugin.AiProvider.DoctorOnline(true), AiHttp.Offline), Nil)
    assertEquals(((Echo.description).value: String), "")

  test("one turn: the request in, the answer out; a turn's defaults"):
    val req = AiTurn.Request(_root_.com.histmersion.plugin.AiTurn.Request.Model("m"), _root_.com.histmersion.plugin.AiTurn.Request.System("sys"), List(AiMessage.User(_root_.com.histmersion.plugin.AiMessage.User.Text("hi"))), Nil, None, _root_.com.histmersion.plugin.AiTurn.Request.MaxTokens(16000), _root_.com.histmersion.plugin.AiTurn.Request.Iteration(1), _root_.com.histmersion.plugin.AiTurn.Request.MaxIterations(4))
    val t = Echo.turn(req, AiHttp.Offline).toOption.get
    assertEquals(({ val refactorTuple = (t.text, t.toolCalls, t.stop, t.servedBy); ((refactorTuple._1).value, refactorTuple._2, refactorTuple._3, (refactorTuple._4).map(refactorValue => (refactorValue).value)) }: (String, List[com.histmersion.plugin.AiTool.Call], com.histmersion.plugin.AiStop, Option[String])), ("hi", Nil, AiStop.EndTurn, None))
    assertEquals(t.usage, Some(AiUsage(_root_.com.histmersion.plugin.AiUsage.Input((10).toLong), _root_.com.histmersion.plugin.AiUsage.Output((2).toLong), _root_.com.histmersion.plugin.AiUsage.CacheRead((0).toLong), _root_.com.histmersion.plugin.AiUsage.CacheWrite((0).toLong))))
    assertEquals(Echo.turn(req.copy(messages = Nil), AiHttp.Offline), Left(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message("no user message"))))
    assertEquals(AiFailure.LimitReached(_root_.com.histmersion.plugin.AiFailure.LimitReached.Message("limit")), AiFailure.LimitReached(_root_.com.histmersion.plugin.AiFailure.LimitReached.Message("limit"), None))

  test("the offline client sends nothing"):
    assertEquals(((AiHttp.Offline.post(_root_.com.histmersion.plugin.AiHttp.PostUrl("https://x.example"), Map.empty, _root_.com.histmersion.plugin.AiHttp.PostBody("{}"), _root_.com.histmersion.plugin.AiHttp.PostTimeoutSeconds(5))).left.map(refactorError => (refactorError).value): Either[String, com.histmersion.plugin.AiHttp.Response]), Left("offline"))
    assertEquals(((AiHttp.Offline.get(_root_.com.histmersion.plugin.AiHttp.GetUrl("https://x.example"), Map.empty, _root_.com.histmersion.plugin.AiHttp.GetTimeoutSeconds(5))).left.map(refactorError2 => (refactorError2).value): Either[String, com.histmersion.plugin.AiHttp.Response]), Left("offline"))

  test("a context written before AI providers existed still compiles and refuses one; the SPI is still 1"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http { def get(url: _root_.com.histmersion.plugin.Plugin.Http.GetUrl, headers: Map[_root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1, _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2]): Either[_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1, _root_.com.histmersion.plugin.Plugin.HttpResponse.Body] = Left(_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1("offline")) }
      def log = new Plugin.Log { def info(m: _root_.com.histmersion.plugin.Plugin.Log.InfoMessage): Unit = (); def warn(m: _root_.com.histmersion.plugin.Plugin.Log.WarnMessage): Unit = () }
      def env(name: _root_.com.histmersion.plugin.Plugin.Context.EnvName): _root_.scala.Option[_root_.com.histmersion.plugin.Plugin.Context.EnvResult] = None
      def secret(s: Secret): Either[_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1, _root_.com.histmersion.plugin.Plugin.Context.SecretResultPart2] = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))
    assert(ctx.aiProviders.add(_root_.com.histmersion.plugin.Registry.AddId("echo"), Echo).left.exists((_).value.contains("no AI providers")))
    assertEquals(Spi.Version, 2)
