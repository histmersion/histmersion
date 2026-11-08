package com.histmersion
package server.agents

import com.histmersion.domain.automation.agents.PluginTree

import java.nio.file.Path
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.domain.automation.agents.Prompts
import com.histmersion.util.storage.Location

/** `just plugin`: writes the checked-in Claude Code plugin under the repository root, its definitions holding the agent
  * tools and roles the engine is given today.
  */
@main def generatePlugin(root: String): Unit =
  given Stores = Stores.disk
  given Resources = Resources.classpath(classOf[Prompts].getClassLoader)
  val store = summon[Stores].at(Location.AgentHomes, Path.of(root).toAbsolutePath.normalize)
  PluginTree
    .write(store, EngineContributions.roster)
    .fold(message => { scribe.error(s"cannot generate the plugin: $message"); sys.exit(1) }, identity)
