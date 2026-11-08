package com.histmersion
package util.basemap

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** When the base map's client asks a provider again: the product's outbound-HTTP policy, which the composition root
  * supplies.
  */
trait RetryPolicy:
  /** The wait before asking again after the `attempt`-th (0-based) failed connection. */
  def backoffMs(attempt: Int): RetryWait.Ms

  /** The wait before asking again after the `attempt`-th (0-based) answer, when it asks to slow down and may be waited
    * for; `None` takes the answer as it is.
    */
  def slowDown(
      code: RangeHttp.StatusCode,
      retryAfter: Option[RetryPolicy.RetryAfterHeader],
      attempt: Int,
      maxRetries: RetryPolicy.MaxRetries,
      maxRetryAfterSeconds: RetryPolicy.RetryAfterCap,
      now: Instant
  ): Option[RetryWait]

object RetryPolicy:
  type MaxRetries = MaxRetries.T
  object MaxRetries extends RefinedType[Int, Pure]
  type RetryAfterCap = RetryAfterCap.T
  object RetryAfterCap extends RefinedType[Int, Pure]
  type RetryAfterHeader = RetryAfterHeader.T
  object RetryAfterHeader extends RefinedType[String, Pure]
