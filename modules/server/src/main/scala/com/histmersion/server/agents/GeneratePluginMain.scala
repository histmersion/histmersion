package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.PluginTree

import java.nio.file.Path

/** `just plugin`: writes the checked-in Claude Code plugin under the repository root, its definitions holding the agent
  * tools the engine is given today.
  */
@main def generatePlugin(root: String): Unit =
  PluginTree.write(Path.of(root).toAbsolutePath.normalize, TransitionTools.names)
