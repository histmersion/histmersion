package com.histmersion
package server.agents
import com.histmersion.util.problem.Problem

import sttp.ai.core.agent.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** What a CLI run printed, read into sttp-ai's result types: the conversation (text, tool calls and their results, in
  * order), the final text, usage, and an error or the plan's limit if it ended on one.
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class CliOutcome(
    entries: Vector[ConversationEntry],
    toolCalls: Vector[ToolCallRecord],
    finalText: Option[CliOutcome.FinalText],
    usage: TokenUsage,
    error: Option[Problem],
    turns: CliOutcome.Turns
)

object CliOutcome:
  type FinalText = FinalText.T
  object FinalText extends Newtype[String, Pure]
  type Turns = Turns.T
  object Turns extends Newtype[Int, Pure]
