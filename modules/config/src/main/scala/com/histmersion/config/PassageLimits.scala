package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `passages` (#954): the defaults and caps of a caller's `limit` (passages shown) and `context` (characters around a
  * match), how many matches one call counts before it stops, and how long a caller's regex may run over one body before
  * it is refused.
  */
final case class PassageLimits(
    limit: PassageLimits.Limit,
    maxLimit: PassageLimits.MaxLimit,
    context: PassageLimits.Context,
    maxContext: PassageLimits.MaxContext,
    maxMatches: PassageLimits.MaxMatches,
    regexTimeoutMs: PassageLimits.RegexTimeoutMs
) derives ConfigReader:
  /** `limit` into `[1, maxLimit]`; absent or `<= 0` is the default. */
  def limitOf(requested: Option[PassageLimits.Limit]): PassageLimits.Limit =
    PassageLimits.Limit(requested.map(_.value).filter(_ > 0).getOrElse(limit.value).max(1).min(math.max(1, maxLimit.value)))

  /** `context` into `[0, maxContext]`; absent or negative is the default. */
  def contextOf(requested: Option[PassageLimits.Context]): PassageLimits.Context =
    PassageLimits.Context(requested.map(_.value).filter(_ >= 0).getOrElse(context.value).max(0).min(math.max(0, maxContext.value)))

object PassageLimits:

  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
  type MaxLimit = MaxLimit.T
  object MaxLimit extends RefinedType[Int, Pure]
  type Context = Context.T
  object Context extends RefinedType[Int, Pure]
  type MaxContext = MaxContext.T
  object MaxContext extends RefinedType[Int, Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends RefinedType[Int, Pure]
  type RegexTimeoutMs = RegexTimeoutMs.T
  object RegexTimeoutMs extends RefinedType[Int, Pure]
