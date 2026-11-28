package com.histmersion
package api.automation.modelchains

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LimitStatus(
    limit: LimitStatus.Limit,
    measure: Measure,
    window: LimitStatus.Window,
    amount: LimitStatus.Amount,
    used: LimitStatus.Used,
    remaining: LimitStatus.Remaining,
    reserve: LimitStatus.Reserve,
    usable: LimitStatus.Usable,
    freesAt: Option[LimitStatus.FreesAt] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LimitStatus:
  type Limit = Limit.T
  object Limit extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Window = Window.T
  object Window extends Newtype[String, Pure]
  type Amount = Amount.T
  object Amount extends Newtype[Double, Pure]
  type Used = Used.T
  object Used extends Newtype[Double, Pure]
  type Remaining = Remaining.T
  object Remaining extends Newtype[Double, Pure]
  type Reserve = Reserve.T
  object Reserve extends Newtype[Double, Pure]
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  type FreesAt = FreesAt.T
  object FreesAt extends Newtype[String, Pure]
