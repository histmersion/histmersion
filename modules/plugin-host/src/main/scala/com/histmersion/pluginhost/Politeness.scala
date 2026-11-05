package com.histmersion.pluginhost

import com.histmersion.domain.HttpPolicy

import java.time.Instant
import scala.jdk.CollectionConverters.*

/** What to do with a media or catalogue answer: [[HttpPolicy]], the one outbound policy, plus MediaWiki's `maxlag`
  * refusal (a 200 that means "slow down"). Pure.
  */
object Politeness:

  type IsMaxlagResult = IsMaxlagResult.T
  object IsMaxlagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideAttempt = DecideAttempt.T
  object DecideAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideMaxRetries = DecideMaxRetries.T
  object DecideMaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideMaxRetryAfterSeconds = DecideMaxRetryAfterSeconds.T
  object DecideMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterMsResult = RetryAfterMsResult.T
  object RetryAfterMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterMsV = RetryAfterMsV.T
  object RetryAfterMsV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Step = HttpPolicy.Step
  val Step = HttpPolicy.Step

  /** Does the answer say "slow down"? A 429, a 503/502/504, or MediaWiki's maxlag refusal — which comes back as HTTP
    * 200 with a `MediaWiki-API-Error: maxlag` header and `error.code = maxlag`.
    */
  def isMaxlag(a: HttpAnswer): com.histmersion.pluginhost.Politeness.IsMaxlagResult =
    a.header("mediawiki-api-error").exists(_.trim.equalsIgnoreCase("maxlag")) ||
      (a.body.length < 4096 && a.text.contains("\"code\":\"maxlag\""))

  /** Pure: the next step after the `attempt`-th answer (0-based) — [[HttpPolicy.decide]]. */
  def decide(a: HttpAnswer, attempt: com.histmersion.pluginhost.Politeness.DecideAttempt, maxRetries: com.histmersion.pluginhost.Politeness.DecideMaxRetries, maxRetryAfterSeconds: com.histmersion.pluginhost.Politeness.DecideMaxRetryAfterSeconds, now: Instant): Step =
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
  def retryAfterMs(v: Option[com.histmersion.pluginhost.Politeness.RetryAfterMsV], now: Instant): Option[com.histmersion.pluginhost.Politeness.RetryAfterMsResult] = HttpPolicy.retryAfterMs(v, now)
