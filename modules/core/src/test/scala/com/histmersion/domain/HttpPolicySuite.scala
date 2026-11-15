package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.time.Instant

/** [[HttpPolicy]] (#1946): one identity and one slow-down rule for every outbound request. */
class HttpPolicySuite extends munit.FunSuite:
  import HttpPolicy.Step

  private val now = Instant.parse("2026-10-10T12:00:00Z")
  private var noPem: HttpPolicy.PemText = scala.compiletime.uninitialized

  test("the User-Agent never carries an e-mail, whatever the setting says"):
    assertEquals(
      HttpPolicy.userAgent(HttpPolicy.UserAgent("histmersion/0.1 (mailto:someone@example.org)")),
      "histmersion/0.1"
    )
    assertEquals(HttpPolicy.userAgent(HttpPolicy.UserAgent("Histmersion someone@example.org")), "Histmersion")
    assertEquals(
      HttpPolicy.userAgent(HttpPolicy.UserAgent("Histmersion (+https://github.com/x/histmersion)")),
      "Histmersion (+https://github.com/x/histmersion)"
    )
    assertEquals(HttpPolicy.userAgent(HttpPolicy.UserAgent("  ")), "Histmersion")

  test("a slow-down waits for Retry-After (seconds or a date), else 1, 2, 4 s"):
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(429),
        Some(HttpPolicy.RetryAfter("7")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Wait(Step.Wait.Ms(7000), HttpPolicy.Rationale.SlowDownStatus(HttpPolicy.StatusCode(429)))
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(503),
        Some(HttpPolicy.RetryAfter("Sat, 10 Oct 2026 12:00:03 GMT")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Wait(Step.Wait.Ms(3000), HttpPolicy.Rationale.SlowDownStatus(HttpPolicy.StatusCode(503)))
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(503),
        None,
        HttpPolicy.Attempt(2),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Wait(Step.Wait.Ms(4000), HttpPolicy.Rationale.SlowDownStatus(HttpPolicy.StatusCode(503)))
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(200),
        None,
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now,
        Some(HttpPolicy.SlowDown("maxlag"))
      ),
      Step.Wait(Step.Wait.Ms(1000), HttpPolicy.Rationale.SlowedDown(HttpPolicy.SlowDown("maxlag")))
    )

  test("it gives up after the retries, on a wait longer than the cap, and on any other error"):
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(429),
        Some(HttpPolicy.RetryAfter("1")),
        HttpPolicy.Attempt(3),
        HttpPolicy.MaxRetries(3),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(
        HttpPolicy.Rationale
          .Retried(HttpPolicy.Rationale.SlowDownStatus(HttpPolicy.StatusCode(429)), HttpPolicy.MaxRetries(3))
      )
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(429),
        Some(HttpPolicy.RetryAfter("3600")),
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(
        HttpPolicy.Rationale
          .WaitTooLong(HttpPolicy.Rationale.SlowDownStatus(HttpPolicy.StatusCode(429)), Step.Wait.Ms(3600000L))
      )
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(404),
        None,
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(HttpPolicy.Rationale.Failed(HttpPolicy.StatusCode(404)))
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(500),
        None,
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.GiveUp(HttpPolicy.Rationale.Failed(HttpPolicy.StatusCode(500)))
    )
    assertEquals(
      HttpPolicy.decide(
        HttpPolicy.StatusCode(200),
        None,
        HttpPolicy.Attempt(0),
        HttpPolicy.MaxRetries(5),
        HttpPolicy.MaxRetryAfterSeconds(60),
        now
      ),
      Step.Accept
    )

  test("Retry-After in the past is no wait; garbage is none"):
    assertEquals(HttpPolicy.retryAfterMs(Some(HttpPolicy.RetryAfter("Sat, 10 Oct 2026 11:00:00 GMT")), now), Some(0L))
    assertEquals(HttpPolicy.retryAfterMs(Some(HttpPolicy.RetryAfter("soon")), now), None)

  test("trust: the JDK's roots always; the system's and a file's only when configured (#1568)"):
    import HttpPolicy.Trust
    assertEquals(HttpPolicy.trust(HttpPolicy.SystemTrust(false), None, HttpPolicy.OsName("Mac OS X")), List(Trust.Jdk))
    assertEquals(
      HttpPolicy.trust(HttpPolicy.SystemTrust(true), None, HttpPolicy.OsName("Mac OS X")),
      List(Trust.Jdk, Trust.Keychains(HttpPolicy.MacKeychains))
    )
    assertEquals(
      HttpPolicy.trust(HttpPolicy.SystemTrust(true), Some(Trust.PemFile.Path("/x.pem")), HttpPolicy.OsName("Linux")),
      List(Trust.Jdk, Trust.Bundle(HttpPolicy.LinuxBundles), Trust.PemFile(Trust.PemFile.Path("/x.pem")))
    )
    assertEquals(
      HttpPolicy.trust(HttpPolicy.SystemTrust(false), Some(Trust.PemFile.Path("/x.pem")), HttpPolicy.OsName("Linux")),
      List(Trust.Jdk, Trust.PemFile(Trust.PemFile.Path("/x.pem")))
    )

  test("pemCertificates: every CERTIFICATE block, decoded; anything else skipped"):
    val body = java.util.Base64.getMimeEncoder.encodeToString("not really DER".getBytes)
    val text = s"junk\n-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n" +
      "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n" +
      s"-----BEGIN TRUSTED CERTIFICATE-----\n$body\n-----END TRUSTED CERTIFICATE-----"
    assertEquals(
      HttpPolicy.pemCertificates(HttpPolicy.PemText(text)).map(c => new String(c.value)),
      List("not really DER", "not really DER")
    )
    assertEquals(HttpPolicy.pemCertificates(HttpPolicy.PemText("")), Nil)
    assertEquals(HttpPolicy.pemCertificates(noPem), Nil)

  test("the log tells why the policy waits or gives up, as before"):
    val r = HttpPolicy.Rationale
    assertEquals(r.SlowDownStatus(HttpPolicy.StatusCode(429)).said, "HTTP 429")
    assertEquals(r.SlowedDown(HttpPolicy.SlowDown("maxlag")).said, "maxlag")
    assertEquals(
      r.Retried(r.SlowDownStatus(HttpPolicy.StatusCode(503)), HttpPolicy.MaxRetries(3)).said,
      "HTTP 503 after 3 retries"
    )
    assertEquals(
      r.WaitTooLong(r.SlowedDown(HttpPolicy.SlowDown("maxlag")), Step.Wait.Ms(3600500L)).said,
      "maxlag: asked to wait 3600 s"
    )
    assertEquals(r.Failed(HttpPolicy.StatusCode(404)).said, "HTTP 404")
