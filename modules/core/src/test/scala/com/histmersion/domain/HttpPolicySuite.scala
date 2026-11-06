package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.time.Instant

/** [[HttpPolicy]] (#1946): one identity and one slow-down rule for every outbound request. */
class HttpPolicySuite extends munit.FunSuite:
  import HttpPolicy.Step

  private val now = Instant.parse("2026-10-10T12:00:00Z")

  test("the User-Agent never carries an e-mail, whatever the setting says"):
    assertEquals(HttpPolicy.userAgent(com.histmersion.domain.HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)")), "histmersion/0.1")
    assertEquals(HttpPolicy.userAgent(com.histmersion.domain.HttpPolicy.UserAgent("Histmersion someone@example.org")), "Histmersion")
    assertEquals(
      HttpPolicy.userAgent(com.histmersion.domain.HttpPolicy.UserAgent("Histmersion (+https://github.com/x/histmersion)")),
      "Histmersion (+https://github.com/x/histmersion)"
    )
    assertEquals(HttpPolicy.userAgent(com.histmersion.domain.HttpPolicy.UserAgent("  ")), "Histmersion")

  test("a slow-down waits for Retry-After (seconds or a date), else 1, 2, 4 s"):
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(429), Some(com.histmersion.domain.HttpPolicy.RetryAfter("7")), com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.Wait(com.histmersion.domain.HttpPolicy.Step.Wait.Ms(7000), com.histmersion.domain.HttpPolicy.Step.Wait.Why("HTTP 429")))
    assertEquals(
      HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(503), Some(com.histmersion.domain.HttpPolicy.RetryAfter("Sat, 10 Oct 2026 12:00:03 GMT")), com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now),
      Step.Wait(com.histmersion.domain.HttpPolicy.Step.Wait.Ms(3000), com.histmersion.domain.HttpPolicy.Step.Wait.Why("HTTP 503"))
    )
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(503), None, com.histmersion.domain.HttpPolicy.Attempt(2), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.Wait(com.histmersion.domain.HttpPolicy.Step.Wait.Ms(4000), com.histmersion.domain.HttpPolicy.Step.Wait.Why("HTTP 503")))
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(200), None, com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now, Some(com.histmersion.domain.HttpPolicy.SlowDown("maxlag"))), Step.Wait(com.histmersion.domain.HttpPolicy.Step.Wait.Ms(1000), com.histmersion.domain.HttpPolicy.Step.Wait.Why("maxlag")))

  test("it gives up after the retries, on a wait longer than the cap, and on any other error"):
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(429), Some(com.histmersion.domain.HttpPolicy.RetryAfter("1")), com.histmersion.domain.HttpPolicy.Attempt(3), com.histmersion.domain.HttpPolicy.MaxRetries(3), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.GiveUp(com.histmersion.domain.HttpPolicy.Step.GiveUp.Why("HTTP 429 after 3 retries")))
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(429), Some(com.histmersion.domain.HttpPolicy.RetryAfter("3600")), com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.GiveUp(com.histmersion.domain.HttpPolicy.Step.GiveUp.Why("HTTP 429: asked to wait 3600 s")))
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(404), None, com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.GiveUp(com.histmersion.domain.HttpPolicy.Step.GiveUp.Why("HTTP 404")))
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(500), None, com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.GiveUp(com.histmersion.domain.HttpPolicy.Step.GiveUp.Why("HTTP 500")))
    assertEquals(HttpPolicy.decide(com.histmersion.domain.HttpPolicy.StatusCode(200), None, com.histmersion.domain.HttpPolicy.Attempt(0), com.histmersion.domain.HttpPolicy.MaxRetries(5), com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds(60), now), Step.Accept)

  test("Retry-After in the past is no wait; garbage is none"):
    assertEquals(HttpPolicy.retryAfterMs(Some(com.histmersion.domain.HttpPolicy.RetryAfter("Sat, 10 Oct 2026 11:00:00 GMT")), now), Some(0L))
    assertEquals(HttpPolicy.retryAfterMs(Some(com.histmersion.domain.HttpPolicy.RetryAfter("soon")), now), None)

  test("trust: the JDK's roots always; the system's and a file's only when configured (#1568)"):
    import HttpPolicy.Trust
    assertEquals(HttpPolicy.trust(com.histmersion.domain.HttpPolicy.SystemTrust(false), None, com.histmersion.domain.HttpPolicy.OsName("Mac OS X")), List(Trust.Jdk))
    assertEquals(HttpPolicy.trust(com.histmersion.domain.HttpPolicy.SystemTrust(true), None, com.histmersion.domain.HttpPolicy.OsName("Mac OS X")), List(Trust.Jdk, Trust.Keychains(HttpPolicy.MacKeychains)))
    assertEquals(
      HttpPolicy.trust(com.histmersion.domain.HttpPolicy.SystemTrust(true), Some(com.histmersion.domain.HttpPolicy.Trust.PemFile.Path("/x.pem")), com.histmersion.domain.HttpPolicy.OsName("Linux")),
      List(Trust.Jdk, Trust.Bundle(HttpPolicy.LinuxBundles), Trust.PemFile(com.histmersion.domain.HttpPolicy.Trust.PemFile.Path("/x.pem")))
    )
    assertEquals(HttpPolicy.trust(com.histmersion.domain.HttpPolicy.SystemTrust(false), Some(com.histmersion.domain.HttpPolicy.Trust.PemFile.Path("/x.pem")), com.histmersion.domain.HttpPolicy.OsName("Linux")), List(Trust.Jdk, Trust.PemFile(com.histmersion.domain.HttpPolicy.Trust.PemFile.Path("/x.pem"))))

  test("pemCertificates: every CERTIFICATE block, decoded; anything else skipped"):
    val body = java.util.Base64.getMimeEncoder.encodeToString("not really DER".getBytes)
    val text = s"junk\n-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n" +
      "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n" +
      s"-----BEGIN TRUSTED CERTIFICATE-----\n$body\n-----END TRUSTED CERTIFICATE-----"
    assertEquals(HttpPolicy.pemCertificates(text).map(new String(_)), List("not really DER", "not really DER"))
    assertEquals(HttpPolicy.pemCertificates(com.histmersion.domain.HttpPolicy.PemText("")), Nil)
    assertEquals(HttpPolicy.pemCertificates(null), Nil)
