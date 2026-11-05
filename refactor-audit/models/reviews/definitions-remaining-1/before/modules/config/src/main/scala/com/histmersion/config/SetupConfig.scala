package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The first-run setup (`setup`): `completed` once it was finished or skipped. */
final case class SetupConfig(completed: Boolean) derives ConfigReader
