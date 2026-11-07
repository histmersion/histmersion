package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** How the agent shows in the person's own Claude Code (the interactive plugin): its model and its built-in tools,
  * exactly as the hand-written definitions had them.
  */
final case class InteractiveSpec(model: InteractiveSpec.Model, tools: List[InteractiveSpec.Tools])

object InteractiveSpec:
  type Model = com.histmersion.domain.automation.agents.AgentsControllers.ModelName
  val Model: _root_.com.histmersion.domain.automation.agents.AgentsControllers.ModelName.type = _root_.com.histmersion.domain.automation.agents.AgentsControllers.ModelName
  type Tools = com.histmersion.domain.automation.agents.ToolUse.Tool
  val Tools: _root_.com.histmersion.domain.automation.agents.ToolUse.Tool.type = _root_.com.histmersion.domain.automation.agents.ToolUse.Tool
