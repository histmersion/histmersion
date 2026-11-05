package com.histmersion.domain

final case class ManagerRunDefaults(maxRuns: ManagerRunDefaults.MaxRuns)

object ManagerRunDefaults:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
