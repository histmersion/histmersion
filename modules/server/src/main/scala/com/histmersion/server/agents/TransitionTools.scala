package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.ToolResultCap

import com.histmersion.api.ArticleMarkdown
import com.histmersion.config.AgentToolsConfig
import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.library.workflows.{AgentUpstream, LibraryAgentTools}
import com.histmersion.server.Handlers
import com.histmersion.domain.research.articles.ArticleTools
import com.histmersion.domain.research.articles.ArticleTools.{ArticleCoverageInput, CheckEditsInput, GetArticleInput}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** The agent tools the engine's toolbox lists: the library's own ([[LibraryAgentTools]]) and the ones today's research
  * code offers, registered through this transition adapter until research contributes its own (each a thin, typed
  * wrapper over the same [[Handlers]] method REST and MCP call, returning the same Markdown), in the order the toolbox
  * has always listed them. Only READ tools: `check_edits` only dry-runs a change.
  */
final class TransitionTools(h: Handlers, upstream: () => AgentUpstream, caps: => AgentToolsConfig):
  import ToolResultCap.cap

  private def max = caps.resultMaxChars

  private def contribution(n: ToolName)(t: => AgentTool[Identity, ?]): ToolContribution = new ToolContribution:
    val name: ToolName = n
    val agentTool: AgentTool[Identity, ?] = t

  private val research: List[ToolContribution] = List(
    contribution(TransitionTools.GetArticle) {
      AgentTool.fromFunction[GetArticleInput](
        TransitionTools.GetArticle.value,
        "One compiled article by slug: body, summary, the generated coverage sections, the sources it cites " +
          "(title, year, status), backlinks. `parts` = comma-separated body,summary,coverage,cited,backlinks."
      ) { in =>
        h.research.articles
          .get(in.slug)
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

    contribution(TransitionTools.CheckEdits) {
      AgentTool.fromFunction[CheckEditsInput](
        TransitionTools.CheckEdits.value,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written."
      )(in => cap(ArticleTools.checkEdits(h.research.articles, in), max, "dry-run fewer edits at once"))
    },

    contribution(TransitionTools.ArticleCoverage) {
      AgentTool.fromFunction[ArticleCoverageInput](
        TransitionTools.ArticleCoverage.value,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note."
      )(in =>
        cap(
          ArticleTools.articleCoverageMarkdown(h.research.articles, in),
          max,
          "read one source's record with get_source"
        )
      )
    }
  )

  /** Every tool, in the order the engine's toolbox has always listed them. */
  def contributions: List[ToolContribution] =
    val all = (LibraryAgentTools(h.librarySite, upstream, caps).contributions ++ research).map(t => t.name -> t).toMap
    TransitionTools.names.map(all)

object TransitionTools:
  export LibraryAgentTools.{Facets, Followups, GetSource, Neighbors, Passages, Query, Search}

  val GetArticle: ToolName = ToolName("get_article")
  val CheckEdits: ToolName = ToolName("check_edits")
  val ArticleCoverage: ToolName = ToolName("article_coverage")

  /** The names, in the order of [[TransitionTools.contributions]]: what the definitions may list before a server runs.
    */
  val names: List[ToolName] = List(
    LibraryAgentTools.GetSource,
    LibraryAgentTools.Passages,
    LibraryAgentTools.Search,
    LibraryAgentTools.Facets,
    LibraryAgentTools.Neighbors,
    LibraryAgentTools.Query,
    GetArticle,
    LibraryAgentTools.Followups,
    CheckEdits,
    ArticleCoverage
  )

/** Scala defaults hold when an agent's arguments are decoded — `search {"topics":[…]}` needs no `tags: []` (without it
  * every list was required, and a Codex skeptic step spent ~3 calls a step on "Missing required field", 2026-10-16).
  * Not strict: a stray argument is ignored rather than costing a turn.
  */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults
