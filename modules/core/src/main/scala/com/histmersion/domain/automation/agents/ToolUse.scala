package com.histmersion
package domain.automation.agents

import io.github.iltotore.iron.constraint.any.Pure

/** A tool call an LLM step made, as its runtime reported it (sttp-ai's loop, Claude Code's stream-json, Codex's
  * `mcp_tool_call` items, opencode's `tool_use` parts) — never as the model describes it: what an answer's claimed
  * lookups are held to.
  */
final case class ToolUse(tool: ToolUse.Tool, input: ToolUse.Input)

object ToolUse:
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]
  type Input = Input.T
  object Input extends Newtype[String, Pure]
