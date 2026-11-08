package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.{Codex, Opencode}

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** The name a runtime gives one of Histmersion's MCP tools: Claude Code `mcp__histmersion__get_source`, opencode
  * `histmersion_get_source`, Codex and the direct API the bare `get_source`.
  */
enum ToolStyle derives CanEqual:
  case Claude, Opencode, Bare

  def tool(name: ToolUse.Tool): String = this match
    case Claude   => s"mcp__${AgentDefinitions.McpServer}__$name"
    case Opencode => s"${AgentDefinitions.McpServer}_$name"
    case Bare     => name.value
