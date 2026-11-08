package com.histmersion
package domain.library.workflows

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown

import com.histmersion.domain.automation.agents.ToolResultCap

import com.histmersion.api.Markdown
import com.histmersion.api.library.{AcquireMarkdown, Query}
import com.histmersion.config.AgentToolsConfig
import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.library.LibraryTools.{FacetsInput, NeighborsInput, PassagesInput}
import com.histmersion.domain.library.search.QueryTools.QueryInput
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.library.LibraryTools
import com.histmersion.api.library.Facet
import com.histmersion.api.Api
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.api.library.Citekey

/** The agent tools the library offers the engine: a source, its passages, the search, the facets, a source's edges, the
  * read-only query and a source's follow-ups — each a thin, typed wrapper over the same controller REST and MCP call,
  * returning the same Markdown (in process, no HTTP, no second schema: the input types are the MCP tools' own
  * Kindlings-derived case classes, or an agent's own where the agent is shown fewer parameters).
  *
  * Only READ tools exist here, on purpose: every write an agent run makes is made by workflow CODE from a validated
  * typed answer, so a prompt injected through an OCR'd body has nothing to call that changes the corpus. `query` runs
  * on the read-only role, `followups` never applies its edges.
  *
  * What a call returns is bounded by `agents.tools` ([[ToolResultCap]]): every later turn of the step re-sends it, so a
  * long result is cut with a trailer saying how to read on, a source record arrives without its UI links, a body slice
  * is short unless asked otherwise, and a `query` shows few rows.
  */
final class LibraryAgentTools(site: => LibrarySite, upstream: () => AgentUpstream, caps: => AgentToolsConfig):
  import ToolResultCap.cap

  private def max: ToolResultCap.CapMax = ToolResultCap.CapMax(caps.resultMaxChars.value)

  /** The body slice an agent reads: what it asked for, or `body-chars`, and never past the result cap. */
  private def bodyChars(asked: Option[SourcesControllers.Chars]): Option[SourcesControllers.Chars] =
    val limit = max.value
    val want = asked.map(_.value).filter(_ > 0).orElse(Option.when(caps.bodyChars.value > 0)(caps.bodyChars.value))
    val chars = if limit <= 0 then want else Some(math.min(want.getOrElse(limit), math.max(500, limit - 300)))
    chars.map(SourcesControllers.Chars(_))

  private def contribution(n: ToolName)(t: => AgentTool[Identity, ?]): ToolContribution = new ToolContribution:
    val name: ToolName = n
    val agentTool: AgentTool[Identity, ?] = t

  /** Every tool of the library, by name. */
  def contributions: List[ToolContribution] = List(
    contribution(LibraryAgentTools.GetSource) {
      AgentTool.fromFunction[AgentGetSourceInput](
        LibraryAgentTools.GetSource.value,
        "Read one source by citekey: its summary, findings and notes, or the part `section` names."
      ) { in =>
        val section = in.sectionOrBody
        site.sources
          .markdown(
            in.citekey,
            fullText = SourcesControllers.MarkdownFullText(false),
            section,
            in.heading,
            in.offset,
            bodyChars(in.chars)
          )
          .fold(
            e => s"No source '${in.citekey}': ${e.message}",
            md =>
              section.map(_.value.toLowerCase) match
                case None | Some("all") =>
                  ToolResultCap
                    .source(ToolResultCap.Markdown(ToolResultCap.compactSource(ToolResultCap.Markdown(md.value))), max)
                case Some("body") =>
                  cap(md.value, max, "read on with get_source section=\"body\" offset=… chars=…; a word: passages")
                case Some("toc") => cap(md.value, max, "read one part with get_source heading=…")
                case _           =>
                  cap(ToolResultCap.plainLinks(ToolResultCap.Markdown(md.value)), max, "a word in the source: passages")
          )
      }
    },
    contribution(LibraryAgentTools.Passages) {
      AgentTool.fromFunction[PassagesInput](
        LibraryAgentTools.Passages.value,
        "Find a word or quotation in ONE source's full text: the passages around each match (in **bold**), " +
          "each with its body `offset` (get_source reads on from it) and heading. Literal, case- and " +
          "accent-insensitive."
      )(in =>
        cap(
          LibraryTools.passagesMarkdown(site.sources, in),
          max,
          "ask for fewer passages (`limit`), less `context`, or a more specific query"
        )
      )
    },
    contribution(LibraryAgentTools.Search) {
      AgentTool.fromFunction[AgentSearchInput](
        LibraryAgentTools.Search.value,
        "Search the corpus: sources, compact, with the findings that matched."
      ) { in =>
        val f = in.toFilter
        cap(
          ToolResultCap.plainLinks(ToolResultCap.Markdown(SearchMarkdown.briefResults(site.search.sources(f)))),
          max,
          "ask for fewer results (`limit`) or narrow the filters"
        )
      }
    },
    contribution(LibraryAgentTools.Facets) {
      AgentTool.fromFunction[AgentFacetsInput](
        LibraryAgentTools.Facets.value,
        "The existing values of one field, with source counts — pick from them instead of inventing new ones."
      ) { in =>
        val facetsIn = FacetsInput(
          Facet.FieldText(in.field.value),
          in.prefix.map(p => Api.Facets.Prefix(p.value))
        )
        site.labels
          .facets(
            Facet.Field.Wire(in.field.value),
            in.prefix.map(p => FilterSql.FacetPrefix(p.value)),
            facetsIn.toFilter
          )
          .fold(
            e => e.message.value,
            fs => cap(SearchMarkdown.facets(facetsIn.field, fs.toVector), max, "narrow it with `prefix`")
          )
      }
    },
    contribution(LibraryAgentTools.Neighbors) {
      AgentTool.fromFunction[NeighborsInput](
        LibraryAgentTools.Neighbors.value,
        "A source's typed edges in and out, with each neighbour's status. No edge is NOT evidence of agreement."
      ) { in =>
        val rows = site.citations.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then s"No edges recorded for ${in.citekey}. An empty result is not evidence of agreement."
        else
          cap(
            SourcesMarkdown.neighbors(Citekey(in.citekey.value), rows.toVector),
            max,
            "ask for one edge kind (`kinds`)"
          )
      }
    },
    contribution(LibraryAgentTools.Query) {
      AgentTool.fromFunction[QueryInput](
        LibraryAgentTools.Query.value,
        "One read-only SQL SELECT on the corpus database (Postgres) — e.g. every cited source's status at once."
      ) { in =>
        site.query
          .run(Query.QueryRequest(Query.QueryRequest.Sql(in.sql.value)))
          .fold(e => e.message.value, r => LibraryAgentTools.queryResult(r, caps.queryMaxRows, max))
      }
    },
    contribution(LibraryAgentTools.Followups) {
      AgentTool.fromFunction[FollowupsInput](
        LibraryAgentTools.Followups.value,
        "The papers citing a source, each with its citation intents and a proposed edge kind — critiques the " +
          "article may ignore."
      ) { in =>
        upstream()
          .followups(AgentUpstream.FollowupsId(in.id.value), in.since, in.limit)
          .fold(
            m => s"followups unavailable: $m",
            r => cap(AcquireMarkdown.followups(r), max, "ask for fewer papers (`limit`) or recent ones (`since`)")
          )
      }
    }
  )

