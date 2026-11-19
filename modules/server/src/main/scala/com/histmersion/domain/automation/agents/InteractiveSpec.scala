package com.histmersion
package domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** How the agent shows in the person's own Claude Code (the interactive plugin): its model and its built-in tools,
  * exactly as the hand-written definitions had them.
  */
final case class InteractiveSpec(model: InteractiveSpec.Model, tools: List[InteractiveSpec.Tools])

object InteractiveSpec:
  export AgentsControllers.ModelName as Model
  export ToolUse.Tool as Tools
