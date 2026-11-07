package com.histmersion.pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.plugin.*

/** The parts of the host the server and the plugin testkit share: the media judgement, the catalogue paging, the AI
  * guards and the acquisition client's policy.
  */
class PluginHostPartsSuite extends munit.FunSuite:

  private def item(licence: String, image: Option[String] = Some("https://x.example/1.jpg")) =
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
      PluginMedia.refusal(PluginMedia.record(item("CC BY-NC 4.0")), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")).exists(_.contains("not allowed"))
    )
    assertEquals(
      PluginMedia.refusal(PluginMedia.record(item("CC0-1.0", image = None)), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")),
      Some("no image")
    )
    assertEquals(
      PluginMedia.refusal(PluginMedia.record(item("CC0-1.0").modify(_.withheld).setTo(Some("human remains"))), allow, com.histmersion.pluginhost.PluginMedia.Provider("mine")),
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
    def parts(q: CatalogueQuery) = pages.keys.toList
    def read(part: String, q: CatalogueQuery, http: Plugin.Http) = pages(part)

  private val offline = new Plugin.Http:
    def get(url: String, headers: Map[String, String]) = Left("offline")

  test("a part is read page by page until no next page, a repeat, a part done or a full run"):
    val cat = Paged(
      Map(
        "a" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("a2")))),
        "a2" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("a2")))),
        "b" -> Left("down"),
        "c" -> Right(CataloguePage(Nil, Some(com.histmersion.plugin.CataloguePage.Next("done"))))
      )
    )
    def read(first: String, done: String => Boolean = _ => false, full: => Boolean = false) =
      val seen = List.newBuilder[(String, Boolean)]
      PluginEvents.readPart(cat, first, CatalogueQuery(None, Year(2025)), offline, done, full)((p, got) =>
        seen += p -> got.isRight
      )
      seen.result()
    assertEquals(read("a"), List("a" -> true, "a2" -> true))
    assertEquals(read("b"), List("b" -> false))
    assertEquals(read("c", done = _ == "done"), List("c" -> true))
    assertEquals(read("a", done = _ == "a"), Nil)
    assertEquals(read("a", full = true), Nil)

  private val request = AiTurn.Request("m", "s", List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("hi"))), Nil, None, 10, 1, 1)

  test("an AI provider's exception or null is its failure"):
    val throwing = new AiProvider:
      val title = com.histmersion.plugin.AiProvider.TitleResult("t")
      override def check(model: String) = throw IllegalStateException("no")
      def turn(r: AiTurn.Request, http: AiHttp) = throw IllegalStateException("boom")
    val nulls = new AiProvider:
      val title = com.histmersion.plugin.AiProvider.TitleResult("t")
      def turn(r: AiTurn.Request, http: AiHttp) = Right(null)
    assertEquals(PluginAi.check(throwing, com.histmersion.pluginhost.PluginAi.ModelName("m")), Left("its check threw IllegalStateException"))
    assertEquals(PluginAi.turn(throwing, request, AiHttp.Offline), Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("IllegalStateException: boom"))))
    assertEquals(PluginAi.turn(nulls, request, AiHttp.Offline), Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("the provider answered null"))))

  /** Answers in turn, recording each request's headers. */
  final private class Scripted(answers: Either[String, HttpAnswer]*) extends HttpTransport:
    private val left = scala.collection.mutable.Queue(answers*)
    val sent = List.newBuilder[Map[String, String]]
    def get(url: String, headers: Map[String, String]) =
      sent += headers
      left.dequeue()

  private def answer(code: Int, body: String, headers: (String, String)*) =
    Right(HttpAnswer(code, headers.toMap, body.getBytes("UTF-8")))

  private val quiet = new HostLog:
    def info(line: String) = ()
    def warn(line: String) = ()

  test("the acquisition client: a slow-down asked again, a challenge ends the request, the User-Agent is the host's"):
    val waits = List.newBuilder[Long]
    val busy = Scripted(answer(503, "busy", "retry-after" -> "2"), answer(200, "ok"))
    val http = PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"), busy, sleep = waits += _, log = quiet)
    assertEquals(http.bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/a"), Map("user-agent" -> "mine")).map(String(_, "UTF-8")), Right("ok"))
    assertEquals(waits.result(), List(2000L))
    assert(busy.sent.result().forall(_.get("User-Agent").contains("mine")))
    val own = Scripted(answer(200, "ok"))
    PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"), own, log = quiet).bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/a"))
    assertEquals(own.sent.result().map(_.get("User-Agent")), List(Some("histmersion/0.1")))
    val challenged = Scripted(answer(403, "<html><title>Just a moment...</title></html>"))
    val got = PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("ua"), challenged, sleep = _ => fail("a challenge is never retried"), log = quiet)
      .bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/b"))
    assert(got.left.exists(_.contains("bot-protection challenge")), got.toString)
    val down = Scripted(Left("x unreachable"), Left("x unreachable"))
    assertEquals(
      PolicyHttp(com.histmersion.domain.HttpPolicy.UserAgent("ua"), down, tries = com.histmersion.pluginhost.PolicyHttp.Tries(2), sleep = _ => (), log = quiet).bytes(com.histmersion.pluginhost.HttpTransport.Url("https://x.example/c")),
      Left("x unreachable")
    )
    val missing = Scripted(answer(404, "no such work"))
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
      PluginHost.failure(other).startsWith("it was built against another plugin SPI; this Histmersion speaks SPI 2")
    )
    assertEquals(PluginHost.foreignSpi(NoSuchMethodError("'void org.example.Lib.go()'")), None)
    assert(PluginHost.failure(NoClassDefFoundError("org/example/Lib")).startsWith("class org.example.Lib is missing"))

  test("a plugin whose info cannot be read is not loaded: the error, not an exception"):
    val old = new Plugin:
      type Config = Unit
      def info = throw spi1
      override given configReader: hearth.kindlings.sconfigderivation.ConfigReader[Unit] = null
      def register(config: Unit, ctx: Plugin.Context) = Right(())
    assert(PluginHost.described(old).left.exists(_ eq spi1))
