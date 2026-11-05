package com.histmersion.domain.automation.agents

/** A tool call an LLM step made, as its runtime reported it (sttp-ai's loop, Claude Code's stream-json, Codex's
  * `mcp_tool_call` items, opencode's `tool_use` parts) — never as the model describes it: what an answer's claimed
  * lookups are held to.
  */
final case class ToolUse(tool: ToolUse.Tool, input: ToolUse.Input)

object ToolUse:
  type Tool = Tool.T
  object Tool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Input = Input.T
  object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
