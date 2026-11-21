package com.histmersion
package fixtures.helloai

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class HelloAiConfig(name: HelloAiConfig.Name, token: Option[Secret], replies: List[Reply])
    derives ConfigReader

object HelloAiConfig:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
