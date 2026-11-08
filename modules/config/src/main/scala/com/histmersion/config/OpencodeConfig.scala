package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The `opencode` provider: `opencode run --agent <role> --format json` on the generated config. `chatgptLogin`: its
  * `openai/…` models are signed in with a ChatGPT plan, so they draw on the same plan pool as Codex (otherwise they are
  * metered).
  */
final case class OpencodeConfig(command: OpencodeConfig.Command, timeoutSeconds: OpencodeConfig.TimeoutSeconds, chatgptLogin: OpencodeConfig.ChatgptLogin) derives ConfigReader

object OpencodeConfig:
  type Command = Command.T
  object Command extends RefinedType[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type ChatgptLogin = ChatgptLogin.T
  object ChatgptLogin extends RefinedType[Boolean, Pure]
