package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.{Codex, Opencode}

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import com.histmersion.lint.{LintRule, tolerated}

/** The name a runtime gives one of Histmersion's MCP tools: Claude Code `mcp__histmersion__get_source`, opencode
  * `histmersion_get_source`, Codex and the direct API the bare `get_source`.
  */
enum ToolStyle:
  case Claude, Opencode, Bare

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tool(name: ToolUse.Tool): String = this match
    case Claude   => s"mcp__${AgentDefinitions.McpServer}__$name"
    case Opencode => s"${AgentDefinitions.McpServer}_$name"
    case Bare     => name.value
