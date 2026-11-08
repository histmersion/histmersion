package com.histmersion
package util.basemap

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** How long to wait before asking a provider again, and why (for the log). */
final case class RetryWait(ms: RetryWait.Ms, why: RetryWait.Why)

object RetryWait:
  type Ms = Ms.T
  object Ms extends RefinedType[Long, Pure]
  type Why = Why.T
  object Why extends RefinedType[String, Pure]
