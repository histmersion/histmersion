package com.histmersion.server.agents

import com.histmersion.api.{Markdown, Query}
import com.histmersion.server.Handlers
import com.histmersion.api.VocabularyCodecs.given

/** The tools an LLM step may be OFFERED. Each is a thin, typed wrapper over the same [[Handlers]] method REST and MCP
  * call, returning the same Markdown — in process, no HTTP, no second schema: the input types are the MCP tools' own
  * Kindlings-derived case classes.
  *
  * Only READ tools exist here, on purpose. Every write an agent run makes is made by pipeline CODE from a validated
  * typed answer (docs/research-embedded-agents.md §3, "Security"), so a prompt injected through an OCR'd body or an
  * article has nothing to call that changes the corpus. `query` runs on the read-only role, `followups` never applies
  * its edges, `check_edits` only dry-runs a change.
  *
  * Every turn of a step re-sends its tools' definitions, so an agent tool is described for an agent: the parameters
  * steps use (traced runs, 2026-10-09), in a sentence each. Where the MCP tool takes more (`search`'s grouping and
  * bounding box, `facets`' filter, `get_source`'s `fullText`), the agent variant is its own Kindlings-derived input,
  * mapped onto the same handler; the person's own `/mcp` keeps the full descriptions.
  *
  * What a call returns is bounded by `agents.tools` ([[ToolResultCap]]): every later turn of the step re-sends it, so a
  * long result is cut with a trailer saying how to read on, a source record arrives without its UI links, a body slice
  * is short unless asked otherwise, and a `query` shows few rows.
  */
enum ToolName(val wire: String):
  case GetSource extends ToolName("get_source")
  case Passages extends ToolName("passages")
  case Search extends ToolName("search")
  case Facets extends ToolName("facets")
  case Neighbors extends ToolName("neighbors")
  case Query extends ToolName("query")
  case GetArticle extends ToolName("get_article")
  case Followups extends ToolName("followups")
  case CheckEdits extends ToolName("check_edits")
  case ArticleCoverage extends ToolName("article_coverage")
