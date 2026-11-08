package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

type ApiKey = ApiKey.T
object ApiKey extends Newtype[String, Pure]:
  given ConfigReader[ApiKey] = summon[ConfigReader[String]].emap(ApiKey.either)
