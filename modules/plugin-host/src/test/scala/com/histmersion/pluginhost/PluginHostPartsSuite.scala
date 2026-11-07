package com.histmersion.pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.plugin.*

/** The parts of the host the server and the plugin testkit share: the media judgement, the catalogue paging, the AI
  * guards and the acquisition client's policy.
  */
class PluginHostPartsSuite extends munit.FunSuite:

  private def item(licence: String, image: Option[MediaItem.ImageUrl] = Some(MediaItem.ImageUrl("https://x.example/1.jpg"))) =
    MediaItem(
      com.histmersion.plugin.MediaItem.Key("k1"),
      com.histmersion.plugin.MediaItem.Title("A picture"),
      com.histmersion.plugin.MediaItem.PageUrl("https://x.example/1"),
      image,
      Right(MediaRights(Licence(licence), credit = com.histmersion.plugin.MediaRights.Credit("A. Photographer")))
    )

  test("a media record is kept only with a readable, allowed licence and an image"):
    val allow = LicencePolicy.of(com.histmersion.domain.Licence.AllowList("cc0,cc-by"))
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC-BY-4.0")), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")), None)
    assert(
      PluginMedia.refusal(PluginMedia.record(item("CC BY-NC 4.0")), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")).exists(_.value.contains("not allowed"))
    )
    assertEquals(
      PluginMedia.refusal(PluginMedia.record(item("CC0-1.0", image = None)), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")),
      Some("no image")
    )
    assertEquals(
      PluginMedia.refusal(PluginMedia.record(item("CC0-1.0").modify(_.withheld).setTo(Some(MediaItem.Withheld("human remains")))), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")),
      Some("human remains")
    )
    assertEquals(
      PluginMedia.refusal(
        PluginMedia.record(item("CC0-1.0").modify(_.rights).setTo(Right(MediaRights(Licence("CC0-1.0"))))),
        allow,
        com.histmersion.pluginhost.PluginMedia.Provider("mine")
      ),
      Some("no credit line")
    )

  final private class Paged(pages: Map[String, Either[String, CataloguePage]]) extends EventCatalogue:
    val title = com.histmersion.plugin.EventCatalogue.TitleResult("t"); val licence = Licence("l"); val citation = com.histmersion.plugin.EventCatalogue.CitationResult("c"); val home = com.histmersion.plugin.EventCatalogue.HomeResult("h")
    def parts(q: CatalogueQuery) = pages.keys.toList.map(EventCatalogue.PartsResult(_))
    def read(part: EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http) = pages(part.value).left.map(EventCatalogue.ReadResultPart1(_))

  private val offline = new Plugin.Http:
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) = Left(Plugin.Http.GetResultPart1("offline"))

  test("a part is read page by page until no next page, a repeat, a part done or a full run"):
    val cat = Paged(
      Map(
        "a" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("a2")))),
        "a2" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("a2")))),
        "b" -> Left("down"),
        "c" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("done"))))
      )
    )
    def read(first: PluginEvents.PartName, done: PluginEvents.PartName => Boolean = _ => false, full: => Boolean = false) =
      val seen = List.newBuilder[(PluginEvents.PartName, Boolean)]
      PluginEvents.readPart(cat, first, CatalogueQuery(None, Year(2025)), offline, done, full)((p, got) =>
        seen += p -> got.isRight
      )
      seen.result()
    assertEquals(read(PluginEvents.PartName("a")), List("a" -> true, "a2" -> true))
    assertEquals(read(PluginEvents.PartName("b")), List("b" -> false))
    assertEquals(read(PluginEvents.PartName("c"), done = _.value == "done"), List("c" -> true))
    assertEquals(read(PluginEvents.PartName("a"), done = _.value == "a"), Nil)
    assertEquals(read(PluginEvents.PartName("a"), full = true), Nil)

  private val request = AiTurn.Request(AiTurn.Request.Model("m"), AiTurn.Request.System("s"), List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("hi"))), Nil, None, AiTurn.Request.MaxTokens(10), AiTurn.Request.Iteration(1), AiTurn.Request.MaxIterations(1))

  test("an AI provider's exception or null is its failure"):
    val throwing = new AiProvider:
      val title = com.histmersion.plugin.AiProvider.TitleResult("t")
      override def check(model: AiProvider.CheckModel) = throw IllegalStateException("no")
      def turn(r: AiTurn.Request, http: AiHttp) = throw IllegalStateException("boom")
    val nulls = new AiProvider:
      val title = com.histmersion.plugin.AiProvider.TitleResult("t")
      def turn(r: AiTurn.Request, http: AiHttp) = Right(null)
    assertEquals(PluginAi.check(throwing, com.histmersion.pluginhost.PluginAi.ModelName("m")), Left("its check threw IllegalStateException"))
    assertEquals(PluginAi.turn(throwing, request, AiHttp.Offline), Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("IllegalStateException: boom"))))
    assertEquals(PluginAi.turn(nulls, request, AiHttp.Offline), Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("the provider answered null"))))

  /** Answers in turn, recording each request's headers. */
  final private class Scripted(answers: Either[HttpTransport.Problem, HttpAnswer]*) extends HttpTransport:
    private val left = scala.collection.mutable.Queue(answers*)
    val sent = List.newBuilder[Map[String, String]]
    def get(url: HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue]) =
      sent += headers.map((k, v) => (k.value, v.value))
      left.dequeue()

  private def answer(code: HttpAnswer.Code, body: String, headers: (com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue)*) =
    Right(HttpAnswer(code, headers.toMap, HttpAnswer.Body(body.getBytes("UTF-8"))))

  private val quiet = new HostLog:
    def info(line: HostLog.Line) = ()
    def warn(line: HostLog.Line) = ()

  test("the acquisition client: a slow-down asked again, a challenge ends the request, the User-Agent is the host's"):
    val waits = List.newBuilder[Long]
    val busy = Scripted(answer(HttpAnswer.Code(503), "busy", com.histmersion.domain.CoreQuota.HeaderName("retry-after") -> com.histmersion.domain.CoreQuota.HeaderValue("2")), answer(HttpAnswer.Code(200), "ok"))
    val http = PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"), busy, sleep = ms => waits += ms.value, log = quiet)
    assertEquals(http.bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/a"), Map(com.histmersion.domain.CoreQuota.HeaderName("user-agent") -> com.histmersion.domain.CoreQuota.HeaderValue("mine"))).map(b => String(b.value, "UTF-8")), Right("ok"))
    assertEquals(waits.result(), List(2000L))
    assert(busy.sent.result().forall(_.get("User-Agent").contains("mine")))
    val own = Scripted(answer(HttpAnswer.Code(200), "ok"))
    PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"), own, log = quiet).bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/a"))
    assertEquals(own.sent.result().map(_.get("User-Agent")), List(Some("histmersion/0.1")))
    val challenged = Scripted(answer(HttpAnswer.Code(403),"<html><title>Just a moment...</title></html>"))
    val got = PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("ua"), challenged, sleep = _ => fail("a challenge is never retried"), log = quiet)
      .bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/b"))
    assert(got.left.exists(_.value.contains("bot-protection challenge")), got.toString)
    val down = Scripted(Left(HttpTransport.Problem("x unreachable")), Left(HttpTransport.Problem("x unreachable")))
    assertEquals(
      PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("ua"), down, tries = com.histmersion.pluginhost.PolicyHttp.Tries(2), sleep = _ => (), log = quiet).bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/c")),
      Left("x unreachable")
    )
    val missing = Scripted(answer(HttpAnswer.Code(404),"no such work"))
    assertEquals(
      PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("ua"), missing, log = quiet).bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/d")),
      Left("https://x.example/d: HTTP 404 no such work")
    )

  // ── a plugin built against another SPI generation ─────────────────────────────────────────────

  /** What linking an SPI-1 plugin against SPI 2 throws: its Plugin.Info read `Spi.Version` at run time. */
  private val spi1 = NoSuchMethodError("'int com.histmersion.plugin.Spi$.Version()'")

  test("a plugin built against SPI 1 is recognised by the member it reads, and told to rebuild"):
    assertEquals(PluginHost.foreignSpi(spi1), Some(Some(1)))
    assertEquals(
      PluginHost.failure(spi1),
      "it was built against plugin SPI 1; this Histmersion speaks SPI 2 — rebuild the plugin against SPI 2 " +
        "(`histmersion plugin repo` has its API)"
    )
    val wrapped = java.util.ServiceConfigurationError(
      "com.histmersion.plugin.Plugin: Provider com.example.OldPlugin could not be instantiated",
      spi1
    )
    assertEquals(PluginHost.foreignSpi(wrapped), Some(Some(1)))
    assertEquals(PluginHost.providerOf(wrapped), Some("com.example.OldPlugin"))

  test("another SPI type a plugin links against differently is another SPI; other errors are not"):
    val other = NoSuchMethodError("'scala.Option com.histmersion.plugin.WorkKey.year()'")
    assertEquals(PluginHost.foreignSpi(other), Some(None))
    assert(
      PluginHost.failure(other).value.startsWith("it was built against another plugin SPI; this Histmersion speaks SPI 2")
    )
    assertEquals(PluginHost.foreignSpi(NoSuchMethodError("'void org.example.Lib.go()'")), None)
    assert(PluginHost.failure(NoClassDefFoundError("org/example/Lib")).value.startsWith("class org.example.Lib is missing"))

  test("a plugin whose info cannot be read is not loaded: the error, not an exception"):
    val old = new Plugin:
      type Config = Unit
      def info = throw spi1
      override given configReader: hearth.kindlings.sconfigderivation.ConfigReader[Unit] = null
      def register(config: Unit, ctx: Plugin.Context) = Right(())
    assert(PluginHost.described(old).left.exists(_ eq spi1))
