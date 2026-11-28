package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure

final case class GoalRow(slug: Goal.Slug, status: Goal.Status.Wire, needsPlan: GoalRow.NeedsPlan)

object GoalRow:
  type NeedsPlan = NeedsPlan.T
  object NeedsPlan extends Newtype[Boolean, Pure]
