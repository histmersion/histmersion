package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.CoreQuota.Description
import com.histmersion.domain.corequota.Purpose
import com.histmersion.domain.corequota.Backoff
import com.histmersion.domain.corequota.Grant
import com.histmersion.domain.CoreQuota

enum Denial:
  case Blocked(until: Instant, why: Backoff.Rationale)
  case OverBudget(purpose: Purpose, spent: Denial.OverBudget.Spent, cap: Denial.OverBudget.Cap)
  case Paced(waitMs: Denial.Paced.WaitMs)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: Description = this match
    case Blocked(until, why)       => Description(s"CORE asked to wait until $until (${why.said})")
    case OverBudget(p, spent, cap) =>
      Description(
        s"CORE's daily budget for ${p.label} is spent ($spent of $cap tokens today, UTC; acquire.core." +
          s"${if p == Purpose.Search then "search-daily-tokens" else "daily-tokens"})"
      )
    case Paced(waitMs) =>
      Description(
        s"CORE's next free slot is ${(waitMs.value + 999) / 1000} s away (acquire.core.per-minute), longer than " +
          "acquire.core.max-wait-seconds"
      )

object Denial:
  object OverBudget:
    export CoreQuota.{Cap, Spent}

  object Paced:
    export Grant.WaitMs
