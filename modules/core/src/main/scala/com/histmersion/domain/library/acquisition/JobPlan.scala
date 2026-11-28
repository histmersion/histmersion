package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library.acquisition.Recheckable
import com.histmersion.domain.AcquireRecheck.checkedSince
import com.histmersion.domain.AcquireRecheck.order
import com.histmersion.domain.library.acquisition.Tally

final case class JobPlan(all: JobPlan.All, upTo: JobPlan.UpTo, since: Instant)

object JobPlan:
  type All = All.T
  object All extends Newtype[Boolean, Pure]
  type UpTo = UpTo.T
  object UpTo extends Newtype[Int, Pure]

  extension (plan: JobPlan)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def jobPicks(
        candidates: List[Recheckable],
        triedByJob: Tally.Tried,
        due: Recheckable => Boolean
    ): List[Recheckable] =
      val left = order(candidates.filterNot(c => checkedSince(c.last, plan.since)))
      if plan.all.value then left
      else left.filter(due).take(math.max(0, plan.upTo.value - math.max(0, triedByJob.value)))
