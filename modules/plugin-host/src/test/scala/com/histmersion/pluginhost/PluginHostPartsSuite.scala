package com.histmersion.pluginhost

import com.histmersion.domain.MediaLicence
import com.histmersion.api.WorkRef
import com.histmersion.plugin.*

/** The parts of the host the server and the plugin testkit share: what a work is asked for, the media
  * judgement, the catalogue paging, the AI guards and the acquisition client's policy. */
class PluginHostPartsSuite extends munit.FunSuite:

  test("the chain is asked for a work by its identifiers and its metadata record"):
    val ref = WorkRef(doi = Some("10.1/x"), arxivId = Some("2101.00001"), title = Some("A title"), year = Some(1999),
      authors = List("A. Author"))
    assertEquals(WorkLookup.key(Some("10.1/x"), None, Some(ref)),
      WorkKey(doi = Some("10.1/x"), arxiv = Some("2101.00001"), title = Some("A title"), authors = List("A. Author"),
        year = Some(1999)))
    assertEquals(WorkLookup.key(None, Some("hep-th/9901001"), None), WorkKey(arxiv = Some("hep-th/9901001")))

  private def item(licence: String, image: Option[String] = Some("https://x.example/1.jpg")) =
    MediaItem("k1", "A picture", "https://x.example/1", image, Right(MediaRights(licence, credit = "A. Photographer")))

  test("a media record is kept only with a readable, allowed licence and an image"):
    val allow = MediaLicence.allowList("cc0,cc-by")
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC-BY-4.0")), allow, "mine"), None)
    assert(PluginMedia.refusal(PluginMedia.record(item("CC BY-NC 4.0")), allow, "mine").exists(_.contains("not allowed")))
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC0-1.0", image = None)), allow, "mine"), Some("no image"))
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC0-1.0").copy(withheld = Some("human remains"))), allow, "mine"),
      Some("human remains"))
    assertEquals(PluginMedia.refusal(PluginMedia.record(item("CC0-1.0").copy(rights = Right(MediaRights("CC0-1.0")))), allow,
      "mine"), Some("no credit line"))

  private final class Paged(pages: Map[String, Either[String, CataloguePage]]) extends EventCatalogue:
    val title = "t"; val licence = "l"; val citation = "c"; val home = "h"
    def parts(q: CatalogueQuery) = pages.keys.toList
    def read(part: String, q: CatalogueQuery, http: PluginHttp) = pages(part)

  private val offline = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = Left("offline")

  test("a part is read page by page until no next page, a repeat, a part done or a full run"):
    val cat = Paged(Map(
      "a" -> Right(CataloguePage(Nil, Some("a2"))),
      "a2" -> Right(CataloguePage(Nil, Some("a2"))),
      "b" -> Left("down"),
      "c" -> Right(CataloguePage(Nil, Some("done")))))
    def read(first: String, done: String => Boolean = _ => false, full: => Boolean = false) =
      val seen = List.newBuilder[(String, Boolean)]
      PluginEvents.readPart(cat, first, CatalogueQuery(None, 2025), offline, done, full)((p, got) => seen += p -> got.isRight)
      seen.result()
    assertEquals(read("a"), List("a" -> true, "a2" -> true))
    assertEquals(read("b"), List("b" -> false))
    assertEquals(read("c", done = _ == "done"), List("c" -> true))
    assertEquals(read("a", done = _ == "a"), Nil)
    assertEquals(read("a", full = true), Nil)

  private val request = AiTurnRequest("m", "s", List(AiMessage.User("hi")), Nil, None, 10, 1, 1)

  test("an AI provider's exception or null is its failure"):
    val throwing = new AiProvider:
      val title = "t"
      override def check(model: String) = throw IllegalStateException("no")
      def turn(r: AiTurnRequest, http: AiHttp) = throw IllegalStateException("boom")
    val nulls = new AiProvider:
      val title = "t"
      def turn(r: AiTurnRequest, http: AiHttp) = Right(null)
    assertEquals(PluginAi.check(throwing, "m"), Left("its check threw IllegalStateException"))
    assertEquals(PluginAi.turn(throwing, request, AiHttp.Offline), Left(AiFailure.Error("IllegalStateException: boom")))
    assertEquals(PluginAi.turn(nulls, request, AiHttp.Offline), Left(AiFailure.Error("the provider answered null")))

  /** Answers in turn, recording each request's headers. */
  private final class Scripted(answers: Either[String, HttpAnswer]*) extends HttpTransport:
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
    val busy  = Scripted(answer(503, "busy", "retry-after" -> "2"), answer(200, "ok"))
    val http  = PolicyHttp("histmersion/0.1 (mailto:someone@example.org)", busy, sleep = waits += _, log = quiet)
    assertEquals(http.bytes("https://x.example/a", Map("user-agent" -> "mine")).map(String(_, "UTF-8")), Right("ok"))
    assertEquals(waits.result(), List(2000L))
    assert(busy.sent.result().forall(_.get("User-Agent").contains("mine")))
    val own = Scripted(answer(200, "ok"))
    PolicyHttp("histmersion/0.1 (mailto:someone@example.org)", own, log = quiet).bytes("https://x.example/a")
    assertEquals(own.sent.result().map(_.get("User-Agent")), List(Some("histmersion/0.1")))
    val challenged = Scripted(answer(403, "<html><title>Just a moment...</title></html>"))
    val got = PolicyHttp("ua", challenged, sleep = _ => fail("a challenge is never retried"), log = quiet).bytes("https://x.example/b")
    assert(got.left.exists(_.contains("bot-protection challenge")), got.toString)
    val down = Scripted(Left("x unreachable"), Left("x unreachable"))
    assertEquals(PolicyHttp("ua", down, tries = 2, sleep = _ => (), log = quiet).bytes("https://x.example/c"), Left("x unreachable"))
    val missing = Scripted(answer(404, "no such work"))
    assertEquals(PolicyHttp("ua", missing, log = quiet).bytes("https://x.example/d"), Left("https://x.example/d: HTTP 404 no such work"))
