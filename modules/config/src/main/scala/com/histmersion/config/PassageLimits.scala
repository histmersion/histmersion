package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** `passages` (#954): the defaults and caps of a caller's `limit` (passages shown) and `context` (characters around a
  * match), how many matches one call counts before it stops, and how long a caller's regex may run over one body before
  * it is refused.
  */
@movesTo(FeaturePlace.LibrarySources)
final case class PassageLimits(
    limit: PassageLimits.Limit,
    maxLimit: PassageLimits.MaxLimit,
    context: PassageLimits.Context,
    maxContext: PassageLimits.MaxContext,
    maxMatches: PassageLimits.MaxMatches,
    regexTimeoutMs: PassageLimits.RegexTimeoutMs
) derives ConfigReader:
  /** `limit` into `[1, maxLimit]`; absent or `<= 0` is the default. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limitOf(requested: Option[PassageLimits.Limit]): PassageLimits.Limit =
    PassageLimits.Limit(
      requested.map(_.value).filter(_ > 0).getOrElse(limit.value).max(1).min(math.max(1, maxLimit.value))
    )

  /** `context` into `[0, maxContext]`; absent or negative is the default. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contextOf(requested: Option[PassageLimits.Context]): PassageLimits.Context =
    PassageLimits.Context(
      requested.map(_.value).filter(_ >= 0).getOrElse(context.value).max(0).min(math.max(0, maxContext.value))
    )

object PassageLimits:

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type MaxLimit = MaxLimit.T
  object MaxLimit extends Newtype[Int, Pure]
  type Context = Context.T
  object Context extends Newtype[Int, Pure]
  type MaxContext = MaxContext.T
  object MaxContext extends Newtype[Int, Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends Newtype[Int, Pure]
  type RegexTimeoutMs = RegexTimeoutMs.T
  object RegexTimeoutMs extends Newtype[Int, Pure]
