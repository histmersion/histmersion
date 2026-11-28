package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.schedulerpolicy.Candidate
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.schedulerpolicy.Settings

final case class Scored(
    c: Candidate,
    waitedMinutes: Scored.WaitedMinutes,
    aging: Scored.Aging,
    effective: Scored.Effective,
    overdue: Scored.Overdue
)

object Scored:
  type WaitedMinutes = WaitedMinutes.T
  object WaitedMinutes extends Newtype[Long, Pure]
  type Aging = Aging.T
  object Aging extends Newtype[Int, Pure]
  type Effective = Effective.T
  object Effective extends Newtype[Int, Pure]
  type Overdue = Overdue.T
  object Overdue extends Newtype[Boolean, Pure]

  extension (x: Scored)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def goalKey(s: Settings): (Int, Long, String, Int, Int, Long, Long) =
      (
        -x.effective.value,
        x.c.goalCreated.fold(Long.MaxValue)(_.toEpochMilli),
        x.c.goal.map(_.value).getOrElse("￿"),
        -s.priorityOf(x.c.jobType).value,
        -x.c.itemPriority.value,
        -x.waitedMinutes.value,
        x.c.id.value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hours(minutes: Scored.WaitedMinutes): String =
    val m = minutes.value
    if m < 60 then s"$m min"
    else s"${m / 60} h" + (if m % 60 != 0 then s" ${m % 60} min" else "")
