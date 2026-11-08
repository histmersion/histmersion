package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ManagerRunDefaults(maxRuns: ManagerRunDefaults.MaxRuns)

object ManagerRunDefaults:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends RefinedType[Int, Pure]
