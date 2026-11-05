package com.histmersion.util.basemap

import java.time.Instant

/** When the base map's client asks a provider again: the product's outbound-HTTP policy, which the composition root
  * supplies.
  */
trait RetryPolicy:
  /** The wait before asking again after the `attempt`-th (0-based) failed connection. */
  def backoffMs(attempt: com.histmersion.util.basemap.RetryPolicy.BackoffMsAttempt): com.histmersion.util.basemap.RetryPolicy.BackoffMsResult

  /** The wait before asking again after the `attempt`-th (0-based) answer, when it asks to slow down and may be waited
    * for; `None` takes the answer as it is.
    */
  def slowDown(
      code: com.histmersion.util.basemap.RetryPolicy.SlowDownCode,
      retryAfter: Option[com.histmersion.util.basemap.RetryPolicy.SlowDownRetryAfter],
      attempt: com.histmersion.util.basemap.RetryPolicy.SlowDownAttempt,
      maxRetries: com.histmersion.util.basemap.RetryPolicy.SlowDownMaxRetries,
      maxRetryAfterSeconds: com.histmersion.util.basemap.RetryPolicy.SlowDownMaxRetryAfterSeconds,
      now: Instant
  ): Option[RetryWait]

object RetryPolicy:
  type BackoffMsResult = BackoffMsResult.T
  object BackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMsAttempt = BackoffMsAttempt.T
  object BackoffMsAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownCode = SlowDownCode.T
  object SlowDownCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownRetryAfter = SlowDownRetryAfter.T
  object SlowDownRetryAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownAttempt = SlowDownAttempt.T
  object SlowDownAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetries = SlowDownMaxRetries.T
  object SlowDownMaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlowDownMaxRetryAfterSeconds = SlowDownMaxRetryAfterSeconds.T
  object SlowDownMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
