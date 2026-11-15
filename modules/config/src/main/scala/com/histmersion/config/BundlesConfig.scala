package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `bundle export|verify|import` (docs/research-merge-import.md): where the server builds a bundle before it is
  * downloaded and keeps an uploaded one while it is read, and how long a finished export is left there for its download
  * before the next export clears it.
  */
final case class BundlesConfig(dir: BundlesConfig.Dir, staleMinutes: BundlesConfig.StaleMinutes) derives ConfigReader

object BundlesConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type StaleMinutes = StaleMinutes.T
  object StaleMinutes extends Newtype[Int, Pure]
