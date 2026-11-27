package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.plugin.*
import com.histmersion.plugin.MediaItem
import com.histmersion.plugin.MediaRights
import com.histmersion.plugin.EventCatalogue
import com.histmersion.plugin.CataloguePage
import com.histmersion.plugin.AiMessage
import com.histmersion.plugin.AiProvider
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.HttpPolicy
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin's settings, read by a derived reader. */
final case class HelloSettings(greeting: String, contactEmail: String)
    derives hearth.kindlings.sconfigderivation.ConfigReader

/** The parts of the host the server and the plugin testkit share: the media judgement, the catalogue paging, the AI
  * guards and the acquisition client's policy.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class PluginHostPartsSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def item(
      licence: String,
      image: Option[MediaItem.ImageUrl] = Some(MediaItem.ImageUrl("https://x.example/1.jpg"))
  ) =
    MediaItem(
      MediaItem.Key("k1"),
      MediaItem.Title("A picture"),
      MediaItem.PageUrl("https://x.example/1"),
      image,
      Right(MediaRights(Licence(licence), credit = MediaRights.Credit("A. Photographer")))
    )

  test("a media record is kept only with a readable, allowed licence and an image"):
    val allow = LicencePolicy.of(domain.Licence.AllowList("cc0,cc-by"))
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC-BY-4.0")), allow, PluginMedia.Provider("mine")), None)
    PluginMedia.refusal(PluginMedia.record(item("CC BY-NC 4.0")), allow, PluginMedia.Provider("mine")) match
      case Some(PluginMedia.Refusal.LicenceNotAllowed(_, provider)) =>
        assertEquals(provider, PluginMedia.Provider("mine"))
      case other => fail("licence not refused")
    assertEquals(
      PluginMedia.refusal(PluginMedia.record(item("CC0-1.0", image = None)), allow, PluginMedia.Provider("mine")),
      Some(PluginMedia.Refusal.NoImage)
    )
    assertEquals(
      PluginMedia.refusal(
        PluginMedia.record(item("CC0-1.0").modify(_.withheld).setTo(Some(MediaItem.Withheld("human remains")))),
        allow,
        PluginMedia.Provider("mine")
      ),
      Some(PluginMedia.Refusal.Withheld(External.PluginSaid("human remains")))
    )
    assertEquals(
      PluginMedia.refusal(
        PluginMedia.record(item("CC0-1.0").modify(_.rights).setTo(Right(MediaRights(Licence("CC0-1.0"))))),
        allow,
        PluginMedia.Provider("mine")
      ),
      Some(PluginMedia.Refusal.Unreadable(Problem.Invalid.Missing.here(Problem.Field("credit"))))
    )

  final private class Paged(pages: Map[String, Either[String, CataloguePage]]) extends EventCatalogue:
    val title = EventCatalogue.Title("t"); val licence = Licence("l");
    val citation = EventCatalogue.Citation("c"); val home = EventCatalogue.Home("h")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parts(q: CatalogueQuery) = pages.keys.toList.map(EventCatalogue.ReadPart(_))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def read(part: EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http) =
      pages(part.value).left.map(text =>
        Problem.Upstream.Unreachable
          .here(Problem.Upstream.Name("x"), Problem.Upstream.Url("https://x.example"), External.Reported.of(text))
      )

  private val offline = new Plugin.Http:
    def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) = Left(
      Problem.Upstream.Unreachable
        .here(Problem.Upstream.Name("x"), Problem.Upstream.Url("https://x.example"), External.Reported("offline"))
    )

  test("a part is read page by page until no next page, a repeat, a part done or a full run"):
    val cat = Paged(
      Map(
        "a" -> Right(CataloguePage(Nil, Some(CataloguePage.Next("a2")))),
        "a2" -> Right(CataloguePage(Nil, Some(CataloguePage.Next("a2")))),
        "b" -> Left("down"),
        "c" -> Right(CataloguePage(Nil, Some(CataloguePage.Next("done"))))
      )
    )
    def read(
        first: PluginEvents.PartName,
        done: PluginEvents.PartName => Boolean = _ => false,
        full: => Boolean = false
    ) =
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

  private val request = AiTurn.Request(
    AiTurn.Request.Model("m"),
    AiTurn.Request.System("s"),
    List(AiMessage.User(AiMessage.User.Text("hi"))),
    Nil,
    None,
    AiTurn.Request.MaxTokens(10),
    AiTurn.Request.Iteration(1),
    AiTurn.Request.MaxIterations(1)
  )

  test("an AI provider's exception or null is its failure"):
    val throwing = new AiProvider:
      val title = AiProvider.Title("t")
      override def check(model: AiProvider.CheckModel) = throw IllegalStateException("no")
      def turn(r: AiTurn.Request, http: AiHttp) = throw IllegalStateException("boom")
    val nulls = new AiProvider:
      val title = AiProvider.Title("t")
      def turn(r: AiTurn.Request, http: AiHttp) = Right(null)
    def threw(answer: Either[Problem, ?]): Boolean = answer.left.exists {
      case Problem.Internal.Unexpected(_, where, e, reported) =>
        where == Problem.Where("the plugin") && e == Problem.Internal.ExceptionName("java.lang.IllegalStateException")
      case _ => false
    }
    assert(threw(PluginAi.check(throwing, PluginAi.ModelName("m"))))
    assert(threw(PluginAi.turn(throwing, request, AiHttp.Offline)))
    assertEquals(PluginAi.turn(nulls, request, AiHttp.Offline), Left(PluginMedia.answeredNothing))

  /** Answers in turn, recording each request's headers. */
  final private class Scripted(answers: Either[Problem, HttpAnswer]*) extends HttpTransport:
    private val left = scala.collection.mutable.Queue(answers*)
    val sent = List.newBuilder[Map[String, String]]
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
      sent += headers.map((k, v) => (k.value, v.value))
      left.dequeue()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def answer(code: HttpAnswer.Code, body: String, headers: (CoreQuota.HeaderName, CoreQuota.HeaderValue)*) =
    Right(HttpAnswer(code, headers.toMap, HttpAnswer.Body(body.getBytes("UTF-8"))))

  private val quiet = new HostLog:
    def info(line: HostLog.Line) = ()
    def warn(line: HostLog.Line) = ()

  test("the acquisition client: a slow-down asked again, a challenge ends the request, the User-Agent is the host's"):
    val waits = List.newBuilder[Long]
    val busy = Scripted(
      answer(HttpAnswer.Code(503), "busy", CoreQuota.HeaderName("retry-after") -> CoreQuota.HeaderValue("2")),
      answer(HttpAnswer.Code(200), "ok")
    )
    val http = PolicyHttp(
      HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"),
      busy,
      sleep = ms => waits += ms.value,
      log = quiet
    )
    assertEquals(
      http
        .bytes(
          HttpTransport.Url("https://x.example/a"),
          Map(CoreQuota.HeaderName("user-agent") -> CoreQuota.HeaderValue("mine"))
        )
        .map(b => String(b.value, "UTF-8")),
      Right("ok")
    )
    assertEquals(waits.result(), List(2000L))
    assert(busy.sent.result().forall(_.get("User-Agent").contains("mine")))
    val own = Scripted(answer(HttpAnswer.Code(200), "ok"))
    PolicyHttp(HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)"), own, log = quiet)
      .bytes(HttpTransport.Url("https://x.example/a"))
    assertEquals(own.sent.result().map(_.get("User-Agent")), List(Some("histmersion/0.1")))
    val challenged = Scripted(answer(HttpAnswer.Code(403), "<html><title>Just a moment...</title></html>"))
    val got =
      PolicyHttp(HttpPolicy.UserAgent("ua"), challenged, sleep = _ => fail("a challenge is never retried"), log = quiet)
        .bytes(HttpTransport.Url("https://x.example/b"))
    assert(
      got.left.exists {
        case Problem.Upstream.Challenged(_, _, code, _) => code == Problem.Upstream.Status.Forbidden
        case _                                          => false
      },
      got.toString
    )
    val unreachable =
      Problem.Upstream.Unreachable.here(
        Problem.Upstream.Name("x.example"),
        Problem.Upstream.Url("https://x.example/c"),
        External.Reported("reset")
      )
    val down = Scripted(Left(unreachable), Left(unreachable))
    assertEquals(
      PolicyHttp(HttpPolicy.UserAgent("ua"), down, tries = PolicyHttp.Tries(2), sleep = _ => (), log = quiet)
        .bytes(HttpTransport.Url("https://x.example/c")),
      Left(unreachable)
    )
    val missing = Scripted(answer(HttpAnswer.Code(404), "no such work"))
    assertEquals(
      PolicyHttp(HttpPolicy.UserAgent("ua"), missing, log = quiet).bytes(HttpTransport.Url("https://x.example/d")),
      Left(
        Problem.Upstream.Http.here(
          Problem.Upstream.Name("x.example"),
          Problem.Upstream.Url("https://x.example/d"),
          Problem.Upstream.Status.NotFound,
          External.ReplyExcerpt("no such work")
        )
      )
    )

  // ── a plugin built against another SPI generation ─────────────────────────────────────────────

  /** What linking an SPI-1 plugin against SPI 2 throws: its Plugin.Info read `Spi.Version` at run time. */
  private val spi1 = NoSuchMethodError("'int com.histmersion.plugin.Spi$.Version()'")

  test("a plugin built against SPI 1 is recognised by the member it reads, and told to rebuild"):
    assertEquals(PluginHost.foreignSpi(spi1), Some(Some(1)))
    assertEquals(
      PluginHost.failure(spi1),
      Problem.Plugin.Fault.SpiMismatch(Problem.Raw("1"), Problem.Raw("3"))
    )
    assertEquals(
      Problem.Plugin.Load(Problem.Plugin.Id("old"), PluginHost.failure(spi1)).sentence,
      "plugin old did not load: it was built against plugin SPI 1; this Histmersion speaks SPI 3 — rebuild the " +
        "plugin against SPI 3"
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
    assertEquals(PluginHost.failure(other), Problem.Plugin.Fault.ForeignSpi(Problem.Raw("3")))
    assert(
      Problem.Plugin
        .Load(Problem.Plugin.Id("x"), PluginHost.failure(other))
        .sentence
        .startsWith("plugin x did not load: it was built against another plugin SPI; this Histmersion speaks SPI 3")
    )
    assertEquals(PluginHost.foreignSpi(NoSuchMethodError("'void org.example.Lib.go()'")), None)
    assertEquals(
      PluginHost.failure(NoClassDefFoundError("org/example/Lib")),
      Problem.Plugin.Fault.MissingClass(Problem.Raw("org.example.Lib"))
    )

  test("a plugin whose info cannot be read is not loaded: the error, not an exception"):
    val old = new Plugin:
      type Config = Unit
      def info = throw spi1
      override given configReader: hearth.kindlings.sconfigderivation.ConfigReader[Unit] = null
      def register(config: Unit, ctx: Plugin.Context) = Right(())
    assert(PluginHost.described(old).left.exists(_ eq spi1))

  test("a setting a plugin's block lacks has no value, at histmersion.plugins.<id>.<key>, from a derived reader too"):
    import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}
    val id = Plugin.Info.Id("hello")
    val block = org.ekrich.config.ConfigFactory.parseString("greeting = hi").root
    val err = ConfigReader[HelloSettings].from(block).swap.getOrElse(fail("read without its key"))
    val missing = Vector(
      Problem.Config.Setting(Problem.SettingKey("histmersion.plugins.hello.contact-email"), Problem.Config.Fault.Empty)
    )
    assertEquals(PluginHost.describe(id, err).toVector, missing)
    assertEquals(PluginHost.describe(id, ConfigDecodingError.Missing(Nil, "contact-email")).toVector, missing)
    assertEquals(missing.head.sentence, "histmersion.plugins.hello.contact-email is empty")
