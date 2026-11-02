package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `name`: the provider name it registers (`hello-ai`); `token`: an optional secret it is given (a reference, resolved
  * by the host — the provider only learns whether it got one).
  */
final case class HelloAiConfig(name: String, token: Option[Secret], replies: List[Reply]) derives ConfigReader
