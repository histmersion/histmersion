package com.histmersion.domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader

/** A keyless upstream's endpoint (`histmersion.acquire.<upstream>.url`). */
final case class Endpoint(url: Endpoint.Url) derives ConfigReader

object Endpoint:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
