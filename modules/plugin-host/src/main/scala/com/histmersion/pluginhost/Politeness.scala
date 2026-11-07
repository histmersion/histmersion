package com.histmersion.pluginhost

import com.histmersion.domain.HttpPolicy

import java.time.Instant
import scala.jdk.CollectionConverters.*

/** What to do with a media or catalogue answer: [[HttpPolicy]], the one outbound policy, plus MediaWiki's `maxlag`
  * refusal (a 200 that means "slow down"). Pure.
  */
object Politeness:


  type Step = HttpPolicy.Step
  val Step = HttpPolicy.Step

  /** Does the answer say "slow down"? A 429, a 503/502/504, or MediaWiki's maxlag refusal — which comes back as HTTP
    * 200 with a `MediaWiki-API-Error: maxlag` header and `error.code = maxlag`.
    */
  def isMaxlag(a: HttpAnswer): Boolean =
    a.header("mediawiki-api-error").exists(_.trim.equalsIgnoreCase("maxlag")) ||
      (a.body.length < 4096 && a.text.contains("\"code\":\"maxlag\""))

  /** Pure: the next step after the `attempt`-th answer (0-based) — [[HttpPolicy.decide]]. */
  def decide(a: HttpAnswer, attempt: com.histmersion.domain.HttpPolicy.Attempt, maxRetries: com.histmersion.domain.HttpPolicy.MaxRetries, maxRetryAfterSeconds: com.histmersion.domain.HttpPolicy.MaxRetryAfterSeconds, now: Instant): Step =
    HttpPolicy.decide(
      a.code,
      a.header("retry-after"),
      attempt,
      maxRetries,
      maxRetryAfterSeconds,
      now,
      Option.when(isMaxlag(a))("maxlag")
    )

  /** Pure: a Retry-After value as milliseconds from `now` — [[HttpPolicy.retryAfterMs]]. */
  def retryAfterMs(v: Option[com.histmersion.domain.HttpPolicy.RetryAfter], now: Instant): Option[com.histmersion.domain.HttpPolicy.Step.Wait.Ms] = HttpPolicy.retryAfterMs(v, now)
