package com.histmersion
package domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** A goal's schedule as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalScheduleRow(
    goalUid: UUID,
    kind: BGoalScheduleRow.Kind,
    cron: BGoalScheduleRow.Cron,
    enabled: BGoalScheduleRow.Enabled,
    budgetUsd: Option[BigDecimal]
) derives DbCodec

object BGoalScheduleRow:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  enum Enabled derives CanEqual:
    case Enabled, Disabled
  object Enabled extends TwoCases[Enabled]:
    extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
