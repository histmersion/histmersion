package com.histmersion.util.basemap

import java.time.Instant

/** When the base map's client asks a provider again: the product's outbound-HTTP policy, which the composition root
  * supplies.
  */
trait RetryPolicy:
  /** The wait before asking again after the `attempt`-th (0-based) failed connection. */
  def backoffMs(attempt: Int): com.histmersion.util.basemap.RetryWait.Ms

  /** The wait before asking again after the `attempt`-th (0-based) answer, when it asks to slow down and may be waited
    * for; `None` takes the answer as it is.
    */
  def slowDown(
      code: com.histmersion.util.basemap.RangeHttp.StatusCode,
      retryAfter: Option[com.histmersion.util.basemap.RetryPolicy.RetryAfterHeader],
      attempt: Int,
      maxRetries: com.histmersion.util.basemap.RetryPolicy.MaxRetries,
      maxRetryAfterSeconds: com.histmersion.util.basemap.RetryPolicy.RetryAfterCap,
      now: Instant
  ): Option[RetryWait]

object RetryPolicy:
  type MaxRetries = MaxRetries.T
  object MaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterCap = RetryAfterCap.T
  object RetryAfterCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterHeader = RetryAfterHeader.T
  object RetryAfterHeader extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
