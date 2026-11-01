package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.ToolResultCap

import com.histmersion.api.{AcquireMarkdown, ArticleMarkdown, Markdown, Query}
import com.histmersion.config.AgentToolsConfig
import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.server.Handlers
import com.histmersion.server.McpTools
import com.histmersion.server.McpTools.{ArticleCoverageInput, CheckEditsInput, GetArticleInput}
import com.histmersion.domain.library.LibraryTools.{FacetsInput, NeighborsInput, PassagesInput}
import com.histmersion.domain.library.search.QueryTools.QueryInput
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** The agent tools today's library and research code offers the engine, registered through this transition adapter
  * until those domains contribute their own (each a thin, typed wrapper over the same [[Handlers]] method REST and MCP
  * call, returning the same Markdown — in process, no HTTP, no second schema: the input types are the MCP tools' own
  * Kindlings-derived case classes).
  *
  * Only READ tools exist here, on purpose. Every write an agent run makes is made by workflow CODE from a validated
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
final class TransitionTools(h: Handlers, upstream: () => AgentUpstream, caps: => AgentToolsConfig):
  import ToolResultCap.cap

  private def max = caps.resultMaxChars

  /** The body slice an agent reads: what it asked for, or `body-chars`, and never past the result cap. */
  private def bodyChars(asked: Option[Int]): Option[Int] =
    val want = asked.filter(_ > 0).orElse(Option.when(caps.bodyChars > 0)(caps.bodyChars))
    if max <= 0 then want else Some(math.min(want.getOrElse(max), math.max(500, max - 300)))

  private def contribution(n: ToolName)(t: => AgentTool[Identity, ?]): ToolContribution = new ToolContribution:
    val name: ToolName = n
    val agentTool: AgentTool[Identity, ?] = t

  /** Every tool, in the order the engine's toolbox has always listed them. */
  def contributions: List[ToolContribution] = List(
    contribution(TransitionTools.GetSource) {
      AgentTool.fromFunction[AgentGetSourceInput](
        TransitionTools.GetSource.value,
        "Read one source by citekey: its summary, findings and notes, or the part `section` names."
      ) { in =>
        val section = in.sectionOrBody
        h.library.sources
          .markdown(in.citekey, fullText = false, section, in.heading, in.offset, bodyChars(in.chars))
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
    },
    contribution(TransitionTools.Passages) {
      AgentTool.fromFunction[PassagesInput](
        TransitionTools.Passages.value,
        "Find a word or quotation in ONE source's full text: the passages around each match (in **bold**), " +
          "each with its body `offset` (get_source reads on from it) and heading. Literal, case- and " +
          "accent-insensitive."
      )(in =>
        cap(
          com.histmersion.domain.library.LibraryTools.passagesMarkdown(h.library.sources, in),
          max,
          "ask for fewer passages (`limit`), less `context`, or a more specific query"
        )
      )
    },
    contribution(TransitionTools.Search) {
      AgentTool.fromFunction[AgentSearchInput](
        TransitionTools.Search.value,
        "Search the corpus: sources, compact, with the findings that matched."
      ) { in =>
        val f = in.toFilter
        cap(
          ToolResultCap.plainLinks(Markdown.briefResults(h.library.search.sources(f))),
          max,
          "ask for fewer results (`limit`) or narrow the filters"
        )
      }
    },
    contribution(TransitionTools.Facets) {
      AgentTool.fromFunction[AgentFacetsInput](
        TransitionTools.Facets.value,
        "The existing values of one field, with source counts — pick from them instead of inventing new ones."
      ) { in =>
        h.library.labels
          .facets(in.field, in.prefix, FacetsInput(in.field, in.prefix).toFilter)
          .fold(e => e.message, fs => cap(Markdown.facets(in.field, fs.toVector), max, "narrow it with `prefix`"))
      }
    },
    contribution(TransitionTools.Neighbors) {
      AgentTool.fromFunction[NeighborsInput](
        TransitionTools.Neighbors.value,
        "A source's typed edges in and out, with each neighbour's status. No edge is NOT evidence of agreement."
      ) { in =>
        val rows = h.library.citations.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else cap(Markdown.neighbors(in.citekey, rows.toVector), max, "ask for one edge kind (`kinds`)")
      }
    },
    contribution(TransitionTools.Query) {
      AgentTool.fromFunction[QueryInput](
        TransitionTools.Query.value,
        "One read-only SQL SELECT on the corpus database (Postgres) — e.g. every cited source's status at once."
      ) { in =>
        h.library.query
          .run(Query.QueryRequest(in.sql))
          .fold(e => e.message, r => TransitionTools.queryResult(r, caps.queryMaxRows, max))
      }
    },
    contribution(TransitionTools.GetArticle) {
      AgentTool.fromFunction[GetArticleInput](
        TransitionTools.GetArticle.value,
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
    },
    contribution(TransitionTools.Followups) {
      AgentTool.fromFunction[FollowupsInput](
        TransitionTools.Followups.value,
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
    },
    contribution(TransitionTools.CheckEdits) {
      AgentTool.fromFunction[CheckEditsInput](
        TransitionTools.CheckEdits.value,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written."
      )(in => cap(McpTools.checkEdits(h, in), max, "dry-run fewer edits at once"))
    },
    contribution(TransitionTools.ArticleCoverage) {
      AgentTool.fromFunction[ArticleCoverageInput](
        TransitionTools.ArticleCoverage.value,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note."
      )(in => cap(McpTools.articleCoverageMarkdown(h, in), max, "read one source's record with get_source"))
    }
  )

object TransitionTools:

  /** A `query` result as an agent sees it: at most `maxRows` rows, marked as cut (the row cap's own flag), then
    * [[cap]]ped like any result.
    */
  def queryResult(r: com.histmersion.api.Query.QueryResult, maxRows: Int, max: Int): String =
    val shown = if maxRows > 0 && r.rows.size > maxRows then
      r.copy(rows = r.rows.take(maxRows), rowCount = maxRows, truncated = true)
    else r
    val more = if shown.rows.size < r.rows.size then
      s"\n\n_…${r.rows.size - shown.rows.size}${
          if r.truncated then "+" else ""
        } more rows not shown — select only the rows you need " +
        "(`WHERE citekey IN (…)`, `LIMIT`), never a whole table_"
    else ""
    ToolResultCap.cap(
      com.histmersion.api.Query.markdown(shown) + more,
      max,
      "select fewer columns or rows (`WHERE … IN (…)`, `LIMIT`)"
    )

  val GetSource: ToolName = ToolName("get_source")
  val Passages: ToolName = ToolName("passages")
  val Search: ToolName = ToolName("search")
  val Facets: ToolName = ToolName("facets")
  val Neighbors: ToolName = ToolName("neighbors")
  val Query: ToolName = ToolName("query")
  val GetArticle: ToolName = ToolName("get_article")
  val Followups: ToolName = ToolName("followups")
  val CheckEdits: ToolName = ToolName("check_edits")
  val ArticleCoverage: ToolName = ToolName("article_coverage")

  /** The names, in the order of [[TransitionTools.contributions]]: what the definitions may list before a server runs.
    */
  val names: List[ToolName] =
    List(GetSource, Passages, Search, Facets, Neighbors, Query, GetArticle, Followups, CheckEdits, ArticleCoverage)

/** Scala defaults hold when an agent's arguments are decoded — `search {"topics":[…]}` needs no `tags: []` (without it
  * every list was required, and a Codex skeptic step spent ~3 calls a step on "Missing required field", 2026-10-16).
  * Not strict: a stray argument is ignored rather than costing a turn.
  */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults
