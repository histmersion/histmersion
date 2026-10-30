package com.histmersion.plugin

/** A tool call the model made: the call's id (echoed in its [[AiMessage.ToolResult]]), the tool, and its input as JSON
  * text.
  */
final case class AiToolCall(id: String, tool: String, input: String)
