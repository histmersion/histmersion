package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `passages` (#954): the defaults and caps of a caller's `limit` (passages shown) and `context` (characters around a
  * match), how many matches one call counts before it stops, and how long a caller's regex may run over one body before
  * it is refused.
  */
final case class PassageLimits(
    limit: Int,
    maxLimit: Int,
    context: Int,
    maxContext: Int,
    maxMatches: Int,
    regexTimeoutMs: Int
) derives ConfigReader:
  /** `limit` into `[1, maxLimit]`; absent or `<= 0` is the default. */
  def limitOf(requested: Option[Int]): Int =
    requested.filter(_ > 0).getOrElse(limit).max(1).min(math.max(1, maxLimit))

  /** `context` into `[0, maxContext]`; absent or negative is the default. */
  def contextOf(requested: Option[Int]): Int =
    requested.filter(_ >= 0).getOrElse(context).max(0).min(math.max(0, maxContext))
