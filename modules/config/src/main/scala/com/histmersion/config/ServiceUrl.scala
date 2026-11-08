package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type ServiceUrl = ServiceUrl.T
object ServiceUrl extends RefinedType[String, Pure]:
  given ConfigReader[ServiceUrl] = summon[ConfigReader[String]].emap(ServiceUrl.either)
