package com.histmersion
package server.media
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.MediaConfig
import com.histmersion.plugin.Plugin
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Polite, Politeness}

import java.time.Instant
import com.histmersion.domain.HttpPolicy
import com.histmersion.config.AppConfig

/** The polite client (#1446): its User-Agent, its pace, and what it does with 429/503 and MediaWiki's maxlag — all
  * against a fake provider and a fake clock.
  */
class PolitenessSuite extends munit.FunSuite:

  import Politeness.Step

  private val now = Instant.parse("2026-10-10T12:00:00Z")
  private def answer(code: Int, headers: Map[String, String] = Map.empty, body: String = "{}") =
    HttpAnswer(
      HttpAnswer.Code(code),
      headers.map((k, v) => CoreQuota.HeaderName(k) -> CoreQuota.HeaderValue(v)),
      HttpAnswer.Body(body.getBytes("UTF-8"))
    )

  test("a 200 is taken; a 404 gives up at once"):
    assertEquals(
      Politeness
        .decide(answer(200), HttpPolicy.Attempt(0), HttpPolicy.MaxRetries(5), HttpPolicy.MaxRetryAfterSeconds(60), now),
      Step.Accept
    )
    assertEquals(
      Politeness
        .decide(answer(404), HttpPolicy.Attempt(0), HttpPolicy.MaxRetries(5), HttpPolicy.MaxRetryAfterSeconds(60), now),
      Step.GiveUp(Problem.Unclassified(Legacy.Text("HTTP 404")))
    )

  test("429 and 503 wait for Retry-After (seconds or a date), else back off 1, 2, 4 … s"):
    assertEquals(
      Politeness.decide(
        answer(429, Map("retry-after" -> "7")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Wait(HttpPolicy.Step.Wait.Ms(7000), Problem.Unclassified(Legacy.Text("HTTP 429")))
    )
    assertEquals(
      Politeness.decide(
        answer(503, Map("retry-after" -> "Sat, 10 Oct 2026 12:00:03 GMT")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Wait(HttpPolicy.Step.Wait.Ms(3000), Problem.Unclassified(Legacy.Text("HTTP 503")))
    )
    assertEquals(
      Politeness
        .decide(answer(503), HttpPolicy.Attempt(2), HttpPolicy.MaxRetries(5), HttpPolicy.MaxRetryAfterSeconds(60), now),
      Step.Wait(HttpPolicy.Step.Wait.Ms(4000), Problem.Unclassified(Legacy.Text("HTTP 503")))
    )

  test("maxlag: the MediaWiki-API-Error header (HTTP 200) or the error code in the body"):
    val lagged = answer(
      200,
      Map("mediawiki-api-error" -> "maxlag", "retry-after" -> "5"),
      """{"error":{"code":"maxlag","info":"Waiting for wdqs1013: 11 seconds lagged."}}"""
    )
    assert(Politeness.isMaxlag(lagged))
    assertEquals(
      Politeness
        .decide(lagged, HttpPolicy.Attempt(0), HttpPolicy.MaxRetries(5), HttpPolicy.MaxRetryAfterSeconds(60), now),
      Step.Wait(HttpPolicy.Step.Wait.Ms(5000), Problem.Unclassified(Legacy.Text("maxlag")))
    )
    assert(Politeness.isMaxlag(answer(200, body = """{"error":{"code":"maxlag"}}""")))
    assert(!Politeness.isMaxlag(answer(200, body = """{"entities":{}}""")))

  test("gives up after max-retries, and when asked to wait longer than max-retry-after-seconds"):
    assertEquals(
      Politeness.decide(
        answer(429, Map("retry-after" -> "1")),
        HttpPolicy.Attempt(5),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(Problem.Unclassified(Legacy.Text("HTTP 429 after 5 retries")))
    )
    assertEquals(
      Politeness.decide(
        answer(429, Map("retry-after" -> "3600")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(Problem.Unclassified(Legacy.Text("HTTP 429: asked to wait 3600 s")))
    )

  test("Retry-After parsing: seconds, a date in the past is now, junk is none"):
    assertEquals(Politeness.retryAfterMs(Some(HttpPolicy.RetryAfter(" 12 ")), now), Some(12000L))
    assertEquals(Politeness.retryAfterMs(Some(HttpPolicy.RetryAfter("Sat, 10 Oct 2026 11:00:00 GMT")), now), Some(0L))
    assertEquals(Politeness.retryAfterMs(Some(HttpPolicy.RetryAfter("soon")), now), None)
    assertEquals(Politeness.retryAfterMs(None, now), None)

  test("every request carries the project's User-Agent and nothing personal"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}"))
    val (p, _) = FakeMediaHttp.polite(http)
    p.json(HttpTransport.Url("https://www.wikidata.org/w/api.php?x=1"))
    val (_, headers) = http.requests.head
    assertEquals(headers, Map("User-Agent" -> "Histmersion (+https://github.com/histmersion/histmersion)"))

  test("requests to one host are spaced by request-interval-ms; another host is not held up"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}"))
    val (p, slept) = FakeMediaHttp.polite(
      http,
      FakeMediaHttp.cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(500))
    )
    p.get(HttpTransport.Url("https://a.example/1")); p.get(HttpTransport.Url("https://a.example/2"));
    p.get(HttpTransport.Url("https://b.example/1"))
    assertEquals(slept.toList, List(500L))
    assertEquals(p.requests, 3)

  test("a maxlag answer is waited out and asked again; the second answer is used"):
    // `var`: the fake's state — maxlag first, then the answer
    var calls = 0
    val http = FakeMediaHttp { _ =>
      calls += 1
      if calls == 1 then
        FakeMediaHttp.json(
          """{"error":{"code":"maxlag"}}""",
          Map("mediawiki-api-error" -> "maxlag", "retry-after" -> "5")
        )
      else FakeMediaHttp.json("""{"entities":{}}""")
    }
    val (p, slept) = FakeMediaHttp.polite(http)
    assertEquals(
      p.json(HttpTransport.Url("https://www.wikidata.org/w/api.php")).map(_.noSpaces),
      Right("""{"entities":{}}""")
    )
    assertEquals(slept.toList, List(5000L))
    assertEquals(http.requests.size, 2)

  test("a provider that keeps saying 429 is given up on after max-retries: its last answer is the failure"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}", Map("retry-after" -> "1"), code = 429))
    val (p, slept) = FakeMediaHttp.polite(http, FakeMediaHttp.cfg.modify(_.maxRetries).setTo(MediaConfig.MaxRetries(2)))
    assertEquals(
      p.get(HttpTransport.Url("https://commons.wikimedia.org/w/api.php")).left.toOption.map(_.sentence),
      Some("commons.wikimedia.org answered HTTP 429 at https://commons.wikimedia.org/w/api.php — {}")
    )
    assertEquals(http.requests.size, 3)
    assertEquals(slept.toList, List(1000L, 1000L))

  test("a file's type is the answer's Content-Type, else the fallback"):
    val http = FakeMediaHttp {
      case u if u.endsWith(".png") =>
        Right(
          HttpAnswer(
            HttpAnswer.Code(200),
            Map(CoreQuota.HeaderName("content-type") -> CoreQuota.HeaderValue("image/png; charset=binary")),
            HttpAnswer.Body(Array[Byte](1))
          )
        )
      case _ => Right(HttpAnswer(HttpAnswer.Code(200), Map.empty, HttpAnswer.Body(Array[Byte](2))))
    }
    val (p, _) = FakeMediaHttp.polite(http)
    assertEquals(p.file(HttpTransport.Url("https://u/x.png"), Polite.Mime("image/jpeg")).map(_._2), Right("image/png"))
    assertEquals(p.file(HttpTransport.Url("https://u/x"), Polite.Mime("image/jpeg")).map(_._2), Right("image/jpeg"))

  // ── a refusal taken as rate limiting (#2561: the Met's firewall after ~2 000 requests) ──────────

  private val metHost = Polite.Host("collectionapi.metmuseum.org")
  private def metUrl(i: Int) = HttpTransport.Url(s"https://$metHost/public/collection/v1/objects/$i")

  test("a 403 after answers waits each backoff step and asks again; the host's pace then halves per refusal"):
    // `var`: the answers the fake host gives, in turn
    var answers = List(200, 403, 403, 200, 200)
    val http = FakeMediaHttp { _ =>
      val code = answers.head; answers = answers.tail
      FakeMediaHttp.json("{}", code = code)
    }
    val (p, slept) = FakeMediaHttp.polite(
      http,
      FakeMediaHttp.cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(250)),
      List(Polite.RefusalBackoffMs(60000L), Polite.RefusalBackoffMs(300000L))
    )
    assert(p.get(metUrl(1)).isRight)
    assert(p.get(metUrl(2)).isRight, "the third answer, after two waits")
    assert(p.get(metUrl(3)).isRight)
    // the pace before the second request, the two waits, then four times the pace: two refusals halved it twice
    assertEquals(slept.toList, List(250L, 60000L, 300000L, 1000L))
    assertEquals(p.intervalMs(metHost), 1000L)
    assertEquals(http.requests.size, 5)

  test(
    "when the backoff steps run out the 403 stands (how long it waited is logged); without steps it is final at once"
  ):
    val always = FakeMediaHttp(_ => FakeMediaHttp.json("{}", code = 403))
    val (p, slept) = FakeMediaHttp.polite(
      always,
      FakeMediaHttp.cfg,
      List(Polite.RefusalBackoffMs(60000L), Polite.RefusalBackoffMs(300000L))
    )
    assertEquals(
      p.get(metUrl(1)).left.toOption.flatMap(_.answered),
      Some(Problem.Upstream.Status.Forbidden)
    )
    assertEquals((always.requests.size, slept.toList), (3, List(60000L, 300000L)))
    val once = FakeMediaHttp(_ => FakeMediaHttp.json("{}", code = 403))
    val (q, none) = FakeMediaHttp.polite(once)
    assertEquals(
      q.get(metUrl(1)).left.toOption.flatMap(_.answered),
      Some(Problem.Upstream.Status.Forbidden)
    )
    assertEquals((once.requests.size, none.toList), (1, Nil))

  test("the slow-down stops at 16 times the pace; another host keeps its own"):
    val http = FakeMediaHttp(u => FakeMediaHttp.json("{}", code = if u.contains(metHost.value) then 403 else 200))
    val (p, _) = FakeMediaHttp.polite(
      http,
      FakeMediaHttp.cfg.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(100)),
      List.fill(6)(Polite.RefusalBackoffMs(1L))
    )
    assert(p.get(metUrl(1)).isLeft)
    assertEquals(p.intervalMs(metHost), 1600L)
    assertEquals(p.intervalMs(Polite.Host("images.metmuseum.org")), 100L)

  test("the Met's backoff is configured; the other collections keep a 403 final"):
    val m = AppConfig.load().media
    assertEquals(m.met.refusalBackoffMs, List(60000L, 300000L))
    assertEquals(m.met.requestIntervalMs, 250, "4 requests a second, under the documented 80")
    assertEquals((m.cleveland.refusalBackoffMs, m.aic.refusalBackoffMs, m.loc.refusalBackoffMs), (Nil, Nil, Nil))
