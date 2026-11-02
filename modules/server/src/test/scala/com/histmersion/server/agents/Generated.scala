package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{AgentDefinitions, AgentGenerator, Prompts}

/** Test helpers for the generated trees. */
object Generated:
  /** Every tool the bundled definitions name by placeholder, plus the agent tools: what a pure test hands the generator
    * as "known" (the HTTP suite checks the placeholders against the live `/mcp`).
    */
  def known(defs: AgentDefinitions): Set[String] =
    val texts = defs.agents.toOption.toList.flatten.map(_.body) ++
      defs.fragments.toOption.toList.flatMap(_.values) ++
      defs.skills.toOption.toList.flatMap(_.values.map(s => s.header.mkString("\n") + s.body))
    texts.flatMap(AgentDefinitions.placeholders).toSet ++ ToolboxOrder.names.map(_.value)

  def defs(): AgentGenerator.Defs =
    val d = AgentDefinitions(Prompts(""), EngineContributions.roster)
    AgentGenerator.Defs(
      d.agents.fold(m => throw AssertionError(m), identity),
      d.fragments.fold(m => throw AssertionError(m), identity),
      d.skills.fold(m => throw AssertionError(m), identity)
    )
