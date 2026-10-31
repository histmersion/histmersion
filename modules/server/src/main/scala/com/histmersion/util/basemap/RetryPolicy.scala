package com.histmersion.util.basemap

import java.time.Instant

/** When the base map's client asks a provider again: the product's outbound-HTTP policy, which the composition root
  * supplies.
  */
trait RetryPolicy:
  /** The wait before asking again after the `attempt`-th (0-based) failed connection. */
  def backoffMs(attempt: Int): Long

  /** The wait before asking again after the `attempt`-th (0-based) answer, when it asks to slow down and may be waited
    * for; `None` takes the answer as it is.
    */
  def slowDown(
      code: Int,
      retryAfter: Option[String],
      attempt: Int,
      maxRetries: Int,
      maxRetryAfterSeconds: Int,
      now: Instant
  ): Option[RetryWait]
