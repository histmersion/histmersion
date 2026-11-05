package com.histmersion.server.agents

import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*

/** What a CLI run printed, read into sttp-ai's result types: the conversation (text, tool calls and their results, in
  * order), the final text, usage, and an error or the plan's limit if it ended on one.
  */
final case class CliOutcome(
    entries: Vector[ConversationEntry],
    toolCalls: Vector[ToolCallRecord],
    finalText: Option[String],
    usage: TokenUsage,
    error: Option[String],
    turns: Int
)
