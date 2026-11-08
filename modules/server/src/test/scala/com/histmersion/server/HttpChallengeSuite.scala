package com.histmersion
package server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.domain.library.acquisition.agent
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{Challenge, SttpTransport}
import com.histmersion.domain.library.acquisition.*
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*
import com.histmersion.config.AcquireConfig
import com.histmersion.pluginhost.PolicyHttp
import com.histmersion.domain.HttpPolicy

/** The acquisition HTTP client against a FAKE upstream on a loopback port (#1523): a bot-protection challenge is "no
  * copy" at once — never retried, never worked around — and a caller's own User-Agent (the PMC hosts get the project's
  * public one) replaces the polite-pool identity.
  */
class HttpChallengeSuite extends munit.FunSuite:

  private val JustAMoment =
    """<!DOCTYPE html><html lang="en-US"><head><title>Just a moment...</title></head><body></body></html>"""

  /** One request log per test; the server answers by path. */
  private def withServer(body: (String, ConcurrentLinkedQueue[(String, String)]) => Unit): Unit =
    val seen = ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    def reply(ex: HttpExchange, code: Int, text: String, headers: (String, String)*): Unit =
      headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
      val b = text.getBytes("UTF-8")
      ex.sendResponseHeaders(code, b.length.toLong)
      ex.getResponseBody.write(b)
      ex.close()
    server.createContext(
      "/",
      (ex: HttpExchange) =>
        seen.add(ex.getRequestURI.getPath -> Option(ex.getRequestHeaders.getFirst("User-Agent")).getOrElse(""))
        ex.getRequestURI.getPath match
          case "/challenge-403" => reply(ex, 403, JustAMoment)
          case "/challenge-503" => reply(ex, 503, "<html>wait</html>", "cf-mitigated" -> "challenge")
          case "/busy"          => reply(ex, 503, "busy")
          case "/anubis-200"    => reply(ex, 200, Anubis)
          case "/anubis-403"    => reply(ex, 403, Anubis)
          case _                => reply(ex, 200, "ok")
    )
    server.start()
    try body(s"http://127.0.0.1:${server.getAddress.getPort}", seen)
    finally server.stop(0)

  private val cfg = AppConfig.load().acquire.copy(contactEmail = AcquireConfig.ContactEmail("someone@example.org"))

  /** Anubis's proof-of-work page (#2560), synthetic: the markers its open-source template emits. */
  private val Anubis =
    String(getClass.getResourceAsStream("/acquire/landing/challenge_anubis_synthetic.html").readAllBytes(), "UTF-8")

  test("an Anubis page is no copy, asked once, whether it answers 200 or 403 (#2560)"):
    withServer { (base, seen) =>
      for path <- List("/anubis-200", "/anubis-403") do
        val r =
          SttpHttp(cfg, tries = PolicyHttp.Tries(3), clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
            .bytes(Http.Url(s"$base$path"))
        assert(r.left.exists(_.value.contains("bot-protection challenge")), s"$path: $r")
      assertEquals(seen.size, 2)
    }

  test("a 403 challenge page is no copy, asked once"):
    withServer { (base, seen) =>
      val r =
        SttpHttp(cfg, tries = PolicyHttp.Tries(3), clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
          .bytes(Http.Url(s"$base/challenge-403"))
      assert(r.left.exists(_.value.contains("bot-protection challenge")), r.toString)
      assertEquals(seen.size, 1)
    }

  test("a 503 marked cf-mitigated: challenge is not retried either"):
    withServer { (base, seen) =>
      val r =
        SttpHttp(cfg, tries = PolicyHttp.Tries(3), clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
          .bytes(Http.Url(s"$base/challenge-503"))
      assert(r.left.exists(_.value.contains("bot-protection challenge")), r.toString)
      assertEquals(seen.size, 1)
    }

  test("an ordinary 503 is still retried"):
    withServer { (base, seen) =>
      assert(
        SttpHttp(cfg, tries = PolicyHttp.Tries(2), clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
          .bytes(Http.Url(s"$base/busy"))
          .isLeft
      )
      assertEquals(seen.size, 2)
    }

  test("the product's User-Agent by default, without the contact; a caller's own replaces it"):
    withServer { (base, seen) =>
      val http = SttpHttp(cfg, clock = () => java.time.Instant.now(java.time.Clock.systemUTC()))
      assertEquals(http.text(Http.Url(s"$base/default")), Right("ok"))
      assertEquals(
        http.text(
          Http.Url(s"$base/public"),
          Map(CoreQuota.HeaderName("user-agent") -> CoreQuota.HeaderValue(cfg.publicUserAgent.value))
        ),
        Right("ok")
      )
      val ua = seen.asScala.toMap
      assertEquals(ua.get("/default"), Some(cfg.agent))
      assert(!ua("/default").contains("someone"), "the contact never rides in a header (#1946)")
      assertEquals(ua.get("/public"), Some(cfg.publicUserAgent))
      assert(!cfg.publicUserAgent.value.contains("someone"), cfg.publicUserAgent)
    }

  test("challenge detection: the interstitials, not ordinary refusals or pages"):
    val page = Challenge.Body(JustAMoment.getBytes("UTF-8"))
    assert(Challenge.isChallenge(HttpPolicy.StatusCode(403), Map.empty, page))
    assert(Challenge.isChallenge(HttpPolicy.StatusCode(503), Map.empty, page))
    assert(
      Challenge.isChallenge(
        HttpPolicy.StatusCode(200),
        Map(CoreQuota.HeaderName("CF-Mitigated") -> CoreQuota.HeaderValue("challenge")),
        Challenge.Body(Array.emptyByteArray)
      )
    )
    assert(
      !Challenge.isChallenge(HttpPolicy.StatusCode(200), Map.empty, page)
    ) // a 200 is judged by looksLikePdf, not here
    assert(
      !Challenge.isChallenge(
        HttpPolicy.StatusCode(403),
        Map.empty,
        Challenge.Body("<html><title>Forbidden</title></html>".getBytes("UTF-8"))
      )
    )
    assert(!Challenge.isChallenge(HttpPolicy.StatusCode(503), Map.empty, Challenge.Body("busy".getBytes("UTF-8"))))
    // Anubis: its challenge at any status; its markers inside a PDF, or a page merely naming it, are not one
    val anubis = Challenge.Body(Anubis.getBytes("UTF-8"))
    assert(Challenge.isChallenge(HttpPolicy.StatusCode(200), Map.empty, anubis))
    assert(Challenge.isChallenge(HttpPolicy.StatusCode(401), Map.empty, anubis))
    assert(Challenge.isChallenge(HttpPolicy.StatusCode(403), Map.empty, anubis))
    assert(
      !Challenge.isChallenge(
        HttpPolicy.StatusCode(200),
        Map.empty,
        Challenge.Body(("%PDF-1.7\n" + Anubis).getBytes("ISO-8859-1"))
      )
    )
    assert(
      !Challenge.isChallenge(
        HttpPolicy.StatusCode(200),
        Map.empty,
        Challenge.Body("<html><p>Anubis, the jackal-headed god</p></html>".getBytes("UTF-8"))
      )
    )

  test("every acquisition request is pinned to HTTP/1.1, keeping its URI and headers"):
    val r = java.net.http.HttpRequest
      .newBuilder(java.net.URI.create("https://example.org/x"))
      .header("User-Agent", "ua")
      .GET()
      .build()
    val out = SttpTransport.http11(r)
    assertEquals(out.version(), java.util.Optional.of(java.net.http.HttpClient.Version.HTTP_1_1))
    assertEquals(out.uri(), r.uri())
    assertEquals(out.headers().firstValue("User-Agent"), java.util.Optional.of("ua"))
