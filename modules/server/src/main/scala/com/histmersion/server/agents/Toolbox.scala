package com.histmersion.server.agents

import com.histmersion.api.{AcquireMarkdown, ArticleMarkdown, Filter, FilterDocs, Markdown, Query}
import com.histmersion.config.AgentToolsConfig
import com.histmersion.server.Handlers
import com.histmersion.server.McpTools
import com.histmersion.server.McpTools.{ArticleCoverageInput, CheckEditsInput, FacetsInput, GetArticleInput, NeighborsInput, PassagesInput}
import com.histmersion.server.OpsTools.QueryInput
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity
import sttp.tapir.Schema

/** The tools an LLM step may be OFFERED. Each is a thin, typed wrapper over the same [[Handlers]]
  * method REST and MCP call, returning the same Markdown — in process, no HTTP, no second schema:
  * the input types are the MCP tools' own Kindlings-derived case classes.
  *
  * Only READ tools exist here, on purpose. Every write an agent run makes is made by pipeline CODE
  * from a validated typed answer (docs/research-embedded-agents.md §3, "Security"), so a prompt
  * injected through an OCR'd body or an article has nothing to call that changes the corpus.
  * `query` runs on the read-only role, `followups` never applies its edges, `check_edits` only
  * dry-runs a change.
  *
  * Every turn of a step re-sends its tools' definitions, so an agent tool is described for an agent:
  * the parameters steps use (traced runs, 2026-10-09), in a sentence each. Where the MCP tool takes
  * more (`search`'s grouping and bounding box, `facets`' filter, `get_source`'s `fullText`), the
  * agent variant is its own Kindlings-derived input, mapped onto the same handler; the person's own
  * `/mcp` keeps the full descriptions.
  *
  * What a call returns is bounded by `agents.tools` ([[ToolResultCap]]): every later turn of the step
  * re-sends it, so a long result is cut with a trailer saying how to read on, a source record arrives
  * without its UI links, a body slice is short unless asked otherwise, and a `query` shows few rows. */
enum ToolName(val wire: String):
  case GetSource  extends ToolName("get_source")
  case Passages   extends ToolName("passages")
  case Search     extends ToolName("search")
  case Facets     extends ToolName("facets")
  case Neighbors  extends ToolName("neighbors")
  case Query      extends ToolName("query")
  case GetArticle extends ToolName("get_article")
  case Followups  extends ToolName("followups")
  case CheckEdits extends ToolName("check_edits")
  case ArticleCoverage extends ToolName("article_coverage")

