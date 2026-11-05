package com.histmersion.domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

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
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cron = Cron.T
  object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
