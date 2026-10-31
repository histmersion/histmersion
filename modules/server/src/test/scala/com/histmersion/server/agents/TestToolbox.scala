package com.histmersion.server.agents

import com.histmersion.config.AgentToolsConfig
import com.histmersion.server.Handlers

/** The toolbox of today's transition tools over `h` (no upstream attached), as the engine holds it. */
object TestToolbox:
  def apply(h: Handlers, caps: AgentToolsConfig = AgentToolsConfig.Off): Toolbox =
    Toolbox(TransitionTools(h, () => AgentUpstream.Offline, caps).contributions)