object LibraryAgentTools:
  /** A `query` result as an agent sees it: at most `maxRows` rows, marked as cut (the row cap's own flag), then
    * [[cap]]ped like any result.
    */

  def queryResult(
      r: com.histmersion.api.library.Query.QueryResult,
      maxRows: AgentToolsConfig.QueryMaxRows,
      max: ToolResultCap.CapMax
  ): String =
    val rows = maxRows.value
    val shown = if rows > 0 && r.rows.size > rows then
      r.modify(_.rows)
        .setTo(r.rows.take(rows))
        .modify(_.rowCount)
        .setTo(com.histmersion.api.library.Query.QueryResult.RowCount(rows))
        .modify(_.truncated)
        .setTo(com.histmersion.api.library.Query.QueryResult.Truncated(true))
    else r
    val more = if shown.rows.size < r.rows.size then
      s"\n\n_…${r.rows.size - shown.rows.size}${
          if r.truncated.value then "+" else ""
        } more rows not shown — select only the rows you need " +
        "(`WHERE citekey IN (…)`, `LIMIT`), never a whole table_"
    else ""
    ToolResultCap.cap(
      com.histmersion.api.library.Query.markdown(shown).value + more,
      max,
      "select fewer columns or rows (`WHERE … IN (…)`, `LIMIT`)"
    )

  val GetSource: ToolName = ToolName("get_source")
  val Passages: ToolName = ToolName("passages")
  val Search: ToolName = ToolName("search")
  val Facets: ToolName = ToolName("facets")
  val Neighbors: ToolName = ToolName("neighbors")
  val Query: ToolName = ToolName("query")
  val Followups: ToolName = ToolName("followups")

/** Scala defaults hold when an agent's arguments are decoded — `search {"topics":[…]}` needs no `tags: []` (without it
  * every list was required, and a Codex skeptic step spent ~3 calls a step on "Missing required field", 2026-10-16).
  * Not strict: a stray argument is ignored rather than costing a turn.
  */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults
