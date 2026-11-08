package com.histmersion
package server.agents

import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a CLI run printed, read into sttp-ai's result types: the conversation (text, tool calls and their results, in
  * order), the final text, usage, and an error or the plan's limit if it ended on one.
  */
final case class CliOutcome(
    entries: Vector[ConversationEntry],
    toolCalls: Vector[ToolCallRecord],
    finalText: Option[CliOutcome.FinalText],
    usage: TokenUsage,
    error: Option[CliOutcome.Error],
    turns: CliOutcome.Turns
)

object CliOutcome:
  type FinalText = FinalText.T
  object FinalText extends RefinedType[String, Pure]
  type Error = Error.T
  object Error extends RefinedType[String, Pure]
  type Turns = Turns.T
  object Turns extends RefinedType[Int, Pure]
