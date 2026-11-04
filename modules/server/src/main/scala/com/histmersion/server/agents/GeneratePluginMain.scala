package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.PluginTree

import java.nio.file.Path

/** `just plugin`: writes the checked-in Claude Code plugin under the repository root, its definitions holding the agent
  * tools and roles the engine is given today.
  */
@main def generatePlugin(root: String): Unit =
  given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(classOf[com.histmersion.domain.automation.agents.Prompts].getClassLoader)
  val store = summon[com.histmersion.util.storage.Stores].at(com.histmersion.util.storage.Location.AgentHomes, Path.of(root).toAbsolutePath.normalize)
  PluginTree.write(store, EngineContributions.roster).fold(message => { scribe.error(s"cannot generate the plugin: $message"); sys.exit(1) }, identity)
