package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Thresholds(
    max: Thresholds.Max,
    minSources: Thresholds.MinSources,
    plansByPerson: Thresholds.PlansByPerson = Thresholds.PlansByPerson(false),
    openPersonPlans: Thresholds.OpenPersonPlans = Thresholds.OpenPersonPlans(0)
)

object Thresholds:
  type Max = Max.T
  object Max extends Newtype[Int, Pure]
  type MinSources = MinSources.T
  object MinSources extends Newtype[Int, Pure]
  type PlansByPerson = PlansByPerson.T
  object PlansByPerson extends Newtype[Boolean, Pure]
  type OpenPersonPlans = OpenPersonPlans.T
  object OpenPersonPlans extends Newtype[Int, Pure]
