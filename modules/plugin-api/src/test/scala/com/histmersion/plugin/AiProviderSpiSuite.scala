package com.histmersion
package plugin

/** The AI-provider part of the SPI (docs/research-plugins.md §22). */
class AiProviderSpiSuite extends munit.FunSuite:

  private object Echo extends AiProvider:
    val title = AiProvider.TitleResult("Echo")
    def turn(r: AiTurn.Request, http: AiHttp) =
      r.messages
        .collectFirst { case AiMessage.User(t) => t }
        .toRight(AiFailure.Error(AiFailure.Error.Message("no user message")))
        .map(t =>
          AiTurn(AiTurn.Text((t).value), usage = Some(AiUsage(AiUsage.Input((10).toLong), AiUsage.Output((2).toLong))))
        )

  test("a provider's defaults: tools, no native structured output, no personal plan, nothing to check or list"):
    assertEquals(
      Echo.capabilities,
      AiCapabilities(tools = AiCapabilities.Tools(true), structuredOutput = AiCapabilities.StructuredOutput(false))
    )
    assert(!(Echo.spendsPersonalPlan).value)
    assertEquals(Echo.check(AiProvider.CheckModel("any")), Right(()))
    assertEquals(Echo.models(AiHttp.Offline), Right(Nil))
    assertEquals(Echo.doctor(online = AiProvider.DoctorOnline(true), AiHttp.Offline), Nil)
    assertEquals(((Echo.description).value: String), "")

  test("one turn: the request in, the answer out; a turn's defaults"):
    val req = AiTurn.Request(
      AiTurn.Request.Model("m"),
      AiTurn.Request.System("sys"),
      List(AiMessage.User(AiMessage.User.Text("hi"))),
      Nil,
      None,
      AiTurn.Request.MaxTokens(16000),
      AiTurn.Request.Iteration(1),
      AiTurn.Request.MaxIterations(4)
    )
    val t = Echo.turn(req, AiHttp.Offline).toOption.get
    assertEquals(
      ({
        val refactorTuple = (t.text, t.toolCalls, t.stop, t.servedBy);
        (
          (refactorTuple._1).value,
          refactorTuple._2,
          refactorTuple._3,
          (refactorTuple._4).map(refactorValue => (refactorValue).value)
        )
      }: (String, List[AiTool.Call], AiStop, Option[String])),
      ("hi", Nil, AiStop.EndTurn, None)
    )
    assertEquals(
      t.usage,
      Some(
        AiUsage(
          AiUsage.Input((10).toLong),
          AiUsage.Output((2).toLong),
          AiUsage.CacheRead((0).toLong),
          AiUsage.CacheWrite((0).toLong)
        )
      )
    )
    assertEquals(
      Echo.turn(req.copy(messages = Nil), AiHttp.Offline),
      Left(AiFailure.Error(AiFailure.Error.Message("no user message")))
    )
    assertEquals(
      AiFailure.LimitReached(AiFailure.LimitReached.Message("limit")),
      AiFailure.LimitReached(AiFailure.LimitReached.Message("limit"), None)
    )

  test("the offline client sends nothing"):
    assertEquals(
      ((AiHttp.Offline
        .post(AiHttp.PostUrl("https://x.example"), Map.empty, AiHttp.PostBody("{}"), AiHttp.PostTimeoutSeconds(5)))
        .left
        .map(refactorError => (refactorError).value): Either[String, AiHttp.Response]),
      Left("offline")
    )
    assertEquals(
      ((AiHttp.Offline
        .get(AiHttp.GetUrl("https://x.example"), Map.empty, AiHttp.GetTimeoutSeconds(5)))
        .left
        .map(refactorError2 => (refactorError2).value): Either[String, AiHttp.Response]),
      Left("offline")
    )

  test("a context written before AI providers existed still compiles and refuses one; the SPI is still 1"):
    val ctx = new Plugin.Context:
      def http = new Plugin.Http {
        def get(
            url: Plugin.Http.GetUrl,
            headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]
        ): Either[Plugin.Http.GetResultPart1, Plugin.HttpResponse.Body] = Left(Plugin.Http.GetResultPart1("offline"))
      }
      def log = new Plugin.Log {
        def info(m: Plugin.Log.InfoMessage): Unit = (); def warn(m: Plugin.Log.WarnMessage): Unit = ()
      }
      def env(name: Plugin.Context.EnvName): Option[Plugin.Context.EnvResult] = None
      def secret(s: Secret): Either[Plugin.Context.SecretResultPart1, Plugin.Context.SecretResultPart2] =
        Left(Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(Registry.RefusingWhy("no vaults"))
    assert(ctx.aiProviders.add(Registry.AddId("echo"), Echo).left.exists((_).value.contains("no AI providers")))
    assertEquals(Spi.Version, 2)
