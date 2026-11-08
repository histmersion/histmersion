package com.histmersion
package domain.automation.workflows

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** The name of a tool an agent may be offered (`get_source`, `check_edits`, …): what a definition's `tools.histmersion`
  * lists, what the agent loop and `/mcp/agent/<agent>` call it by.
  */
type ToolName = ToolName.T

object ToolName extends RefinedType[String, Match["[a-z][a-z0-9_]*"]]
