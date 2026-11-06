package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The `opencode` provider: `opencode run --agent <role> --format json` on the generated config. `chatgptLogin`: its
  * `openai/…` models are signed in with a ChatGPT plan, so they draw on the same plan pool as Codex (otherwise they are
  * metered).
  */
final case class OpencodeConfig(command: OpencodeConfig.Command, timeoutSeconds: OpencodeConfig.TimeoutSeconds, chatgptLogin: OpencodeConfig.ChatgptLogin) derives ConfigReader

object OpencodeConfig:
  type Command = Command.T
  object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChatgptLogin = ChatgptLogin.T
  object ChatgptLogin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
