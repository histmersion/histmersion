package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  def limitOf(requested: Option[com.histmersion.config.PassageLimits.Limit]): com.histmersion.config.PassageLimits.Limit =
    requested.filter(_ > 0).getOrElse(limit).max(1).min(math.max(1, maxLimit))

  /** `context` into `[0, maxContext]`; absent or negative is the default. */
  def contextOf(requested: Option[com.histmersion.config.PassageLimits.Context]): com.histmersion.config.PassageLimits.Context =
    requested.filter(_ >= 0).getOrElse(context).max(0).min(math.max(0, maxContext))

object PassageLimits:

  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxLimit = MaxLimit.T
  object MaxLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Context = Context.T
  object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxContext = MaxContext.T
  object MaxContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegexTimeoutMs = RegexTimeoutMs.T
  object RegexTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
