package com.histmersion.util.basemap

/** How long to wait before asking a provider again, and why (for the log). */
final case class RetryWait(ms: RetryWait.Ms, why: RetryWait.Why)

object RetryWait:
  type Ms = Ms.T
  object Ms extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Why = Why.T
  object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
