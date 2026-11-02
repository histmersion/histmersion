package com.histmersion.plugin

/** A tool the step offers the model: its name, what it does, and its input as a JSON Schema (text). */
final case class AiTool(name: String, description: String, inputSchema: String)

object AiTool:

  /** A tool call the model made: the call's id (echoed in its [[AiMessage.ToolResult]]), the tool, and its input as
    * JSON text.
    */
  final case class Call(id: String, tool: String, input: String)
