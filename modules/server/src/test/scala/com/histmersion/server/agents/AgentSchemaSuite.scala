package com.histmersion
package server.agents
import com.histmersion.domain.automation.agents.SttpAiJson
import com.histmersion.domain.automation.agents.SttpAiJson.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.Toolbox

import com.histmersion.domain.automation.workflows.ToolName

import com.histmersion.api.automation.Agents.ProbeAnswer
import com.histmersion.domain.library.LibraryTools.GetSourceInput
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.ai.core.agent.{AgentTool, ResponseSchema}
import sttp.tapir.Schema
import com.histmersion.domain.EnrichSource
import com.histmersion.domain.research.workflows.ResearchAgentTools
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.CompileArticle
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Source

/** The JSON schemas sttp-ai sends to a provider come from the same Kindlings-derived Tapir schemas the MCP tools
  * advertise — no second schema stack.
  */
class AgentSchemaSuite extends munit.FunSuite:

  test("a typed answer's response schema renders from its Kindlings schema"):
    val js = SttpAiJson.schema(ResponseSchema.derived[ProbeAnswer]().schema).noSpaces
    assert(js.contains("\"title\""), js)
    assert(js.contains("\"citekey\""), js)

  test("a tool's input schema is the MCP tool's own"):
    val t = AgentTool.fromFunction[GetSourceInput]("get_source", "read")(_ => "")
    val js = t.rawJsonSchema.noSpaces
    assert(js.contains("\"citekey\""), js)
    assert(js.contains("source's citekey"), js) // the MCP description travels with it

  test("the review panel's answers render as response schemas, with the field descriptions the prompts rely on"):
    import com.histmersion.domain.ReviewArticle.*
    def js[A](s: ResponseSchema[A]) = SttpAiJson.schema(s.schema).noSpaces
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
    val js = SttpAiJson.schema(ResponseSchema.derived[EnrichSource.Findings]().schema).noSpaces
    assert(js.contains("\"placeKind\""), js)
    assert(List("site (", "region (", "polity (", "water (").forall(js.contains), js)

  test("followups as an agent tool has no apply flag"):
    val t = TestToolbox(null).tool(LibraryAgentTools.Followups).get
    assert(!t.rawJsonSchema.noSpaces.contains("apply"), t.rawJsonSchema.noSpaces)

  test("check_edits as an agent tool takes the MCP tool's input: slug, edits (op, anchor, text) or a body"):
    val t = TestToolbox(null).tool(ResearchAgentTools.CheckEdits).get
    val js = t.rawJsonSchema.noSpaces
    assertEquals(t.name, "check_edits")
    assert(List("\"slug\"", "\"edits\"", "\"anchor\"", "\"body\"", "insertBefore").forall(js.contains), js)

  test("#958: the fixer and the compile writer answer coverage reasons; article_coverage takes a slug"):
    def js[A](s: ResponseSchema[A]) = SttpAiJson.schema(s.schema).noSpaces
    val fixer = js(ResponseSchema.derived[ReviewArticle.FixerAnswer]())
    assert(
      fixer.contains("\"reasons\"") && fixer.contains("\"citekey\"") && fixer.contains("an empty reason removes one"),
      fixer
    )
    val draft = js(ResponseSchema.derived[CompileArticle.DraftAnswer]())
    assert(draft.contains("\"reasons\"") && draft.contains("you do NOT cite"), draft)
    val t = TestToolbox(null).tool(ResearchAgentTools.ArticleCoverage).get
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
      schema(ResearchAgentTools.CheckEdits).contains("\"anchor\""),
      "the $defs survive, only their titles go"
    )

  test("a property named title is kept"):
    val t = AgentTool.fromFunction[AgentSchemaSuite.Titled]("t", "d")(_ => "")
    val js = Toolbox.inputSchema(t)
    assert(js.cursor.downField("properties").downField("title").succeeded, js.noSpaces)
    assert(js.cursor.downField("title").succeeded == false, js.noSpaces)

  test("the agent search, facets and get_source take what steps use, mapped onto the same handlers"):
    val search = schema(LibraryAgentTools.Search)
    List("\"query\"", "\"topics\"", "\"status\"", "\"limit\"", "\"offset\"").foreach(k => assert(search.contains(k), k))
    List("groupBy", "minLon", "samples", "verbose", "asOf").foreach(k => assert(!search.contains(k), k))
    val f = AgentSearchInput(
      query = Some(Filter.Q("Hittite")),
      topics = List(Filter.Topics("a,b")),
      status = List(Filter.Status("contested")),
      from = Some(Year(-1600)),
      to = Some(Year(-1200)),
      limit = Some(Filter.Limit(30)),
      offset = Some(Filter.Offset(30))
    ).toFilter
    assertEquals(
      (f.q, f.topics, f.status, f.limit, f.offset),
      (Some("Hittite"), List("a", "b"), List("contested"), 30, 30)
    )
    assert(f.period.isDefined && f.bbox.isEmpty && f.places.isEmpty)
    assert(
      !schema(LibraryAgentTools.Facets).contains("minLon") && schema(
        LibraryAgentTools.Facets
      ).contains("\"prefix\"")
    )
    assert(!schema(LibraryAgentTools.GetSource).contains("fullText"))
    assertEquals(
      AgentGetSourceInput(Citekey.assume("x")).sectionOrBody,
      None,
      "no section: summary, findings and notes"
    )
    assertEquals(
      AgentGetSourceInput(Citekey.assume("x"), offset = Some(Offset(120))).sectionOrBody,
      Some("body"),
      "a slice reads the body"
    )
    assertEquals(
      AgentGetSourceInput(
        Citekey.assume("x"),
        section = Some(Source.Section.Wire("all")),
        offset = Some(Offset(120))
      ).sectionOrBody,
      Some("all")
    )

  test("an agent's arguments may leave out what has a default, and a stray one costs no turn (2026-10-16)"):
    // a Codex skeptic spent ~3 calls a step on "DecodingFailure at .tags: Missing required field"
    assertEquals(
      RawJson.decode[AgentSearchInput]("""{"topics":["dragon_slaying_myth"],"limit":50}"""),
      Right(AgentSearchInput(topics = List(Filter.Topics("dragon_slaying_myth")), limit = Some(Filter.Limit(50))))
    )
    assertEquals(
      RawJson.decode[AgentGetSourceInput]("""{"citekey":"x","fullText":true}"""),
      Right(AgentGetSourceInput(Citekey.assume("x")))
    )
    assertEquals(
      RawJson.decode[AgentFacetsInput]("""{"field":"tags"}"""),
      Right(AgentFacetsInput(AgentFacetsInput.Field("tags")))
    )
    assertEquals(RawJson.decode[FollowupsInput]("""{"id":"x"}"""), Right(FollowupsInput(FollowupsInput.Id("x"))))
    val t = box.tool(LibraryAgentTools.Search).get
    assert(
      SttpAiJson.arguments(t, Json.fromFields(List("topics" -> Json.fromValues(List(Json.fromString("a")))))).isRight,
      "the codec the agent endpoints decode with"
    )

object AgentSchemaSuite:
  final case class Titled(title: String) derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema
