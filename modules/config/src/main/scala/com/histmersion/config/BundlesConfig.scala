package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `bundle export|verify|import` (docs/research-merge-import.md): where the server builds a bundle before it is
  * downloaded and keeps an uploaded one while it is read, and how long a finished export is left there for its download
  * before the next export clears it.
  */
final case class BundlesConfig(dir: BundlesConfig.Dir, staleMinutes: BundlesConfig.StaleMinutes) derives ConfigReader

object BundlesConfig:
  type Dir = Dir.T
  object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleMinutes = StaleMinutes.T
  object StaleMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
