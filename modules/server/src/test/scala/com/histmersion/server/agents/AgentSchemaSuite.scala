package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.Toolbox

import com.histmersion.domain.automation.workflows.ToolName

import com.histmersion.api.Agents.ProbeAnswer
import com.histmersion.domain.library.LibraryTools.GetSourceInput
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.ai.core.agent.{AgentTool, ResponseSchema}
import sttp.tapir.Schema

/** The JSON schemas sttp-ai sends to a provider come from the same Kindlings-derived Tapir schemas the MCP tools
  * advertise — no second schema stack.
  */
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
    import com.histmersion.domain.ReviewArticle.*
    def js[A](s: ResponseSchema[A]) = sttp.apispec.circe.encoderSchema(s.schema).noSpaces
    val skeptic = js(ResponseSchema.derived[SkepticAnswer]())
    assert(skeptic.contains("\"proposals\"") && skeptic.contains("superseded_by"), skeptic)
    val fixer = js(ResponseSchema.derived[FixerAnswer]())
    // the fixer answers with edits, never a whole body
    assert(
      fixer.contains("\"edits\"") && fixer.contains("\"anchor\"") && fixer.contains("insertAfter") && fixer.contains(
        "copied EXACTLY"
      ),
      fixer
    )
    assert(!fixer.contains("\"body\""), fixer)
    val cold = js(ResponseSchema.derived[ColdReaderAnswer]())
    assert(
      cold.contains("\"explainBack\"") && !cold.contains("\"evidence\""),
      "the cold reader cites no records: " + cold
    )
    val rules = js(ResponseSchema.derived[RulesAnswer]())
    assert(rules.contains("\"spotChecks\"") && rules.contains("\"recordQuote\""))
    // a rules or skeptic finding carries its records: citekey, offset, quote
    for s <- List(rules, skeptic) do
      assert(s.contains("\"evidence\"") && s.contains("\"offset\"") && s.contains("\"citekey\""), s)

  test("#981: the findings answer carries placeKind, with the kinds the geocoder understands"):
    val js = sttp.apispec.circe
      .encoderSchema(ResponseSchema.derived[com.histmersion.domain.EnrichSource.Findings]().schema)
      .noSpaces
    assert(js.contains("\"placeKind\""), js)
    assert(List("site (", "region (", "polity (", "water (").forall(js.contains), js)

  test("followups as an agent tool has no apply flag"):
    val t = TestToolbox(null).tool(com.histmersion.domain.library.workflows.LibraryAgentTools.Followups).get
    assert(!t.rawJsonSchema.noSpaces.contains("apply"), t.rawJsonSchema.noSpaces)

  test("check_edits as an agent tool takes the MCP tool's input: slug, edits (op, anchor, text) or a body"):
    val t = TestToolbox(null).tool(com.histmersion.domain.research.workflows.ResearchAgentTools.CheckEdits).get
    val js = t.rawJsonSchema.noSpaces
    assertEquals(t.name, "check_edits")
    assert(List("\"slug\"", "\"edits\"", "\"anchor\"", "\"body\"", "insertBefore").forall(js.contains), js)

  test("#958: the fixer and the compile writer answer coverage reasons; article_coverage takes a slug"):
    def js[A](s: ResponseSchema[A]) = sttp.apispec.circe.encoderSchema(s.schema).noSpaces
    val fixer = js(ResponseSchema.derived[com.histmersion.domain.ReviewArticle.FixerAnswer]())
    assert(
      fixer.contains("\"reasons\"") && fixer.contains("\"citekey\"") && fixer.contains("an empty reason removes one"),
      fixer
    )
    val draft = js(ResponseSchema.derived[com.histmersion.domain.CompileArticle.DraftAnswer]())
    assert(draft.contains("\"reasons\"") && draft.contains("you do NOT cite"), draft)
    val t = TestToolbox(null).tool(com.histmersion.domain.research.workflows.ResearchAgentTools.ArticleCoverage).get
    assertEquals(t.name, "article_coverage")
    assert(t.rawJsonSchema.noSpaces.contains("\"slug\""), t.rawJsonSchema.noSpaces)

  // ── the agent tools ────────────────────────────────────────────────────────────────────────────

  private val box = TestToolbox(null)
  private def schema(t: ToolName) = Toolbox.inputSchema(box.tool(t).get).noSpaces

  test("an agent tool's schema carries no $schema marker and no type titles"):
    ToolboxOrder.names.foreach { t =>
      val js = schema(t)
      assert(!js.contains("$schema") && !js.contains("\"title\":\""), s"${t.value}: $js")
    }
    assert(
      schema(com.histmersion.domain.research.workflows.ResearchAgentTools.CheckEdits).contains("\"anchor\""),
      "the $defs survive, only their titles go"
    )

  test("a property named title is kept"):
    val t = AgentTool.fromFunction[AgentSchemaSuite.Titled]("t", "d")(_ => "")
    val js = Toolbox.inputSchema(t)
    assert(js.hcursor.downField("properties").downField("title").succeeded, js.noSpaces)
    assert(js.hcursor.downField("title").failed, js.noSpaces)

  test("the agent search, facets and get_source take what steps use, mapped onto the same handlers"):
    val search = schema(com.histmersion.domain.library.workflows.LibraryAgentTools.Search)
    List("\"query\"", "\"topics\"", "\"status\"", "\"limit\"", "\"offset\"").foreach(k => assert(search.contains(k), k))
    List("groupBy", "minLon", "samples", "verbose", "asOf").foreach(k => assert(!search.contains(k), k))
    val f = AgentSearchInput(
      query = Some("Hittite"),
      topics = List("a,b"),
      status = List("contested"),
      from = Some(-1600),
      to = Some(-1200),
      limit = Some(30),
      offset = Some(30)
    ).toFilter
    assertEquals(
      (f.q, f.topics, f.status, f.limit, f.offset),
      (Some("Hittite"), List("a", "b"), List("contested"), 30, 30)
    )
    assert(f.period.isDefined && f.bbox.isEmpty && f.places.isEmpty)
    assert(
      !schema(com.histmersion.domain.library.workflows.LibraryAgentTools.Facets).contains("minLon") && schema(
        com.histmersion.domain.library.workflows.LibraryAgentTools.Facets
      ).contains("\"prefix\"")
    )
    assert(!schema(com.histmersion.domain.library.workflows.LibraryAgentTools.GetSource).contains("fullText"))
    assertEquals(AgentGetSourceInput("x").sectionOrBody, None, "no section: summary, findings and notes")
    assertEquals(AgentGetSourceInput("x", offset = Some(120)).sectionOrBody, Some("body"), "a slice reads the body")
    assertEquals(AgentGetSourceInput("x", section = Some("all"), offset = Some(120)).sectionOrBody, Some("all"))

  test("an agent's arguments may leave out what has a default, and a stray one costs no turn (2026-10-16)"):
    import io.circe.parser.decode
    // a Codex skeptic spent ~3 calls a step on "DecodingFailure at .tags: Missing required field"
    assertEquals(
      decode[AgentSearchInput]("""{"topics":["dragon_slaying_myth"],"limit":50}"""),
      Right(AgentSearchInput(topics = List("dragon_slaying_myth"), limit = Some(50)))
    )
    assertEquals(decode[AgentGetSourceInput]("""{"citekey":"x","fullText":true}"""), Right(AgentGetSourceInput("x")))
    assertEquals(decode[AgentFacetsInput]("""{"field":"tags"}"""), Right(AgentFacetsInput("tags")))
    assertEquals(decode[FollowupsInput]("""{"id":"x"}"""), Right(FollowupsInput("x")))
    val t = box.tool(com.histmersion.domain.library.workflows.LibraryAgentTools.Search).get
    assert(
      t.codec.decodeJson(io.circe.Json.obj("topics" -> io.circe.Json.arr(io.circe.Json.fromString("a")))).isRight,
      "the codec the agent endpoints decode with"
    )

object AgentSchemaSuite:
  final case class Titled(title: String) derives KindlingsCodecAsObject, KindlingsSchema
  object Titled:
    given Schema[Titled] = summon[KindlingsSchema[Titled]].schema
