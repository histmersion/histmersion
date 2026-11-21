package com.histmersion
package fixtures.hellodeps

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class HelloDepsConfig(greeting: HelloDepsConfig.Greeting) derives ConfigReader

object HelloDepsConfig:
  type Greeting = Greeting.T
  object Greeting extends Newtype[String, Pure]
