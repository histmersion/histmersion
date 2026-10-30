package com.histmersion.server.agents

import com.histmersion.api.{AcquireMarkdown, ArticleMarkdown, Markdown, Query}
import com.histmersion.config.AgentToolsConfig
import com.histmersion.server.Handlers
import com.histmersion.server.McpTools
import com.histmersion.server.McpTools.{
  ArticleCoverageInput,
  CheckEditsInput,
  FacetsInput,
  GetArticleInput,
  NeighborsInput,
  PassagesInput
}
import com.histmersion.server.OpsTools.QueryInput
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

final class Toolbox(h: Handlers, upstream: () => AgentUpstream, caps: => AgentToolsConfig = AgentToolsConfig.Off):
  import ToolResultCap.cap

  private def max = caps.resultMaxChars

  /** The body slice an agent reads: what it asked for, or `body-chars`, and never past the result cap. */
  private def bodyChars(asked: Option[Int]): Option[Int] =
    val want = asked.filter(_ > 0).orElse(Option.when(caps.bodyChars > 0)(caps.bodyChars))
    if max <= 0 then want else Some(math.min(want.getOrElse(max), math.max(500, max - 300)))

  /** The tools for an allowlist, in its order. A step's allowlist is the ONLY way a tool reaches a model: sttp-ai
    * answers "Tool not found" for a call to anything not offered.
    */
  def tools(allow: List[ToolName]): List[AgentTool[Identity, ?]] = allow.distinct.map(tool)

  def tool(t: ToolName): AgentTool[Identity, ?] = t match
    case ToolName.GetSource =>
      AgentTool.fromFunction[AgentGetSourceInput](
        t.wire,
        "Read one source by citekey: its summary, findings and notes, or the part `section` names."
      ) { in =>
        val section = in.sectionOrBody
        h.sourceMarkdown(in.citekey, fullText = false, section, in.heading, in.offset, bodyChars(in.chars))
          .fold(
            e => s"No source '${in.citekey}': ${e.message}",
            md =>
              section.map(_.toLowerCase) match
                case None | Some("all") => ToolResultCap.source(ToolResultCap.compactSource(md), max)
                case Some("body")       =>
                  cap(md, max, "read on with get_source section=\"body\" offset=… chars=…; a word: passages")
                case Some("toc") => cap(md, max, "read one part with get_source heading=…")
                case _           => cap(ToolResultCap.plainLinks(md), max, "a word in the source: passages")
          )
      }
    case ToolName.Passages =>
      AgentTool.fromFunction[PassagesInput](
        t.wire,
        "Find a word or quotation in ONE source's full text: the passages around each match (in **bold**), " +
          "each with its body `offset` (get_source reads on from it) and heading. Literal, case- and " +
          "accent-insensitive."
      )(in =>
        cap(
          McpTools.passagesMarkdown(h, in),
          max,
          "ask for fewer passages (`limit`), less `context`, or a more specific query"
        )
      )
    case ToolName.Search =>
      AgentTool.fromFunction[AgentSearchInput](
        t.wire,
        "Search the corpus: sources, compact, with the findings that matched."
      ) { in =>
        val f = in.toFilter
        cap(
          ToolResultCap.plainLinks(Markdown.briefResults(h.sources(f))),
          max,
          "ask for fewer results (`limit`) or narrow the filters"
        )
      }
    case ToolName.Facets =>
      AgentTool.fromFunction[AgentFacetsInput](
        t.wire,
        "The existing values of one field, with source counts — pick from them instead of inventing new ones."
      ) { in =>
        h.facets(in.field, in.prefix, FacetsInput(in.field, in.prefix).toFilter)
          .fold(e => e.message, fs => cap(Markdown.facets(in.field, fs.toVector), max, "narrow it with `prefix`"))
      }
    case ToolName.Neighbors =>
      AgentTool.fromFunction[NeighborsInput](
        t.wire,
        "A source's typed edges in and out, with each neighbour's status. No edge is NOT evidence of agreement."
      ) { in =>
        val rows = h.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else cap(Markdown.neighbors(in.citekey, rows.toVector), max, "ask for one edge kind (`kinds`)")
      }
    case ToolName.Query =>
      AgentTool.fromFunction[QueryInput](
        t.wire,
        "One read-only SQL SELECT on the corpus database (Postgres) — e.g. every cited source's status at once."
      ) { in =>
        h.ops
          .query(Query.QueryRequest(in.sql))
          .fold(e => e.message, r => ToolResultCap.queryResult(r, caps.queryMaxRows, max))
      }
    case ToolName.GetArticle =>
      AgentTool.fromFunction[GetArticleInput](
        t.wire,
        "One compiled article by slug: body, summary, the generated coverage sections, the sources it cites " +
          "(title, year, status), backlinks. `parts` = comma-separated body,summary,coverage,cited,backlinks."
      ) { in =>
        h.getArticle(in.slug)
          .fold(
            e => s"No article '${in.slug}': ${e.message}",
            v =>
              cap(
                ArticleMarkdown.article(v, in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)),
                max,
                "ask for fewer `parts`"
              )
          )
      }
    case ToolName.Followups =>
      AgentTool.fromFunction[FollowupsInput](
        t.wire,
        "The papers citing a source, each with its citation intents and a proposed edge kind — critiques the " +
          "article may ignore."
      ) { in =>
        upstream()
          .followups(in.id, in.since, in.limit)
          .fold(
            m => s"followups unavailable: $m",
            r => cap(AcquireMarkdown.followups(r), max, "ask for fewer papers (`limit`) or recent ones (`since`)")
          )
      }
    case ToolName.CheckEdits =>
      AgentTool.fromFunction[CheckEditsInput](
        t.wire,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written."
      )(in => cap(McpTools.checkEdits(h, in), max, "dry-run fewer edits at once"))
    case ToolName.ArticleCoverage =>
      AgentTool.fromFunction[ArticleCoverageInput](
        t.wire,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note."
      )(in => cap(McpTools.articleCoverageMarkdown(h, in), max, "read one source's record with get_source"))

object Toolbox:
  /** A tool's input schema as `/mcp/agent/<agent>` lists it, and so as every turn of a step re-sends it. */
  def inputSchema(t: AgentTool[Identity, ?]): io.circe.Json =
    import sttp.apispec.circe.*
    import io.circe.syntax.*
    def untitled(j: io.circe.Json) = j.mapObject(_.remove("title"))
    untitled(t.jsonSchema.asJson.deepDropNullValues).mapObject { o =>
      val top = o.remove("$schema")
      top("$defs")
        .flatMap(_.asObject)
        .fold(top)(ds => top.add("$defs", io.circe.Json.fromJsonObject(ds.mapValues(untitled))))
    }

/** Scala defaults hold when an agent's arguments are decoded — `search {"topics":[…]}` needs no `tags: []` (without it
  * every list was required, and a Codex skeptic step spent ~3 calls a step on "Missing required field", 2026-10-16).
  * Not strict: a stray argument is ignored rather than costing a turn.
  */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults
