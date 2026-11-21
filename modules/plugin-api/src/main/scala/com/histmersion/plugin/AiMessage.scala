package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** One entry of the conversation so far, oldest first. */
enum AiMessage:
  /** The user's message — the step's prompt, with the untrusted data in it. */
  case User(text: AiMessage.User.Text)

  /** What the model answered on an earlier turn: its text and the tools it called. */
  case Assistant(text: AiMessage.Assistant.Text, toolCalls: List[AiTool.Call])

  case ToolResult(
      callId: AiMessage.ToolResult.CallId,
      tool: AiMessage.ToolResult.Tool,
      output: AiMessage.ToolResult.Output
  )

object AiMessage:
  object User:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object Assistant:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object ToolResult:
    type CallId = CallId.T
    object CallId extends Newtype[String, Pure]
    type Tool = Tool.T
    object Tool extends Newtype[String, Pure]
    type Output = Output.T
    object Output extends Newtype[String, Pure]
