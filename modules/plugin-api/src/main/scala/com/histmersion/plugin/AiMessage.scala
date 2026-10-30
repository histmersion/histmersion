package com.histmersion.plugin

/** One entry of the conversation so far, oldest first. */
enum AiMessage:
  /** The user's message — the step's prompt, with the untrusted data in it. */
  case User(text: String)

  /** What the model answered on an earlier turn: its text and the tools it called. */
  case Assistant(text: String, toolCalls: List[AiToolCall])

  /** What the host's tool answered to one call. */
  case ToolResult(callId: String, tool: String, output: String)
