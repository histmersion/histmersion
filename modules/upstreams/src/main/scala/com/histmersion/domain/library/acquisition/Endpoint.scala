package com.histmersion
package domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A keyless upstream's endpoint (`histmersion.acquire.<upstream>.url`). */
final case class Endpoint(url: Endpoint.Url) derives ConfigReader

object Endpoint:
  type Url = Url.T
  object Url extends RefinedType[String, Pure]:
    given ConfigReader[Url] = summon[ConfigReader[String]].emap(Url.either)
