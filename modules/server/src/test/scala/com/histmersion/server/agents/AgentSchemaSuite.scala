package com.histmersion.server.agents

import com.histmersion.core.Agents.ProbeAnswer
import com.histmersion.server.McpTools.GetSourceInput
import sttp.ai.core.agent.{AgentTool, ResponseSchema}

/** The JSON schemas sttp-ai sends to a provider come from the same Kindlings-derived Tapir schemas the
  * MCP tools advertise — no second schema stack. */
class AgentSchemaSuite extends munit.FunSuite:

  test("a typed answer's response schema renders from its Kindlings schema"):
    val js = sttp.apispec.circe.encoderSchema(ResponseSchema.derived[ProbeAnswer]().schema).noSpaces
    assert(js.contains("\"title\""), js)
    assert(js.contains("\"citekey\""), js)

  test("a tool's input schema is the MCP tool's own"):
    val t = AgentTool.fromFunction[GetSourceInput]("get_source", "read")(_ => "")
    val js = t.rawJsonSchema.noSpaces
    assert(js.contains("\"citekey\""), js)
    assert(js.contains("source's citekey"), js) // the MCP description travels with it
