package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The `opencode` provider: `opencode run --agent <role> --format json` on the generated config. `chatgptLogin`: its
  * `openai/…` models are signed in with a ChatGPT plan, so they draw on the same plan pool as Codex (otherwise they are
  * metered).
  */
final case class OpencodeConfig(command: String, timeoutSeconds: Int, chatgptLogin: Boolean) derives ConfigReader
