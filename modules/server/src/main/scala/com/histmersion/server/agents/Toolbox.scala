package com.histmersion.server.agents

import com.histmersion.core.{AcquireMarkdown, ArticleMarkdown, Markdown, Query}
import com.histmersion.server.Handlers
import com.histmersion.server.McpTools.{FacetsInput, GetArticleInput, GetSourceInput, NeighborsInput, SearchInput}
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
  * `query` runs on the read-only role, `followups` never applies its edges. */
enum ToolName(val wire: String):
  case GetSource  extends ToolName("get_source")
  case Search     extends ToolName("search")
  case Facets     extends ToolName("facets")
  case Neighbors  extends ToolName("neighbors")
  case Query      extends ToolName("query")
  case GetArticle extends ToolName("get_article")
  case Followups  extends ToolName("followups")

final class Toolbox(h: Handlers, upstream: () => AgentUpstream):

  /** The tools for an allowlist, in its order. A step's allowlist is the ONLY way a tool reaches a
    * model: sttp-ai answers "Tool not found" for a call to anything not offered. */
  def tools(allow: List[ToolName]): List[AgentTool[Identity, ?]] = allow.distinct.map(tool)

  def tool(t: ToolName): AgentTool[Identity, ?] = t match
    case ToolName.GetSource =>
      AgentTool.fromFunction[GetSourceInput](t.wire,
        "Read one source of the research corpus by citekey. `section`: summary|findings|notes|toc|body|all " +
          "(default summary+findings+notes); `heading` returns only the body under one heading; " +
          "`offset`/`chars` slice the body.") { in =>
        h.sourceMarkdown(in.citekey, in.fullText, in.section, in.heading, in.offset, in.chars)
          .fold(e => s"No source '${in.citekey}': ${e.message}", identity)
      }
    case ToolName.Search =>
      AgentTool.fromFunction[SearchInput](t.wire,
        "Search the corpus (sources, with the findings that matched). `query` is free text; tags/topics " +
          "are ALL-of, authors/places ANY-of; from/to are signed years (BCE negative). Compact listing.") { in =>
        val f = in.toFilter
        Markdown.briefResults(h.sources(f), f.offset)
      }
    case ToolName.Facets =>
      AgentTool.fromFunction[FacetsInput](t.wire,
        "The vocabulary of one dimension — field = tags|authors|places|topics|status — with source counts. " +
          "Use it to pick existing tags/topics instead of inventing new ones; `prefix` narrows.") { in =>
        h.facets(in.field, in.prefix, in.toFilter)
          .fold(e => e.message, fs => Markdown.facets(in.field, fs.toVector))
      }
    case ToolName.Neighbors =>
      AgentTool.fromFunction[NeighborsInput](t.wire,
        "The typed edges to and from a source (references, challenges, refutes, updates, superseded_by), " +
          "with each neighbour's status. An empty result is NOT evidence of agreement: disagreement edges " +
          "are sparse.") { in =>
        val rows = h.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else Markdown.neighbors(in.citekey, rows.toVector)
      }
    case ToolName.Query =>
      AgentTool.fromFunction[QueryInput](t.wire,
        "One read-only SQL SELECT against the corpus database (Postgres/PostGIS) — for what search and " +
          "get_source cannot express, e.g. every cited source's status at once. Writes are refused.") { in =>
        h.ops.query(Query.QueryRequest(in.sql)).fold(e => e.message, Query.markdown)
      }
    case ToolName.GetArticle =>
      AgentTool.fromFunction[GetArticleInput](t.wire,
        "One compiled article by slug: body, summary, the sources it cites (title, year, status), " +
          "backlinks. `parts` = comma-separated body,summary,cited,backlinks.") { in =>
        h.getArticle(in.slug).fold(e => s"No article '${in.slug}': ${e.message}", v =>
          ArticleMarkdown.article(v, in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)))
      }
    case ToolName.Followups =>
      AgentTool.fromFunction[FollowupsInput](t.wire,
        "What came AFTER a paper: the papers citing it (by DOI or citekey), with citation intents and a " +
          "proposed edge kind each — how to find critiques the article ignores. Read only.") { in =>
        upstream().followups(in.id, in.since, in.limit).fold(m => s"followups unavailable: $m", AcquireMarkdown.followups)
      }

/** `followups` as an agent may call it: no `apply` — a model never writes edges. */
final case class FollowupsInput(id: String, since: Option[Int] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject
object FollowupsInput:
  given Schema[FollowupsInput] = KindlingsSchema.derived[FollowupsInput].schema
    .modify(_.id)(_.description("A citekey, DOI or arxiv:<id>"))
    .modify(_.since)(_.description("Only papers published from this year on"))
    .modify(_.limit)(_.description("At most this many citing papers"))
