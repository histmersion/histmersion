package com.histmersion
package fixtures.hello

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class HelloConfig(
    greeting: HelloConfig.Greeting,
    greeter: HelloConfig.Greeter,
    mirrors: List[HelloConfig.Mirrors]
) derives ConfigReader

object HelloConfig:
  type Greeting = Greeting.T
  object Greeting extends Newtype[String, Pure]
  type Greeter = Greeter.T
  object Greeter extends Newtype[String, Pure]
  type Mirrors = Mirrors.T
  object Mirrors extends Newtype[String, Pure]
