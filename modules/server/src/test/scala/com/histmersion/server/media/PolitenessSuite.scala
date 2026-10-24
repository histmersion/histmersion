package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpAnswer, Politeness}

import java.time.Instant

/** The polite client (#1446): its User-Agent, its pace, and what it does with 429/503 and MediaWiki's
  * maxlag — all against a fake provider and a fake clock. */
class PolitenessSuite extends munit.FunSuite:

  import Politeness.Step

  private val now = Instant.parse("2026-10-10T12:00:00Z")
  private def answer(code: Int, headers: Map[String, String] = Map.empty, body: String = "{}") =
    HttpAnswer(code, headers, body.getBytes("UTF-8"))

  test("a 200 is taken; a 404 gives up at once"):
    assertEquals(Politeness.decide(answer(200), 0, 5, 60, now), Step.Accept)
    assertEquals(Politeness.decide(answer(404), 0, 5, 60, now), Step.GiveUp("HTTP 404"))

  test("429 and 503 wait for Retry-After (seconds or a date), else back off 1, 2, 4 … s"):
    assertEquals(Politeness.decide(answer(429, Map("retry-after" -> "7")), 0, 5, 60, now), Step.Wait(7000, "HTTP 429"))
    assertEquals(Politeness.decide(answer(503, Map("retry-after" -> "Sat, 10 Oct 2026 12:00:03 GMT")), 0, 5, 60, now),
      Step.Wait(3000, "HTTP 503"))
    assertEquals(Politeness.decide(answer(503), 2, 5, 60, now), Step.Wait(4000, "HTTP 503"))

  test("maxlag: the MediaWiki-API-Error header (HTTP 200) or the error code in the body"):
    val lagged = answer(200, Map("mediawiki-api-error" -> "maxlag", "retry-after" -> "5"),
      """{"error":{"code":"maxlag","info":"Waiting for wdqs1013: 11 seconds lagged."}}""")
    assert(Politeness.isMaxlag(lagged))
    assertEquals(Politeness.decide(lagged, 0, 5, 60, now), Step.Wait(5000, "maxlag"))
    assert(Politeness.isMaxlag(answer(200, body = """{"error":{"code":"maxlag"}}""")))
    assert(!Politeness.isMaxlag(answer(200, body = """{"entities":{}}""")))

  test("gives up after max-retries, and when asked to wait longer than max-retry-after-seconds"):
    assertEquals(Politeness.decide(answer(429, Map("retry-after" -> "1")), 5, 5, 60, now),
      Step.GiveUp("HTTP 429 after 5 retries"))
    assertEquals(Politeness.decide(answer(429, Map("retry-after" -> "3600")), 0, 5, 60, now),
      Step.GiveUp("HTTP 429: asked to wait 3600 s"))

  test("Retry-After parsing: seconds, a date in the past is now, junk is none"):
    assertEquals(Politeness.retryAfterMs(Some(" 12 "), now), Some(12000L))
    assertEquals(Politeness.retryAfterMs(Some("Sat, 10 Oct 2026 11:00:00 GMT"), now), Some(0L))
    assertEquals(Politeness.retryAfterMs(Some("soon"), now), None)
    assertEquals(Politeness.retryAfterMs(None, now), None)

  test("every request carries the project's User-Agent and nothing personal"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}"))
    val (p, _) = FakeMediaHttp.polite(http)
    p.json("https://www.wikidata.org/w/api.php?x=1")
    val (_, headers) = http.requests.head
    assertEquals(headers, Map("User-Agent" -> "Histmersion (+https://github.com/histmersion/histmersion)"))

  test("requests to one host are spaced by request-interval-ms; another host is not held up"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}"))
    val (p, slept) = FakeMediaHttp.polite(http, FakeMediaHttp.cfg.copy(requestIntervalMs = 500))
    p.get("https://a.example/1"); p.get("https://a.example/2"); p.get("https://b.example/1")
    assertEquals(slept.toList, List(500L))
    assertEquals(p.requests, 3)

  test("a maxlag answer is waited out and asked again; the second answer is used"):
    // `var`: the fake's state — maxlag first, then the answer
    var calls = 0
    val http = FakeMediaHttp { _ =>
      calls += 1
      if calls == 1 then FakeMediaHttp.json("""{"error":{"code":"maxlag"}}""", Map("mediawiki-api-error" -> "maxlag", "retry-after" -> "5"))
      else FakeMediaHttp.json("""{"entities":{}}""")
    }
    val (p, slept) = FakeMediaHttp.polite(http)
    assertEquals(p.json("https://www.wikidata.org/w/api.php").map(_.noSpaces), Right("""{"entities":{}}"""))
    assertEquals(slept.toList, List(5000L))
    assertEquals(http.requests.size, 2)

  test("a provider that keeps saying 429 is given up on after max-retries, with the reason"):
    val http = FakeMediaHttp(_ => FakeMediaHttp.json("{}", Map("retry-after" -> "1"), code = 429))
    val (p, slept) = FakeMediaHttp.polite(http, FakeMediaHttp.cfg.copy(maxRetries = 2))
    assertEquals(p.get("https://commons.wikimedia.org/w/api.php").left.toOption,
      Some("commons.wikimedia.org: HTTP 429 after 2 retries"))
    assertEquals(http.requests.size, 3)
    assertEquals(slept.toList, List(1000L, 1000L))

  test("a file's type is the answer's Content-Type, else the fallback"):
    val http = FakeMediaHttp {
      case u if u.endsWith(".png") => Right(HttpAnswer(200, Map("content-type" -> "image/png; charset=binary"), Array[Byte](1)))
      case _                       => Right(HttpAnswer(200, Map.empty, Array[Byte](2)))
    }
    val (p, _) = FakeMediaHttp.polite(http)
    assertEquals(p.file("https://u/x.png", "image/jpeg").map(_._2), Right("image/png"))
    assertEquals(p.file("https://u/x", "image/jpeg").map(_._2), Right("image/jpeg"))

  // ── a refusal taken as rate limiting (#2561: the Met's firewall after ~2 000 requests) ──────────

  private val metHost = "collectionapi.metmuseum.org"
  private def metUrl(i: Int) = s"https://$metHost/public/collection/v1/objects/$i"

  test("a 403 after answers waits each backoff step and asks again; the host's pace then halves per refusal"):
    // `var`: the answers the fake host gives, in turn
    var answers = List(200, 403, 403, 200, 200)
    val http = FakeMediaHttp { _ =>
      val code = answers.head; answers = answers.tail
      FakeMediaHttp.json("{}", code = code)
    }
    val (p, slept) = FakeMediaHttp.polite(http, FakeMediaHttp.cfg.copy(requestIntervalMs = 250), List(60000L, 300000L))
    assert(p.get(metUrl(1)).isRight)
    assert(p.get(metUrl(2)).isRight, "the third answer, after two waits")
    assert(p.get(metUrl(3)).isRight)
    // the pace before the second request, the two waits, then four times the pace: two refusals halved it twice
    assertEquals(slept.toList, List(250L, 60000L, 300000L, 1000L))
    assertEquals(p.intervalMs(metHost), 1000L)
    assertEquals(http.requests.size, 5)

  test("when the backoff steps run out the 403 stands, saying how long it waited; without steps it is final at once"):
    val always = FakeMediaHttp(_ => FakeMediaHttp.json("{}", code = 403))
    val (p, slept) = FakeMediaHttp.polite(always, FakeMediaHttp.cfg, List(60000L, 300000L))
    assertEquals(p.get(metUrl(1)).left.toOption, Some(s"$metHost: HTTP 403 (still, after waiting 360 s)"))
    assertEquals((always.requests.size, slept.toList), (3, List(60000L, 300000L)))
    val once = FakeMediaHttp(_ => FakeMediaHttp.json("{}", code = 403))
    val (q, none) = FakeMediaHttp.polite(once)
    assertEquals(q.get(metUrl(1)).left.toOption, Some(s"$metHost: HTTP 403"))
    assertEquals((once.requests.size, none.toList), (1, Nil))

  test("the slow-down stops at 16 times the pace; another host keeps its own"):
    val http = FakeMediaHttp(u => FakeMediaHttp.json("{}", code = if u.contains(metHost) then 403 else 200))
    val (p, _) = FakeMediaHttp.polite(http, FakeMediaHttp.cfg.copy(requestIntervalMs = 100), List.fill(6)(1L))
    assert(p.get(metUrl(1)).isLeft)
    assertEquals(p.intervalMs(metHost), 1600L)
    assertEquals(p.intervalMs("images.metmuseum.org"), 100L)

  test("the Met's backoff is configured; the other collections keep a 403 final"):
    val m = com.histmersion.core.AppConfig.load().media
    assertEquals(m.met.refusalBackoffMs, List(60000L, 300000L))
    assertEquals(m.met.requestIntervalMs, 250, "4 requests a second, under the documented 80")
    assertEquals((m.cleveland.refusalBackoffMs, m.aic.refusalBackoffMs, m.loc.refusalBackoffMs), (Nil, Nil, Nil))
