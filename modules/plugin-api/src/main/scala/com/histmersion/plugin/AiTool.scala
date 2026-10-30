package com.histmersion.plugin

/** A tool the step offers the model: its name, what it does, and its input as a JSON Schema (text). */
final case class AiTool(name: String, description: String, inputSchema: String)
