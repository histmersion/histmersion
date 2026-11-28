package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LimitState(
    limit: Limit,
    used: BigDecimal,
    remaining: BigDecimal,
    reserve: BigDecimal,
    usable: LimitState.Usable,
    freesAt: Option[LimitState.FreesAt]
):
  def headroom: BigDecimal = (limit.amount - reserve - used).max(0)

object LimitState:
  type Now = Now.T
  object Now extends Newtype[Long, Pure]
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  type FreesAt = FreesAt.T
  object FreesAt extends Newtype[Long, Pure]
