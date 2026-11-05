package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** What one tool call of an agent step may return (every runtime: the agent MCP endpoint a run token reaches and the
  * API tool loop). Every later turn of the step re-sends the result, so a long one is cut at a line with a trailer
  * saying how to read on: `resultMaxChars` for any result, `bodyChars` the body slice a `get_source section=body`
  * without `chars` reads, `queryMaxRows` the rows of a `query`.
  */
final case class AgentToolsConfig(resultMaxChars: Int, bodyChars: Int, queryMaxRows: Int) derives ConfigReader
object AgentToolsConfig:
  /** No cap (0 = off): what a tool returns is what the handler returned. */
  val Off: AgentToolsConfig = AgentToolsConfig(0, 0, 0)
