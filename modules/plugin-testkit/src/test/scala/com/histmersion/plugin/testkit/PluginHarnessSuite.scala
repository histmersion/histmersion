package com.histmersion.plugin.testkit

import com.histmersion.plugin.*
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The harness against the test plugins in `kit/`: loading as the server loads, each runner, and each
  * HTTP mode against a local server. */
class PluginHarnessSuite extends munit.FunSuite:

  /** A body the host's PDF check accepts: `%PDF` and more than a KiB. */
  private val Pdf = ("%PDF-1.7\n" + "x" * 2048 + "\n%%EOF\n").getBytes(UTF_8)
  private val JustAMoment = "<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>"

  /** The paths and User-Agents a local site was asked for, while `body` ran. */
  private def withSite(body: (String, ConcurrentLinkedQueue[(String, String)]) => Unit): Unit =
    val seen   = ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    def reply(ex: HttpExchange, code: Int, bytes: Array[Byte], contentType: String): Unit =
      ex.getResponseHeaders.add("Content-Type", contentType)
      ex.getResponseHeaders.add("Set-Cookie", "session=private")
      ex.sendResponseHeaders(code, bytes.length.toLong)
      ex.getResponseBody.write(bytes)
      ex.close()
    server.createContext("/", (ex: HttpExchange) =>
      val uri = ex.getRequestURI
      seen.add(uri.toString -> Option(ex.getRequestHeaders.getFirst("User-Agent")).getOrElse(""))
      uri.getPath match
        case "/10.1/ok.pdf"        => reply(ex, 200, Pdf, "application/pdf")
        case "/10.1/tiny.pdf"      => reply(ex, 200, "%PDF-1.7".getBytes(UTF_8), "application/pdf")
        case "/10.1/challenge.pdf" => reply(ex, 403, JustAMoment.getBytes(UTF_8), "text/html")
        case p if p.startsWith("/works/") =>
          reply(ex, 200, """{"doi":"https://doi.org/10.1/ok","display_name":"A Book","publication_year":2020,
            |"authorships":[{"author":{"display_name":"A. Author"}}]}""".stripMargin.getBytes(UTF_8), "application/json")
        case _ => reply(ex, 404, "nothing here".getBytes(UTF_8), "text/plain"))
    server.start()
    try body(s"http://127.0.0.1:${server.getAddress.getPort}", seen)
    finally server.stop(0)

  private val tmp = FunFixture[Path](_ => Files.createTempDirectory("testkit"), d =>
    Files.walk(d).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p)))

  private def loaded(id: String, config: String = "", http: HttpMode = HttpMode.Offline,
      secrets: Map[String, String] = Map.empty, env: Map[String, String] = Map.empty): PluginHarness =
    PluginHarness.load(id, config, http, secrets, env).fold(e => fail(e), identity)

  // ── loading ────────────────────────────────────────────────────────────────────────────────────

  test("a plugin loads as the server loads it, and lists what it registered"):
    val h = loaded("kit-fetcher")
    assertEquals(h.contributions.provides, List("source-fetcher:kit-fetcher"))
    assertEquals(loaded("kit-vault").contributions.secretVaults, List("kit-vault"))

  test("the server's own refusals: an unknown id, no defaults, a failed register, a bad setting, disabled"):
    def why(id: String, config: String = "") = PluginHarness.load(id, config, env = Map.empty).swap.getOrElse(fail("loaded"))
    assert(why("nope").contains("found: kit-"), why("nope"))
    assert(why("kit-nodefaults").contains("META-INF/histmersion/plugins/kit-nodefaults.conf"), why("kit-nodefaults"))
    assert(why("kit-broken").contains("broken on purpose"), why("kit-broken"))
    assert(why("kit-fetcher", "base-urll = \"x\"").contains("histmersion.plugins.kit-fetcher.base-urll: unknown setting"))
    assert(why("kit-fetcher", "enabled = false").contains("disabled"))
    assert(why("kit-fetcher", "token = \"nonsense\"").contains("not a bare value") || why("kit-fetcher", "token = \"nonsense\"").contains("reference"))

  test("the config is the block's keys or the whole histmersion.plugins block, over the defaults"):
    withSite { (base, _) =>
      val keys  = loaded("kit-fetcher", s"base-url = \"$base\"", HttpMode.Live)
      val whole = loaded("kit-fetcher", s"histmersion.plugins.kit-fetcher { base-url = \"$base\" }", HttpMode.Live)
      for h <- List(keys, whole) do
        assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
    }

  test("secrets come from the test: a stand-in vault or the environment, never a real vault"):
    val viaVault = loaded("kit-ai", "api-key = \"keepassxc:Models/key\"", secrets = Map("keepassxc:Models/key" -> "k-1234"))
    assertEquals(viaVault.contributions.secretVaults, Nil, "the stand-in vault is not the plugin's")
    val viaEnv = loaded("kit-ai", "api-key = \"env:KIT_KEY\"", secrets = Map("env:KIT_KEY" -> "k-5678"))
    val ai = ScriptedAiHttp(Right(AiHttpResponse(200, "hello")))
    assertEquals(viaEnv.aiTurn("kit-ai", request, ai).turn, Some(Right(AiTurn("hello"))))
    assertEquals(ai.requests.map(_.headers("x-api-key")), List("***"))
    val missing = PluginHarness.load("kit-ai", "api-key = \"keepassxc:Models/key\"", env = Map.empty)
    assert(missing.left.exists(_.contains("no secret vault 'keepassxc'")), missing.toString)

  // ── fetch ──────────────────────────────────────────────────────────────────────────────────────

  tmp.test("fetch: found, saved, with its licence; the host's PDF check and bot-challenge handling apply") { dir =>
    val h = loaded("kit-fetcher", "base-url = \"https://books.example\"", HttpMode.Stub(Map(
      "https://books.example/10.1/ok.pdf"   -> StubAnswer.ok(Pdf, "application/pdf"),
      "https://books.example/10.1/tiny.pdf" -> StubAnswer.ok("%PDF-1.7".getBytes(UTF_8)),
      "https://books.example/10.1/cf.pdf"   -> StubAnswer(403, JustAMoment.getBytes(UTF_8)))))
    val found = h.fetch(WorkKey(doi = Some(Doi("10.1/ok"))), saveTo = Some(dir))
    found.outcome match
      case FetchOutcome.Found("kit-fetcher", file, Some(saved)) =>
        assertEquals(Files.readAllBytes(saved).toSeq, Pdf.toSeq)
        assertEquals(saved.getFileName.toString, "kit-fetcher-10.1_ok.pdf")
      case other => fail(s"expected a saved copy, got $other")
    assertEquals(found.licence, Some("CC-BY-4.0"))
    assertEquals(found.requests, List("GET https://books.example/10.1/ok.pdf"))
    assertEquals(h.fetch(WorkKey(doi = Some(Doi("10.1/tiny")))).outcome, FetchOutcome.Miss("kit-fetcher: not a PDF"))
    h.fetch(WorkKey(doi = Some(Doi("10.1/cf")))).outcome match
      case FetchOutcome.Refused(why) => assert(why.contains("bot-protection challenge"), why)
      case other                     => fail(s"expected a refusal, got $other")
    assert(h.fetch(WorkKey(doi = Some(Doi("10.1/none")))).outcome.toString.contains("HTTP 404"))
    assertEquals(h.fetch(WorkKey(title = Some("No DOI"))).answers, List(FetcherAnswer("kit-fetcher", false, "no DOI")))
  }

  test("live: the server's User-Agent goes out"):
    withSite { (base, seen) =>
      val h = loaded("kit-fetcher", s"base-url = \"$base\"", HttpMode.Live, env = Map("HISTMERSION_ACQUIRE_USER_AGENT" -> "kit-agent/1"))
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
      assertEquals(seen.asScala.toList, List("/10.1/ok.pdf" -> "kit-agent/1"))
    }

  tmp.test("record, then replay offline: the same answers, no secret and no cookie in the files") { dir =>
    val config = "token = \"env:KIT_TOKEN\""
    val env    = Map("KIT_TOKEN" -> "tok-secret-42")
    withSite { (base, seen) =>
      val h = loaded("kit-fetcher", config + s"\nbase-url = \"$base\"", HttpMode.Record(dir), env = env)
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/ok")))).file.isDefined)
      assert(h.fetch(WorkKey(doi = Some(Doi("10.1/challenge")))).outcome.isInstanceOf[FetchOutcome.Refused])
      assert(seen.asScala.exists(_._1.contains("key=tok-secret-42")), "the live request carries the real key")
      // the replay must not reach the site
      val replayed = loaded("kit-fetcher", config + s"\nbase-url = \"$base\"", HttpMode.Replay(dir), env = env)
      seen.clear()
      val ok = replayed.fetch(WorkKey(doi = Some(Doi("10.1/ok"))))
      assertEquals(ok.file.map(_.bytes.toSeq), Some(Pdf.toSeq))
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
      val env = Map("HISTMERSION_ACQUIRE_OPENALEX_URL" -> base, "HISTMERSION_ACQUIRE_CONTACT_EMAIL" -> "someone@example.org")
      val live = loaded("kit-fetcher", s"base-url = \"$base\"", HttpMode.Record(dir), env = env)
      val work = WorkKey.fromDoi("https://doi.org/10.1/OK", live)
      assertEquals(work, Right(WorkKey(doi = Some(Doi("10.1/ok")), title = Some("A Book"), authors = List("A. Author"), year = Some(Year(2020)))))
      assert(!live.requests.exists(_.contains("someone")), "no contact e-mail from a test")
      val replay = loaded("kit-fetcher", s"base-url = \"$base\"", HttpMode.Replay(dir), env = env)
      assertEquals(WorkKey.fromDoi("10.1/ok", replay), work)
    }
  }

  // ── the other kinds ────────────────────────────────────────────────────────────────────────────

  test("media: each record judged as the server's import judges it"):
    val h = loaded("kit-media", "", HttpMode.Stub(Map("https://pictures.example/search?q=Troy" ->
      StubAnswer.text("a|CC-BY-4.0|https://pictures.example/a.jpg\nb|CC BY-NC 4.0|https://pictures.example/b.jpg\nc|CC0-1.0|\nnonsense"))))
    val r = h.searchMedia("kit-media", MediaPlace("pleiades_550595", "Troy"))
    assertEquals(r.kept.map(v => v.key -> v.licence), List("a" -> Some("CC-BY-4.0")))
    assertEquals(r.verdicts.map(v => v.key -> v.refused.isDefined), List("a" -> false, "b" -> true, "c" -> true,
      "unreadable record 'nonsense'" -> true))
    assertEquals(r.verdicts(2).refused, Some("no image"))
    assertEquals(h.searchMedia("other", MediaPlace("x", "y")).failure, Some("the plugin registered no media collection 'other'"))

  test("events: every part read page by page, each record checked and dated by the server"):
    val h = loaded("kit-events", "", HttpMode.Stub(Map(
      "https://events.example/p1" -> StubAnswer.text("thera|Eruption of Thera|-1620|-1600\nbad|Backwards|-1500|-1600"),
      "https://events.example/p2" -> StubAnswer.text("vesuvius|Vesuvius|79|79"))))
    val r = h.readCatalogue("kit-events", CatalogueQuery(None, Year(2025)))
    assertEquals(r.pages, List("p1" -> Right(2), "p2" -> Right(1)))
    assertEquals(r.accepted.map(e => (e.event.id, e.kind, e.notBefore, e.notAfter)),
      List(("thera", "eruption", Some(Year(-1620)), Some(Year(-1600))), ("vesuvius", "eruption", Some(Year(79)), Some(Year(79)))))
    assertEquals(r.refused, List("p1: bad: its dating runs backwards"))

  private val request = AiTurnRequest("m", "system", List(AiMessage.User("hi")), Nil, None, 100, 1, 1)

  test("AI: the provider's check first, then one call through the scripted transport"):
    val none = loaded("kit-ai")
    assertEquals(none.aiTurn("kit-ai", request), AiReport(Left("no api-key configured"), None, Nil))
    val keyed = loaded("kit-ai", "api-key = \"value:k-0000\"")
    assert(keyed.warnings.exists(_.contains("value:***")), keyed.warnings.toString)
    val down = keyed.aiTurn("kit-ai", request, ScriptedAiHttp())
    assertEquals(down.turn, Some(Left(AiFailure.Error("no scripted answer"))))

  test("a secret vault resolves as the server asks it"):
    val h = loaded("kit-vault", "entries = [\"portal=s3cret\"]")
    assertEquals(h.resolveSecret("kit-vault", "portal"), Right("s3cret"))
    assert(h.resolveSecret("kit-vault", "other").left.exists(_.contains("no entry 'other'")))
