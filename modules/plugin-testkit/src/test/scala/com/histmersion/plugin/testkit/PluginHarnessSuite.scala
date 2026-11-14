package com.histmersion
package plugin.testkit

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.*
import com.histmersion.pluginhost.{FetchChain, HttpAnswer, HttpTransport, PluginAi, Registries}
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The harness against the test plugins in `kit/`: loading as the server loads, each runner, and each HTTP mode against
  * a local server.
  */
class PluginHarnessSuite extends munit.FunSuite:

  /** A body the host's PDF check accepts: `%PDF` and more than a KiB. */
  private val Pdf = ("%PDF-1.7\n" + "x" * 2048 + "\n%%EOF\n").getBytes(UTF_8)
  private val JustAMoment = "<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>"

  /** The paths and User-Agents a local site was asked for, while `body` ran. */
  private def withSite(body: (String, ConcurrentLinkedQueue[(String, String)]) => Unit): Unit =
    val seen = ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    def reply(ex: HttpExchange, code: Int, bytes: Array[Byte], contentType: String): Unit =
      ex.getResponseHeaders.add("Content-Type", contentType)
      ex.getResponseHeaders.add("Set-Cookie", "session=private")
      ex.sendResponseHeaders(code, bytes.length.toLong)
      ex.getResponseBody.write(bytes)
      ex.close()
    server.createContext(
      "/",
      (ex: HttpExchange) =>
        val uri = ex.getRequestURI
        seen.add(uri.toString -> Option(ex.getRequestHeaders.getFirst("User-Agent")).getOrElse(""))
        uri.getPath match
          case "/10.1/ok.pdf"               => reply(ex, 200, Pdf, "application/pdf")
          case "/10.1/tiny.pdf"             => reply(ex, 200, "%PDF-1.7".getBytes(UTF_8), "application/pdf")
          case "/10.1/challenge.pdf"        => reply(ex, 403, JustAMoment.getBytes(UTF_8), "text/html")
          case p if p.startsWith("/works/") =>
            reply(
              ex,
              200,
              """{"doi":"https://doi.org/10.1/ok","display_name":"A Book","publication_year":2020,
            |"authorships":[{"author":{"display_name":"A. Author"}}]}""".stripMargin.getBytes(UTF_8),
              "application/json"
            )
          case _ => reply(ex, 404, "nothing here".getBytes(UTF_8), "text/plain")
    )
    server.start()
    try body(s"http://127.0.0.1:${server.getAddress.getPort}", seen)
    finally server.stop(0)

  private val tmp = FunFixture[Path](
    _ => Files.createTempDirectory("testkit"),
    d => Files.walk(d).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  )

  private def loaded(
      id: Plugin.Info.Id,
      config: String = "",
      http: HttpMode = HttpMode.Offline,
      secrets: Map[Secret.Ref.ParseText, Secret.Resolved] = Map.empty,
      env: Map[String, String] = Map.empty
  ): PluginHarness =
    PluginHarness.load(id, config, http, secrets, env).fold(e => fail(e.sentences.mkString("; ")), identity)

  private val KitFetcher = Plugin.Info.Id("kit-fetcher")
  private val KitMedia = Plugin.Info.Id("kit-media")
  private val KitEvents = Plugin.Info.Id("kit-events")
  private val KitAi = Plugin.Info.Id("kit-ai")
  private val KitVault = Plugin.Info.Id("kit-vault")

  // ── loading ────────────────────────────────────────────────────────────────────────────────────

  test("a plugin loads as the server loads it, and lists what it registered"):
    val h = loaded(KitFetcher)
    assertEquals(h.contributions.provides, List("source-fetcher:kit-fetcher"))
    assertEquals(loaded(KitVault).contributions.secretVaults, List("kit-vault"))

  test("the server's own refusals: an unknown id, no defaults, a failed register, a bad setting, disabled"):
    def why(id: Plugin.Info.Id, config: String = ""): String =
      PluginHarness.load(id, config, env = Map.empty).swap.map(_.sentences.mkString("; ")).getOrElse(fail("loaded"))
    assert(why(Plugin.Info.Id("nope")).contains("no plugin id 'nope'"), why(Plugin.Info.Id("nope")))
    assert(
      why(Plugin.Info.Id("kit-nodefaults")).contains("META-INF/histmersion/plugins/kit-nodefaults.conf"),
      why(Plugin.Info.Id("kit-nodefaults"))
    )
    assert(why(Plugin.Info.Id("kit-broken")).contains("broken on purpose"), why(Plugin.Info.Id("kit-broken")))
    assert(
      why(KitFetcher, "base-urll = \"x\"").contains(
        "histmersion.plugins.kit-fetcher.base-urll is no setting of the plugin"
      )
    )
    assert(why(KitFetcher, "enabled = false").contains("is switched off"))
    assert(
      why(KitFetcher, "token = \"nonsense\"")
        .contains("not a bare value") || why(KitFetcher, "token = \"nonsense\"").contains("reference")
    )

  test("the config is the block's keys or the whole histmersion.plugins block, over the defaults"):
    withSite { (base, _) =>
      val keys = loaded(KitFetcher, s"base-url = \"$base\"", HttpMode.Live)
      val whole = loaded(KitFetcher, s"histmersion.plugins.kit-fetcher { base-url = \"$base\" }", HttpMode.Live)
      for h <- List(keys, whole) do assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
    }

  test("secrets come from the test: a stand-in vault or the environment, never a real vault"):
    val viaVault =
      loaded(
        KitAi,
        "api-key = \"keepassxc:Models/key\"",
        secrets = Map(Secret.Ref.ParseText("keepassxc:Models/key") -> Secret.Resolved("k-1234"))
      )
    assertEquals(viaVault.contributions.secretVaults, Nil, "the stand-in vault is not the plugin's")
    val viaEnv = loaded(
      KitAi,
      "api-key = \"env:KIT_KEY\"",
      secrets = Map(Secret.Ref.ParseText("env:KIT_KEY") -> Secret.Resolved("k-5678"))
    )
    val ai = ScriptedAiHttp(Right(AiHttp.Response(AiHttp.Response.Status(200), AiHttp.Response.Body("hello"))))
    assertEquals(
      viaEnv.aiTurn(Registries.AiProvidersKey("kit-ai"), request, ai).turn,
      Some(Right(AiTurn(AiTurn.Text("hello"))))
    )
    assertEquals(ai.requests.map(_.headers(CoreQuota.HeaderName("x-api-key"))), List("***"))
    val missing = PluginHarness.load(KitAi, "api-key = \"keepassxc:Models/key\"", env = Map.empty)
    assert(
      missing.left.exists(_.exists {
        case Problem.Plugin.Vault(vault, Problem.Plugin.Retrieval.NoVault) => vault == Problem.Plugin.Id("keepassxc")
        case _                                                             => false
      }),
      missing.toString
    )

  // ── fetch ──────────────────────────────────────────────────────────────────────────────────────

  tmp.test("fetch: found, saved, with its licence; the host's PDF check and bot-challenge handling apply") { dir =>
    val h = loaded(
      KitFetcher,
      "base-url = \"https://books.example\"",
      HttpMode.Stub(
        Map(
          HttpTransport.Url("https://books.example/10.1/ok.pdf") -> StubAnswer
            .ok(HttpAnswer.Body(Pdf), CoreQuota.HeaderValue("application/pdf")),
          HttpTransport.Url("https://books.example/10.1/tiny.pdf") -> StubAnswer.ok(
            HttpAnswer.Body("%PDF-1.7".getBytes(UTF_8))
          ),
          HttpTransport.Url("https://books.example/10.1/cf.pdf") -> StubAnswer(
            HttpAnswer.Code(403),
            HttpAnswer.Body(JustAMoment.getBytes(UTF_8))
          )
        )
      )
    )
    val found = h.fetch(WorkKey(doi = Some(Doi("10.1/ok"))), saveTo = Some(dir))
    found.outcome match
      case FetchOutcome.Found(by, file, Some(saved)) if by.value == "kit-fetcher" =>
        assertEquals(Files.readAllBytes(saved).toSeq, Pdf.toSeq)
        assertEquals(saved.getFileName.toString, "kit-fetcher-10.1_ok.pdf")
      case other => fail(s"expected a saved copy, got $other")
    assertEquals(found.licence, Some("CC-BY-4.0"))
    assertEquals(found.requests, List("GET https://books.example/10.1/ok.pdf"))
    assertEquals(
      h.fetch(WorkKey(doi = Some(Doi("10.1/tiny")))).outcome,
      FetchOutcome.Miss(
        Problem.Io.Corrupt(
          Problem.Io.Format.Pdf,
          Problem.Where("https://books.example/10.1/tiny.pdf"),
          Problem.Io.Damage.Undecodable
        )
      )
    )
    h.fetch(WorkKey(doi = Some(Doi("10.1/cf")))).outcome match
      case FetchOutcome.Refused(why) => assert(why.sentences.mkString.contains("bot-protection challenge"), why)
      case other                     => fail(s"expected a refusal, got $other")
    val none = h.fetch(WorkKey(doi = Some(Doi("10.1/none")))).outcome
    assert(
      none match
        case FetchOutcome.Miss(why) => why.sentences.mkString.contains("HTTP 404")
        case _                      => false
      ,
      none
    )
    assertEquals(
      h.fetch(WorkKey(title = Some(WorkKey.Title("No DOI")))).answers,
      List(
        FetcherAnswer(
          FetchChain.Answer.Fetcher("kit-fetcher"),
          false,
          Problem.Invalid.Missing.here(Problem.Field("doi"))
        )
      )
    )
  }

  test("live: the server's User-Agent goes out"):
    withSite { (base, seen) =>
      val h = loaded(
        KitFetcher,
        s"base-url = \"$base\"",
        HttpMode.Live,
        env = Map("HISTMERSION_ACQUIRE_USER_AGENT" -> "kit-agent/1")
      )
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
      assertEquals(seen.asScala.toList, List("/10.1/ok.pdf" -> "kit-agent/1"))
    }

  tmp.test("record, then replay offline: the same answers, no secret and no cookie in the files") { dir =>
    val config = "token = \"env:KIT_TOKEN\""
    val env = Map("KIT_TOKEN" -> "tok-secret-42")
    withSite { (base, seen) =>
      val h = loaded(KitFetcher, config + s"\nbase-url = \"$base\"", HttpMode.Record(dir), env = env)
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/challenge")))).outcome.isInstanceOf[FetchOutcome.Refused])
      assert(seen.asScala.exists(_._1.contains("key=tok-secret-42")), "the live request carries the real key")
      // the replay must not reach the site
      val replayed = loaded(KitFetcher, config + s"\nbase-url = \"$base\"", HttpMode.Replay(dir), env = env)
      seen.clear()
      val ok = replayed.fetch(WorkKey(doi = Some(Doi("10.1/ok"))))
      assertEquals(ok.file.map(_.bytes.value.toSeq), Some(Pdf.toSeq))
      assert(replayed.fetch(WorkKey(doi = Some(Doi("10.1/challenge")))).outcome.isInstanceOf[FetchOutcome.Refused])
      assert(seen.isEmpty, seen.toString)
      val unrecorded = intercept[UnrecordedRequest](replayed.fetch(WorkKey(doi = Some(Doi("10.1/other")))))
      assert(unrecorded.getMessage.contains("/10.1/other.pdf?key={secret}"), unrecorded.getMessage)
    }
    val files = Files.list(dir).iterator.asScala.toList.map(_.getFileName.toString).sorted
    assertEquals(files.filter(_.endsWith(".http")), List("001-127.0.0.1.http", "002-127.0.0.1.http"))
    val text = files.filterNot(_.endsWith(".pdf")).map(f => Files.readString(dir.resolve(f), UTF_8)).mkString("\n")
    assert(!text.contains("tok-secret-42") && !text.contains("session=private"), text)
    assert(text.contains("?key={secret}") && text.contains("status: 403"), text)
  }

  tmp.test("a work from its DOI, as an ingest looks it up — recorded and replayed with the rest") { dir =>
    withSite { (base, _) =>
      val env =
        Map("HISTMERSION_ACQUIRE_OPENALEX_URL" -> base, "HISTMERSION_ACQUIRE_CONTACT_EMAIL" -> "someone@example.org")
      val live = loaded(KitFetcher, s"base-url = \"$base\"", HttpMode.Record(dir), env = env)
      val work = WorkKey.fromDoi(com.histmersion.plugin.Doi.assume("https://doi.org/10.1/OK"), live)
      assertEquals(
        work,
        Right(
          WorkKey(
            doi = Some(Doi("10.1/ok")),
            title = Some(WorkKey.Title("A Book")),
            authors = List(WorkKey.Authors("A. Author")),
            year = Some(Year(2020))
          )
        )
      )
      assert(!live.requests.exists(_.value.contains("someone")), "no contact e-mail from a test")
      val replay = loaded(KitFetcher, s"base-url = \"$base\"", HttpMode.Replay(dir), env = env)
      assertEquals(WorkKey.fromDoi(com.histmersion.plugin.Doi("10.1/ok"), replay), work)
    }
  }

  // ── the other kinds ────────────────────────────────────────────────────────────────────────────

  test("media: each record judged as the server's import judges it"):
    val h = loaded(
      KitMedia,
      "",
      HttpMode.Stub(
        Map(
          HttpTransport.Url("https://pictures.example/search?q=Troy") ->
            StubAnswer.text(
              HttpAnswer.Text(
                "a|CC-BY-4.0|https://pictures.example/a.jpg\nb|CC BY-NC 4.0|https://pictures.example/b.jpg\nc|CC0-1.0|\nnonsense"
              )
            )
        )
      )
    )
    val r = h.searchMedia(
      Registries.MediaCollectionsKey("kit-media"),
      MediaPlace(MediaPlace.Id("pleiades_550595"), MediaPlace.Label("Troy"))
    )
    assertEquals(r.kept.map(v => v.key -> v.licence), List("a" -> Some("CC-BY-4.0")))
    assertEquals(
      r.verdicts.map(v => v.key -> v.refused.isDefined),
      List("a" -> false, "b" -> true, "c" -> true, "the text data at record nonsense cannot be decoded" -> true)
    )
    assertEquals(r.verdicts(2).refused.map(_.sentence), Some("kit-media did not take c: no image"))
    assertEquals(
      h.searchMedia(Registries.MediaCollectionsKey("other"), MediaPlace(MediaPlace.Id("x"), MediaPlace.Label("y")))
        .failure
        .map(_.sentence),
      Some("no media collection 'other'")
    )

  test("events: every part read page by page, each record checked and dated by the server"):
    val h = loaded(
      KitEvents,
      "",
      HttpMode.Stub(
        Map(
          HttpTransport.Url("https://events.example/p1") -> StubAnswer.text(
            HttpAnswer.Text("thera|Eruption of Thera|-1620|-1600\nbad|Backwards|-1500|-1600")
          ),
          HttpTransport.Url("https://events.example/p2") -> StubAnswer.text(HttpAnswer.Text("vesuvius|Vesuvius|79|79"))
        )
      )
    )
    val r = h.readCatalogue(Registries.EventCataloguesKey("kit-events"), CatalogueQuery(None, Year(2025)))
    assertEquals(r.pages, List("p1" -> Right(2), "p2" -> Right(1)))
    assertEquals(
      r.accepted.map(e => (e.event.id, e.kind, e.notBefore, e.notAfter)),
      List(
        ("thera", "eruption", Some(Year(-1620)), Some(Year(-1600))),
        ("vesuvius", "eruption", Some(Year(79)), Some(Year(79)))
      )
    )
    assertEquals(r.refused, List("p1: kit-events.bad: notBefore, beginBy, endFrom, notAfter must be in order"))

  private val request = AiTurn.Request(
    AiTurn.Request.Model("m"),
    AiTurn.Request.System("system"),
    List(AiMessage.User(AiMessage.User.Text("hi"))),
    Nil,
    None,
    AiTurn.Request.MaxTokens(100),
    AiTurn.Request.Iteration(1),
    AiTurn.Request.MaxIterations(1)
  )

  test("AI: the provider's check first, then one call through the scripted transport"):
    val none = loaded(KitAi)
    assertEquals(
      none.aiTurn(Registries.AiProvidersKey("kit-ai"), request),
      AiReport(
        Left(
          Problem.Config.Setting(Problem.SettingKey("histmersion.plugins.kit-ai.api-key"), Problem.Config.Fault.Empty)
        ),
        None,
        Nil
      )
    )
    val keyed = loaded(KitAi, "api-key = \"value:k-0000\"")
    assert(keyed.warnings.exists(_.value.contains("value:***")), keyed.warnings.toString)
    val down = keyed.aiTurn(Registries.AiProvidersKey("kit-ai"), request, ScriptedAiHttp())
    assert(down.turn.exists(_.left.exists(_.isNotFound)), down.toString)

  test("a secret vault resolves as the server asks it"):
    val h = loaded(KitVault, "entries = [\"portal=s3cret\"]")
    assertEquals(h.resolveSecret(Secret.Ref.Vault.Value("kit-vault"), Secret.Ref.Vault.Ref("portal")), Right("s3cret"))
    assert(
      h.resolveSecret(Secret.Ref.Vault.Value("kit-vault"), Secret.Ref.Vault.Ref("other"))
        .left
        .exists(_.sentences.mkString.contains("it holds no 'other'"))
    )
