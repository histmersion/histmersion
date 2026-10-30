package com.histmersion.server.agents

import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

/** Whether the server listens on loopback only — the `claude-code` (and every plan-login) provider's precondition:
  * nobody else's request may spend the owner's plan.
  */
object ClaudeCodeEnv:
  /** `127.x`, `::1`, `localhost` — a bind nobody else can reach. `0.0.0.0` (every interface) is not. */
  def isLoopback(host: String): Boolean =
    val h = host.trim.toLowerCase(java.util.Locale.ROOT).stripPrefix("[").stripSuffix("]")
    h == "localhost" || h == "::1" || h.startsWith("127.")
