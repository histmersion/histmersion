package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The first-run setup (`setup`): `completed` once it was finished or skipped. */
final case class SetupConfig(completed: SetupConfig.Completed) derives ConfigReader

object SetupConfig:
  type Completed = Completed.T
  object Completed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
