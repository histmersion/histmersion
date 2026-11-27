package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The `opencode` provider: `opencode run --agent <role> --format json` on the generated config. `chatgptLogin`: its
  * `openai/…` models are signed in with a ChatGPT plan, so they draw on the same plan pool as Codex (otherwise they are
  * metered).
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class OpencodeConfig(
    command: OpencodeConfig.Command,
    timeoutSeconds: OpencodeConfig.TimeoutSeconds,
    chatgptLogin: OpencodeConfig.ChatgptLogin
) derives ConfigReader

object OpencodeConfig:
  export ClaudeCodeConfig.Command
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type ChatgptLogin = ChatgptLogin.T
  object ChatgptLogin extends Newtype[Boolean, Pure]
