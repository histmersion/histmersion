package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class CorpusConfig(dir: CorpusConfig.Dir) derives ConfigReader

object CorpusConfig:
  type Dir = Dir.T
  object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
