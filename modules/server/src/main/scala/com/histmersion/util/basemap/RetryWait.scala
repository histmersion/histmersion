package com.histmersion
package util.basemap

import com.histmersion.api.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** How long to wait before asking a provider again, and why (for the log). */
final case class RetryWait(ms: RetryWait.Ms, why: Problem)

object RetryWait:
  type Ms = Ms.T
  object Ms extends Newtype[Long, Pure]
