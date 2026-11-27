package com.histmersion
package pluginhost

import com.histmersion.domain.HttpPolicy

import java.time.Instant
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import com.histmersion.lint.{tolerated, LintRule}

/** What to do with a media or catalogue answer: [[HttpPolicy]], the one outbound policy, plus MediaWiki's `maxlag`
  * refusal (a 200 that means "slow down"). Pure.
  */
object Politeness:

  type Step = HttpPolicy.Step
  val Step = HttpPolicy.Step

  /** Does the answer say "slow down"? A 429, a 503/502/504, or MediaWiki's maxlag refusal — which comes back as HTTP
    * 200 with a `MediaWiki-API-Error: maxlag` header and `error.code = maxlag`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isMaxlag(a: HttpAnswer): Boolean =
    a.header(CoreQuota.HeaderName("mediawiki-api-error")).exists(_.value.trim.equalsIgnoreCase("maxlag")) ||
      (a.body.value.length < 4096 && a.text.value.contains("\"code\":\"maxlag\""))

  /** Pure: the next step after the `attempt`-th answer (0-based) — [[HttpPolicy.decide]]. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decide(
      a: HttpAnswer,
      attempt: HttpPolicy.Attempt,
      maxRetries: HttpPolicy.MaxRetries,
      maxRetryAfterSeconds: HttpPolicy.MaxRetryAfterSeconds,
      now: Instant
  ): Step =
    HttpPolicy.decide(
      HttpPolicy.StatusCode(a.code.value),
      a.header(CoreQuota.HeaderName("retry-after")).map(v => HttpPolicy.RetryAfter(v.value)),
      attempt,
      maxRetries,
      maxRetryAfterSeconds,
      now,
      Option.when(isMaxlag(a))(HttpPolicy.SlowDown("maxlag"))
    )

  /** Pure: a Retry-After value as milliseconds from `now` — [[HttpPolicy.retryAfterMs]]. */
  def retryAfterMs(v: Option[HttpPolicy.RetryAfter], now: Instant): Option[HttpPolicy.Step.Wait.Ms] =
    HttpPolicy.retryAfterMs(v, now)
