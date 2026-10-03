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

  test("the review panel's answers render as response schemas, with the field descriptions the prompts rely on"):
    import com.histmersion.core.ReviewArticle.*
    def js[A](s: ResponseSchema[A]) = sttp.apispec.circe.encoderSchema(s.schema).noSpaces
    val skeptic = js(ResponseSchema.derived[SkepticAnswer]())
    assert(skeptic.contains("\"proposals\"") && skeptic.contains("superseded_by"), skeptic)
    val fixer = js(ResponseSchema.derived[FixerAnswer]())
    assert(fixer.contains("WHOLE revised article body") && fixer.contains("copied EXACTLY"), fixer)
    assert(js(ResponseSchema.derived[ColdReaderAnswer]()).contains("\"explainBack\""))
    assert(js(ResponseSchema.derived[RulesAnswer]()).contains("\"accuracyChecked\""))

  test("followups as an agent tool has no apply flag"):
    val t = Toolbox(null, () => AgentUpstream.Offline).tool(ToolName.Followups)
    assert(!t.rawJsonSchema.noSpaces.contains("apply"), t.rawJsonSchema.noSpaces)
