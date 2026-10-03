package com.histmersion.server.agents

import com.histmersion.core.Markdown
import com.histmersion.server.Handlers
import com.histmersion.server.McpTools.{FacetsInput, GetSourceInput, SearchInput}
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** The tools an LLM step may be OFFERED. Each is a thin, typed wrapper over the same [[Handlers]]
  * method REST and MCP call, returning the same Markdown — in process, no HTTP, no second schema:
  * the input types are the MCP tools' own Kindlings-derived case classes.
  *
  * Only READ tools exist here, on purpose. Every write an agent run makes is made by pipeline CODE
  * from a validated typed answer (docs/research-embedded-agents.md §3, "Security"), so a prompt
  * injected through an OCR'd body has nothing to call that changes the corpus. */
enum ToolName(val wire: String):
  case GetSource extends ToolName("get_source")
  case Search    extends ToolName("search")
  case Facets    extends ToolName("facets")

final class Toolbox(h: Handlers):

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

