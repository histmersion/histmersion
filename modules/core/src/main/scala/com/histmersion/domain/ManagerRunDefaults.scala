package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.AutomationManagers)
final case class ManagerRunDefaults(maxRuns: ManagerRunDefaults.MaxRuns)

object ManagerRunDefaults:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
