package com.histmersion.plugin

/** One entry of the conversation so far, oldest first. */
enum AiMessage:
  /** The user's message — the step's prompt, with the untrusted data in it. */
  case User(text: User.Text)

  /** What the model answered on an earlier turn: its text and the tools it called. */
  case Assistant(text: Assistant.Text, toolCalls: List[AiTool.Call])

  /** What the host's tool answered to one call. */
  case ToolResult(callId: ToolResult.CallId, tool: ToolResult.Tool, output: ToolResult.Output)

object AiMessage:
  object User:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Assistant:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object ToolResult:
    type CallId = CallId.T
    object CallId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tool = Tool.T
    object Tool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
