package com.histmersion.server

import com.histmersion.core.AppConfig
import com.histmersion.server.acquire.*
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The acquisition HTTP client against a FAKE upstream on a loopback port (#1523): a bot-protection
  * challenge is "no copy" at once — never retried, never worked around — and a caller's own
  * User-Agent (the PMC hosts get the project's public one) replaces the polite-pool identity. */
class HttpChallengeSuite extends munit.FunSuite:

  private val Challenge =
    """<!DOCTYPE html><html lang="en-US"><head><title>Just a moment...</title></head><body></body></html>"""

  /** One request log per test; the server answers by path. */
  private def withServer(body: (String, ConcurrentLinkedQueue[(String, String)]) => Unit): Unit =
    val seen   = ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    def reply(ex: HttpExchange, code: Int, text: String, headers: (String, String)*): Unit =
      headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
      val b = text.getBytes("UTF-8")
      ex.sendResponseHeaders(code, b.length.toLong)
      ex.getResponseBody.write(b)
      ex.close()
    server.createContext("/", (ex: HttpExchange) =>
      seen.add(ex.getRequestURI.getPath -> Option(ex.getRequestHeaders.getFirst("User-Agent")).getOrElse(""))
      ex.getRequestURI.getPath match
        case "/challenge-403" => reply(ex, 403, Challenge)
        case "/challenge-503" => reply(ex, 503, "<html>wait</html>", "cf-mitigated" -> "challenge")
        case "/busy"          => reply(ex, 503, "busy")
        case _                => reply(ex, 200, "ok"))
    server.start()
    try body(s"http://127.0.0.1:${server.getAddress.getPort}", seen)
    finally server.stop(0)

  private val cfg = AppConfig.load().acquire.copy(contactEmail = "someone@example.org")

  test("a 403 challenge page is no copy, asked once"):
    withServer { (base, seen) =>
      val r = SttpHttp(cfg, tries = 3).bytes(s"$base/challenge-403")
      assert(r.left.exists(_.contains("bot-protection challenge")), r.toString)
      assertEquals(seen.size, 1)
    }

  test("a 503 marked cf-mitigated: challenge is not retried either"):
    withServer { (base, seen) =>
      val r = SttpHttp(cfg, tries = 3).bytes(s"$base/challenge-503")
      assert(r.left.exists(_.contains("bot-protection challenge")), r.toString)
      assertEquals(seen.size, 1)
    }

  test("an ordinary 503 is still retried"):
    withServer { (base, seen) =>
      assert(SttpHttp(cfg, tries = 2).bytes(s"$base/busy").isLeft)
      assertEquals(seen.size, 2)
    }

  test("the polite-pool User-Agent by default; a caller's own replaces it"):
    withServer { (base, seen) =>
      val http = SttpHttp(cfg)
      assertEquals(http.text(s"$base/default"), Right("ok"))
      assertEquals(http.text(s"$base/public", Map("user-agent" -> cfg.publicUserAgent)), Right("ok"))
      val ua = seen.asScala.toMap
      assertEquals(ua.get("/default"), Some(cfg.politeUserAgent))
      assertEquals(ua.get("/public"), Some(cfg.publicUserAgent))
      assert(!cfg.publicUserAgent.contains("someone"), cfg.publicUserAgent)
    }

  test("challenge detection: the interstitials, not ordinary refusals or pages"):
    val page = Challenge.getBytes("UTF-8")
    assert(Http.isChallenge(403, Map.empty, page))
    assert(Http.isChallenge(503, Map.empty, page))
    assert(Http.isChallenge(200, Map("CF-Mitigated" -> "challenge"), Array.emptyByteArray))
    assert(!Http.isChallenge(200, Map.empty, page)) // a 200 is judged by looksLikePdf, not here
    assert(!Http.isChallenge(403, Map.empty, "<html><title>Forbidden</title></html>".getBytes("UTF-8")))
    assert(!Http.isChallenge(503, Map.empty, "busy".getBytes("UTF-8")))

  test("every acquisition request is pinned to HTTP/1.1, keeping its URI and headers"):
    val r = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://example.org/x"))
      .header("User-Agent", "ua").GET().build()
    val out = SttpHttp.http11(r)
    assertEquals(out.version(), java.util.Optional.of(java.net.http.HttpClient.Version.HTTP_1_1))
    assertEquals(out.uri(), r.uri())
    assertEquals(out.headers().firstValue("User-Agent"), java.util.Optional.of("ua"))
