package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.CoreQuota.Cap
import com.histmersion.domain.corequota.Purpose
import com.histmersion.domain.CoreQuota.Cost
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Limits(
    perMinute: Limits.PerMinute,
    dailyTokens: Limits.DailyTokens,
    searchDailyTokens: Limits.SearchDailyTokens,
    lookupCost: Limits.LookupCost,
    searchCost: Limits.SearchCost,
    exhaustedPauseSeconds: Limits.ExhaustedPauseSeconds,
    maxWaitSeconds: Limits.MaxWaitSeconds
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cost(p: Purpose): Cost = p match
    case Purpose.Lookup => Cost(lookupCost.value.max(0))
    case Purpose.Search => Cost(searchCost.value.max(0))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dailyCap: Cap = Cap(dailyTokens.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def searchCap: Cap = Cap(searchDailyTokens.value)

object Limits:

  type PerMinute = PerMinute.T
  object PerMinute extends Newtype[Int, Pure]
  type DailyTokens = DailyTokens.T
  object DailyTokens extends Newtype[Int, Pure]
  type SearchDailyTokens = SearchDailyTokens.T
  object SearchDailyTokens extends Newtype[Int, Pure]
  type LookupCost = LookupCost.T
  object LookupCost extends Newtype[Int, Pure]
  type SearchCost = SearchCost.T
  object SearchCost extends Newtype[Int, Pure]
  type ExhaustedPauseSeconds = ExhaustedPauseSeconds.T
  object ExhaustedPauseSeconds extends Newtype[Int, Pure]
  type MaxWaitSeconds = MaxWaitSeconds.T
  object MaxWaitSeconds extends Newtype[Int, Pure]