final class Toolbox(h: Handlers, upstream: () => AgentUpstream, caps: => AgentToolsConfig = AgentToolsConfig.Off):
  import ToolResultCap.cap

  private def max = caps.resultMaxChars

  /** The body slice an agent reads: what it asked for, or `body-chars`, and never past the result cap. */
  private def bodyChars(asked: Option[Int]): Option[Int] =
    val want = asked.filter(_ > 0).orElse(Option.when(caps.bodyChars > 0)(caps.bodyChars))
    if max <= 0 then want else Some(math.min(want.getOrElse(max), math.max(500, max - 300)))

  /** The tools for an allowlist, in its order. A step's allowlist is the ONLY way a tool reaches a
    * model: sttp-ai answers "Tool not found" for a call to anything not offered. */
  def tools(allow: List[ToolName]): List[AgentTool[Identity, ?]] = allow.distinct.map(tool)

  def tool(t: ToolName): AgentTool[Identity, ?] = t match
    case ToolName.GetSource =>
      AgentTool.fromFunction[AgentGetSourceInput](t.wire,
        "Read one source by citekey: its summary, findings and notes, or the part `section` names.") { in =>
        val section = in.sectionOrBody
        h.sourceMarkdown(in.citekey, fullText = false, section, in.heading, in.offset, bodyChars(in.chars))
          .fold(e => s"No source '${in.citekey}': ${e.message}", md =>
            section.map(_.toLowerCase) match
              case None | Some("all") => ToolResultCap.source(ToolResultCap.compactSource(md), max)
              case Some("body")       => cap(md, max, "read on with get_source section=\"body\" offset=… chars=…; a word: passages")
              case Some("toc")        => cap(md, max, "read one part with get_source heading=…")
              case _                  => cap(ToolResultCap.plainLinks(md), max, "a word in the source: passages"))
      }
    case ToolName.Passages =>
      AgentTool.fromFunction[PassagesInput](t.wire,
        "Find a word or quotation in ONE source's full text: the passages around each match (in **bold**), " +
          "each with its body `offset` (get_source reads on from it) and heading. Literal, case- and " +
          "accent-insensitive.")(in => cap(McpTools.passagesMarkdown(h, in), max,
            "ask for fewer passages (`limit`), less `context`, or a more specific query"))
    case ToolName.Search =>
      AgentTool.fromFunction[AgentSearchInput](t.wire,
        "Search the corpus: sources, compact, with the findings that matched.") { in =>
        val f = in.toFilter
        cap(ToolResultCap.plainLinks(Markdown.briefResults(h.sources(f))), max,
          "ask for fewer results (`limit`) or narrow the filters")
      }
    case ToolName.Facets =>
      AgentTool.fromFunction[AgentFacetsInput](t.wire,
        "The existing values of one field, with source counts — pick from them instead of inventing new ones.") { in =>
        h.facets(in.field, in.prefix, FacetsInput(in.field, in.prefix).toFilter)
          .fold(e => e.message, fs => cap(Markdown.facets(in.field, fs.toVector), max, "narrow it with `prefix`"))
      }
    case ToolName.Neighbors =>
      AgentTool.fromFunction[NeighborsInput](t.wire,
        "A source's typed edges in and out, with each neighbour's status. No edge is NOT evidence of agreement.") { in =>
        val rows = h.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else cap(Markdown.neighbors(in.citekey, rows.toVector), max, "ask for one edge kind (`kinds`)")
      }
    case ToolName.Query =>
      AgentTool.fromFunction[QueryInput](t.wire,
        "One read-only SQL SELECT on the corpus database (Postgres) — e.g. every cited source's status at once.") { in =>
        h.ops.query(Query.QueryRequest(in.sql)).fold(e => e.message, r => ToolResultCap.queryResult(r, caps.queryMaxRows, max))
      }
    case ToolName.GetArticle =>
      AgentTool.fromFunction[GetArticleInput](t.wire,
        "One compiled article by slug: body, summary, the generated coverage sections, the sources it cites " +
          "(title, year, status), backlinks. `parts` = comma-separated body,summary,coverage,cited,backlinks.") { in =>
        h.getArticle(in.slug).fold(e => s"No article '${in.slug}': ${e.message}", v =>
          cap(ArticleMarkdown.article(v, in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)), max,
            "ask for fewer `parts`"))
      }
    case ToolName.Followups =>
      AgentTool.fromFunction[FollowupsInput](t.wire,
        "The papers citing a source, each with its citation intents and a proposed edge kind — critiques the " +
          "article may ignore.") { in =>
        upstream().followups(in.id, in.since, in.limit).fold(m => s"followups unavailable: $m", r =>
          cap(AcquireMarkdown.followups(r), max, "ask for fewer papers (`limit`) or recent ones (`since`)"))
      }
    case ToolName.CheckEdits =>
      AgentTool.fromFunction[CheckEditsInput](t.wire,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written.")(in =>
          cap(McpTools.checkEdits(h, in), max, "dry-run fewer edits at once"))
    case ToolName.ArticleCoverage =>
      AgentTool.fromFunction[ArticleCoverageInput](t.wire,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note.")(in =>
        cap(McpTools.articleCoverageMarkdown(h, in), max, "read one source's record with get_source"))

