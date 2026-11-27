package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** What one tool call of an agent step may return (every runtime: the agent MCP endpoint a run token reaches and the
  * API tool loop). Every later turn of the step re-sends the result, so a long one is cut at a line with a trailer
  * saying how to read on: `resultMaxChars` for any result, `bodyChars` the body slice a `get_source section=body`
  * without `chars` reads, `queryMaxRows` the rows of a `query`.
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class AgentToolsConfig(
    resultMaxChars: AgentToolsConfig.ResultMaxChars,
    bodyChars: AgentToolsConfig.BodyChars,
    queryMaxRows: AgentToolsConfig.QueryMaxRows
) derives ConfigReader
object AgentToolsConfig:
  /** No cap (0 = off): what a tool returns is what the handler returned. */
  type ResultMaxChars = ResultMaxChars.T
  object ResultMaxChars extends Newtype[Int, Pure]
  type BodyChars = BodyChars.T
  object BodyChars extends Newtype[Int, Pure]
  type QueryMaxRows = QueryMaxRows.T
  object QueryMaxRows extends Newtype[Int, Pure]

  val Off: AgentToolsConfig = AgentToolsConfig(ResultMaxChars(0), BodyChars(0), QueryMaxRows(0))
