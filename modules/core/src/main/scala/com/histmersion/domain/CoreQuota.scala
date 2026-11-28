package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.UpstreamQuota

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.time.TimeEquality.dayEquality
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The budget Histmersion keeps for CORE's API (api.core.ac.uk), shared by its two uses: the `core` step of the
  * acquisition chain (a PDF lookup by DOI) and the `core` index of the new-source search (a goal's saved query). Pure:
  * the state goes in, the next state comes out; the server holds it.
  *
  * CORE meters a key in tokens — a personal key 1,000 a day and 25 a minute, a simple query 1 token, a scroll,
  * recommender or bulk call 3–5 — and says so on every answer: `X-RateLimit-Limit` and `X-RateLimit-Remaining` for the
  * current window, `X-RateLimit-Retry-After` (an ISO time, e.g. `2026-10-26T18:52:11+0000`) once the window is spent,
  * and HTTP 429 when a call came too soon. CORE may change the costs and the limits, so all of them are settings.
  *
  *   - **Blocked**: after a 429, or `Remaining` 0, nothing is sent until the time CORE gave (or, without one,
  *     `exhaustedPauseSeconds` later).
  *   - **Pacing**: at most `perMinute` calls in any 60 s; a call that would wait longer than `maxWaitSeconds` for its
  *     slot is not made.
  *   - **Daily budget**: at most `dailyTokens` a day (a UTC day: CORE does not say when its day starts), of which
  *     searches may use `searchDailyTokens` — the rest stays for PDF lookups, so a sweep of many saved queries cannot
  *     leave the ingests without CORE.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryAcquisition)
object CoreQuota:
  type Cap = Cap.T
  object Cap extends Newtype[Int, Pure]
  type Cost = Cost.T
  object Cost extends Newtype[Int, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends Newtype[String, Pure]
  type HeaderValue = HeaderValue.T
  object HeaderValue extends Newtype[String, Pure]
  type Spent = Spent.T
  object Spent extends Newtype[Int, Pure]
  type StatusCode = StatusCode.T
  object StatusCode extends Newtype[Int, Pure]

  /** The UTC day of `now`. */
  def day(now: Instant): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate

  private val CoreTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  /** A retry time as CORE gives it (`2026-10-26T18:52:11+0000`), an ISO-8601 instant or offset time, delay seconds, or
    * an HTTP date.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def retryAt(v: Option[HeaderValue], now: Instant): Option[Instant] =
    v.map(_.value.trim).filter(_.nonEmpty).flatMap { s =>
      Try(OffsetDateTime.parse(s, CoreTime).toInstant).toOption
        .orElse(Try(OffsetDateTime.parse(s).toInstant).toOption)
        .orElse(Try(Instant.parse(s)).toOption)
        .orElse(HttpPolicy.retryAfterMs(Some(HttpPolicy.RetryAfter(s)), now).map(ms => now.plusMillis(ms.value)))
    }
