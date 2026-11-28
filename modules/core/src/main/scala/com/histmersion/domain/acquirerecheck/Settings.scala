package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AcquireRecheck.Allowance
import com.histmersion.domain.AcquireRecheck.CheckedToday
import com.histmersion.domain.acquirerecheck.Shared

final case class Settings(
    enabled: Settings.Enabled,
    everyDays: Settings.EveryDays,
    perDay: Settings.PerDay,
    perPass: Settings.PerPass,
    passMinutes: Settings.PassMinutes,
    sharePercent: Settings.SharePercent,
    retryHours: Settings.RetryHours
)

object Settings:
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type EveryDays = EveryDays.T
  object EveryDays extends Newtype[Int, Pure]:
    extension (d: EveryDays) def after(at: Instant): Instant = at.plusSeconds(math.max(1, d.value).toLong * 86400L)
  type PerDay = PerDay.T
  object PerDay extends Newtype[Int, Pure]
  type PerPass = PerPass.T
  object PerPass extends Newtype[Int, Pure]
  type PassMinutes = PassMinutes.T
  object PassMinutes extends Newtype[Int, Pure]
  type SharePercent = SharePercent.T
  object SharePercent extends Newtype[Int, Pure]
  type RetryHours = RetryHours.T
  object RetryHours extends Newtype[Int, Pure]:
    extension (h: RetryHours) def after(at: Instant): Instant = at.plusSeconds(math.max(1, h.value).toLong * 3600L)

  extension (s: Settings)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def allowance(checkedToday: CheckedToday, shared: Option[Shared]): Allowance =
      val own = math.min(math.max(0, s.perDay.value) - math.max(0, checkedToday.value), math.max(0, s.perPass.value))
      val room = shared.fold(Int.MaxValue) { sh =>
        val share = math.max(0, sh.cap.value) * math.max(0, math.min(100, s.sharePercent.value)) / 100
        math.min(sh.left.value - math.max(0, sh.backlogPending.value), share - math.max(0, sh.fetchedToday.value))
      }
      Allowance(math.max(0, math.min(own, room)))
