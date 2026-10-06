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
    // the fixer answers with edits, never a whole body
    assert(fixer.contains("\"edits\"") && fixer.contains("\"anchor\"") && fixer.contains("insertAfter") && fixer.contains("copied EXACTLY"), fixer)
    assert(!fixer.contains("\"body\""), fixer)
    val cold = js(ResponseSchema.derived[ColdReaderAnswer]())
    assert(cold.contains("\"explainBack\"") && !cold.contains("\"evidence\""), "the cold reader cites no records: " + cold)
    val rules = js(ResponseSchema.derived[RulesAnswer]())
    assert(rules.contains("\"accuracyChecked\""))
    // a rules or skeptic finding carries its records: citekey, offset, quote
    for s <- List(rules, skeptic) do assert(s.contains("\"evidence\"") && s.contains("\"offset\"") && s.contains("\"citekey\""), s)

  test("#981: the findings answer carries placeKind, with the kinds the geocoder understands"):
    val js = sttp.apispec.circe.encoderSchema(
      ResponseSchema.derived[com.histmersion.core.EnrichSource.Findings]().schema).noSpaces
    assert(js.contains("\"placeKind\""), js)
    assert(List("site (", "region (", "polity (", "water (").forall(js.contains), js)

  test("followups as an agent tool has no apply flag"):
    val t = Toolbox(null, () => AgentUpstream.Offline).tool(ToolName.Followups)
    assert(!t.rawJsonSchema.noSpaces.contains("apply"), t.rawJsonSchema.noSpaces)

  test("check_edits as an agent tool takes the MCP tool's input: slug, edits (op, anchor, text) or a body"):
    val t = Toolbox(null, () => AgentUpstream.Offline).tool(ToolName.CheckEdits)
    val js = t.rawJsonSchema.noSpaces
    assertEquals(t.name, "check_edits")
    assert(List("\"slug\"", "\"edits\"", "\"anchor\"", "\"body\"", "insertBefore").forall(js.contains), js)

  test("#958: the fixer and the compile writer answer coverage reasons; article_coverage takes a slug"):
    def js[A](s: ResponseSchema[A]) = sttp.apispec.circe.encoderSchema(s.schema).noSpaces
    val fixer = js(ResponseSchema.derived[com.histmersion.core.ReviewArticle.FixerAnswer]())
    assert(fixer.contains("\"reasons\"") && fixer.contains("\"citekey\"") && fixer.contains("an empty reason removes one"), fixer)
    val draft = js(ResponseSchema.derived[com.histmersion.core.CompileArticle.DraftAnswer]())
    assert(draft.contains("\"reasons\"") && draft.contains("you do NOT cite"), draft)
    val t = Toolbox(null, () => AgentUpstream.Offline).tool(ToolName.ArticleCoverage)
    assertEquals(t.name, "article_coverage")
    assert(t.rawJsonSchema.noSpaces.contains("\"slug\""), t.rawJsonSchema.noSpaces)
