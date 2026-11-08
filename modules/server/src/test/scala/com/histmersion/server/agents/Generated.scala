package com.histmersion
package server.agents

import com.histmersion.domain.automation.agents.{AgentDefinitions, AgentGenerator, Prompts}
import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome

/** Test helpers for the generated trees. */
object Generated:
  /** Every tool the bundled definitions name by placeholder, plus the agent tools: what a pure test hands the generator
    * as "known" (the HTTP suite checks the placeholders against the live `/mcp`).
    */
  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  def known(defs: AgentDefinitions): Set[ToolName] =
    val texts = defs.agents.toOption.toList.flatten.map(_.body.value) ++
      defs.fragments.toOption.toList.flatMap(_.values.map(_.value)) ++
      defs.skills.toOption.toList.flatMap(_.values.map(s => s.header.mkString("\n") + s.body))
    texts
      .flatMap(AgentDefinitions.placeholders)
      .map(t => ToolName.either(t).fold(m => throw AssertionError(m), identity))
      .toSet ++ ToolboxOrder.names

  def defs(): AgentGenerator.Defs =
    val d = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), EngineContributions.roster)
    AgentGenerator.Defs(
      d.agents.fold(m => throw AssertionError(m), identity),
      d.fragments.fold(m => throw AssertionError(m), identity),
      d.skills.fold(m => throw AssertionError(m), identity)
    )