object Toolbox:
  /** A tool's input schema as `/mcp/agent/<agent>` lists it, and so as every turn of a step re-sends it. */
  def inputSchema(t: AgentTool[Identity, ?]): io.circe.Json =
    import sttp.apispec.circe.*
    import io.circe.syntax.*
    def untitled(j: io.circe.Json) = j.mapObject(_.remove("title"))
    untitled(t.jsonSchema.asJson.deepDropNullValues).mapObject { o =>
      val top = o.remove("$schema")
      top("$defs").flatMap(_.asObject).fold(top)(ds => top.add("$defs", io.circe.Json.fromJsonObject(ds.mapValues(untitled))))
    }

/** Scala defaults hold when an agent's arguments are decoded — `search {"topics":[…]}` needs no `tags: []`
  * (without it every list was required, and a Codex skeptic step spent ~3 calls a step on "Missing
  * required field", 2026-10-16). Not strict: a stray argument is ignored rather than costing a turn. */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults

/** `get_source` as an agent calls it: no `fullText` (`section: "body"` says the same), and a body
  * slice (`offset`, `chars`, `heading`) without a section reads the body — the MCP tool would answer
  * the summary and ignore the slice. */
final case class AgentGetSourceInput(citekey: String, section: Option[String] = None, heading: Option[String] = None,
    offset: Option[Int] = None, chars: Option[Int] = None) derives KindlingsCodecAsObject:
  def sectionOrBody: Option[String] =
    section.map(_.trim).filter(_.nonEmpty).orElse(Option.when(offset.isDefined || chars.isDefined || heading.isDefined)("body"))
object AgentGetSourceInput:
  given Schema[AgentGetSourceInput] = KindlingsSchema.derived[AgentGetSourceInput].schema
    .modify(_.section)(_.description("summary|findings|notes|toc|body|all (default: summary+findings+notes)"))
    .modify(_.heading)(_.description("Only the body under this heading (prefix match)"))
    .modify(_.offset)(_.description("Body slice start (a character offset, e.g. from passages)"))
    .modify(_.chars)(_.description("Body slice length"))

/** `search` as an agent calls it: the filters steps use, mapped onto the same [[Filter]]. */
final case class AgentSearchInput(query: Option[String] = None, topics: List[String] = Nil, tags: List[String] = Nil,
    authors: List[String] = Nil, status: List[String] = Nil, from: Option[Long] = None, to: Option[Long] = None,
    sort: Option[String] = None, limit: Option[Int] = None, offset: Option[Int] = None) derives KindlingsCodecAsObject:
  def toFilter: Filter =
    Filter.of(query, tags, authors, Nil, topics, status, None, None, from, to, None, None, None, None, sort, limit, offset)
object AgentSearchInput:
  // the agent's few words per filter field are FilterDocs' `brief` (the MCP tool and the CLI read its `doc`)
  given Schema[AgentSearchInput] = FilterDocs.describeBrief(KindlingsSchema.derived[AgentSearchInput].schema)

/** `facets` as an agent calls it: one field's values, optionally by prefix. */
final case class AgentFacetsInput(field: String, prefix: Option[String] = None) derives KindlingsCodecAsObject
object AgentFacetsInput:
  given Schema[AgentFacetsInput] = KindlingsSchema.derived[AgentFacetsInput].schema
    .modify(_.field)(_.description("tags|authors|places|topics|status"))
    .modify(_.prefix)(_.description("Only values starting with this"))

/** `followups` as an agent may call it: no `apply` — a model never writes edges. */
final case class FollowupsInput(id: String, since: Option[Int] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject
object FollowupsInput:
  given Schema[FollowupsInput] = KindlingsSchema.derived[FollowupsInput].schema
    .modify(_.id)(_.description("A citekey, DOI or arxiv:<id>"))
    .modify(_.since)(_.description("Only papers published from this year on"))
    .modify(_.limit)(_.description("At most this many citing papers"))
