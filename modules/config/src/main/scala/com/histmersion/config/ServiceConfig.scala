package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream service. */
final case class ServiceConfig(url: ServiceConfig.Url) derives ConfigReader

object ServiceConfig:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
