package com.histmersion.core

import java.time.Instant

/** [[HttpPolicy]] (#1946): one identity and one slow-down rule for every outbound request. */
class HttpPolicySuite extends munit.FunSuite:
  import HttpPolicy.Step

  private val now = Instant.parse("2026-10-10T12:00:00Z")

  test("the User-Agent never carries an e-mail, whatever the setting says"):
    assertEquals(HttpPolicy.userAgent("histmersion/0.1 (mailto:someone@example.org)"), "histmersion/0.1")
    assertEquals(HttpPolicy.userAgent("Histmersion someone@example.org"), "Histmersion")
    assertEquals(HttpPolicy.userAgent("Histmersion (+https://github.com/x/histmersion)"),
      "Histmersion (+https://github.com/x/histmersion)")
    assertEquals(HttpPolicy.userAgent("  "), "Histmersion")

  test("a slow-down waits for Retry-After (seconds or a date), else 1, 2, 4 s"):
    assertEquals(HttpPolicy.decide(429, Some("7"), 0, 5, 60, now), Step.Wait(7000, "HTTP 429"))
    assertEquals(HttpPolicy.decide(503, Some("Sat, 10 Oct 2026 12:00:03 GMT"), 0, 5, 60, now), Step.Wait(3000, "HTTP 503"))
    assertEquals(HttpPolicy.decide(503, None, 2, 5, 60, now), Step.Wait(4000, "HTTP 503"))
    assertEquals(HttpPolicy.decide(200, None, 0, 5, 60, now, Some("maxlag")), Step.Wait(1000, "maxlag"))

  test("it gives up after the retries, on a wait longer than the cap, and on any other error"):
    assertEquals(HttpPolicy.decide(429, Some("1"), 3, 3, 60, now), Step.GiveUp("HTTP 429 after 3 retries"))
    assertEquals(HttpPolicy.decide(429, Some("3600"), 0, 5, 60, now), Step.GiveUp("HTTP 429: asked to wait 3600 s"))
    assertEquals(HttpPolicy.decide(404, None, 0, 5, 60, now), Step.GiveUp("HTTP 404"))
    assertEquals(HttpPolicy.decide(500, None, 0, 5, 60, now), Step.GiveUp("HTTP 500"))
    assertEquals(HttpPolicy.decide(200, None, 0, 5, 60, now), Step.Accept)

  test("Retry-After in the past is no wait; garbage is none"):
    assertEquals(HttpPolicy.retryAfterMs(Some("Sat, 10 Oct 2026 11:00:00 GMT"), now), Some(0L))
    assertEquals(HttpPolicy.retryAfterMs(Some("soon"), now), None)
