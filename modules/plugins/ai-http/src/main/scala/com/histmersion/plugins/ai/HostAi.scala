package com.histmersion
package plugins.ai

import hearth.kindlings.sconfigderivation.ConfigReader

/** The settings of an HTTP AI provider plugin: none of its own beyond `enabled`, and Histmersion's
  * `agents.providers.<name>`, which the host supplies.
  */
final case class HostAi(agents: HostAgents) derives ConfigReader
