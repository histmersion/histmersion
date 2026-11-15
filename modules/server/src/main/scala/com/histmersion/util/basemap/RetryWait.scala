package com.histmersion
package util.basemap

import io.github.iltotore.iron.constraint.any.Pure

/** How long to wait before asking a provider again, and the status that asked to slow down (for the log). */
final case class RetryWait(ms: RetryWait.Ms, asked: RangeHttp.StatusCode)

object RetryWait:
  type Ms = Ms.T
  object Ms extends Newtype[Long, Pure]
