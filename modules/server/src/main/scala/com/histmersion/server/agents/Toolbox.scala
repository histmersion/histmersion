package com.histmersion.server.agents

import com.histmersion.core.{AcquireMarkdown, ArticleMarkdown, Filter, Markdown, Query}
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
  * `/mcp` keeps the full descriptions. */
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

final class Toolbox(h: Handlers, upstream: () => AgentUpstream):

  /** The tools for an allowlist, in its order. A step's allowlist is the ONLY way a tool reaches a
    * model: sttp-ai answers "Tool not found" for a call to anything not offered. */
  def tools(allow: List[ToolName]): List[AgentTool[Identity, ?]] = allow.distinct.map(tool)

  def tool(t: ToolName): AgentTool[Identity, ?] = t match
    case ToolName.GetSource =>
      AgentTool.fromFunction[AgentGetSourceInput](t.wire,
        "Read one source by citekey: its summary, findings and notes, or the part `section` names.") { in =>
        h.sourceMarkdown(in.citekey, fullText = false, in.sectionOrBody, in.heading, in.offset, in.chars)
          .fold(e => s"No source '${in.citekey}': ${e.message}", identity)
      }
    case ToolName.Passages =>
      AgentTool.fromFunction[PassagesInput](t.wire,
        "Find a word or quotation in ONE source's full text: the passages around each match (in **bold**), " +
          "each with its body `offset` (get_source reads on from it) and heading. Literal, case- and " +
          "accent-insensitive.")(in => McpTools.passagesMarkdown(h, in))
    case ToolName.Search =>
      AgentTool.fromFunction[AgentSearchInput](t.wire,
        "Search the corpus: sources, compact, with the findings that matched.") { in =>
        val f = in.toFilter
        Markdown.briefResults(h.sources(f), f.offset)
      }
    case ToolName.Facets =>
      AgentTool.fromFunction[AgentFacetsInput](t.wire,
        "The existing values of one field, with source counts — pick from them instead of inventing new ones.") { in =>
        h.facets(in.field, in.prefix, FacetsInput(in.field, in.prefix).toFilter)
          .fold(e => e.message, fs => Markdown.facets(in.field, fs.toVector))
      }
    case ToolName.Neighbors =>
      AgentTool.fromFunction[NeighborsInput](t.wire,
        "A source's typed edges in and out, with each neighbour's status. No edge is NOT evidence of agreement.") { in =>
        val rows = h.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else Markdown.neighbors(in.citekey, rows.toVector)
      }
    case ToolName.Query =>
      AgentTool.fromFunction[QueryInput](t.wire,
        "One read-only SQL SELECT on the corpus database (Postgres) — e.g. every cited source's status at once.") { in =>
        h.ops.query(Query.QueryRequest(in.sql)).fold(e => e.message, Query.markdown)
      }
    case ToolName.GetArticle =>
      AgentTool.fromFunction[GetArticleInput](t.wire,
        "One compiled article by slug: body, summary, the generated coverage sections, the sources it cites " +
          "(title, year, status), backlinks. `parts` = comma-separated body,summary,coverage,cited,backlinks.") { in =>
        h.getArticle(in.slug).fold(e => s"No article '${in.slug}': ${e.message}", v =>
          ArticleMarkdown.article(v, in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)))
      }
    case ToolName.Followups =>
      AgentTool.fromFunction[FollowupsInput](t.wire,
        "The papers citing a source, each with its citation intents and a proposed edge kind — critiques the " +
          "article may ignore.") { in =>
        upstream().followups(in.id, in.since, in.limit).fold(m => s"followups unavailable: $m", AcquireMarkdown.followups)
      }
    case ToolName.CheckEdits =>
      AgentTool.fromFunction[CheckEditsInput](t.wire,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written.")(in => McpTools.checkEdits(h, in))
    case ToolName.ArticleCoverage =>
      AgentTool.fromFunction[ArticleCoverageInput](t.wire,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note.")(in =>
        McpTools.articleCoverageMarkdown(h, in))

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
  given Schema[AgentSearchInput] = KindlingsSchema.derived[AgentSearchInput].schema
    .modify(_.query)(_.description("Free text: words AND-ed, \"a phrase\", -word, or"))
    .modify(_.topics)(_.description("Every one required"))
    .modify(_.tags)(_.description("Every one required"))
    .modify(_.authors)(_.description("Any one, a substring of an author's name"))
    .modify(_.status)(_.description("Any of foundational|current|contested|superseded|debunked|retracted|unassessed"))
    .modify(_.from)(_.description("Period start, a signed year (BCE negative)"))
    .modify(_.to)(_.description("Period end, a signed year"))
    .modify(_.sort)(_.description("relevance|cited|period|year|title"))
    .modify(_.limit)(_.description("Results per page"))
    .modify(_.offset)(_.description("The next page's offset, from the listing's footer"))

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
