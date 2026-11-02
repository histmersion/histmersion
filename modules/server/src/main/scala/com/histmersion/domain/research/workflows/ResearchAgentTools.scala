package com.histmersion.domain.research.workflows

import com.histmersion.domain.automation.agents.ToolResultCap

import com.histmersion.api.ArticleMarkdown
import com.histmersion.config.AgentToolsConfig
import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleTools}
import com.histmersion.domain.research.articles.ArticleTools.{ArticleCoverageInput, CheckEditsInput, GetArticleInput}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** The agent tools research offers the engine: an article, a dry run of edits to it, its coverage — each a thin, typed
  * wrapper over the same controller method REST and MCP call, returning the same Markdown. Only READ tools:
  * `check_edits` only dry-runs a change.
  */
final class ResearchAgentTools(articles: => ArticlesControllers, caps: => AgentToolsConfig):
  import ToolResultCap.cap

  private def max = caps.resultMaxChars

  private def contribution(n: ToolName)(t: => AgentTool[Identity, ?]): ToolContribution = new ToolContribution:
    val name: ToolName = n
    val agentTool: AgentTool[Identity, ?] = t

  /** Every tool of research, by name. */
  def contributions: List[ToolContribution] = List(
    contribution(ResearchAgentTools.GetArticle) {
      AgentTool.fromFunction[GetArticleInput](
        ResearchAgentTools.GetArticle.value,
        "One compiled article by slug: body, summary, the generated coverage sections, the sources it cites " +
          "(title, year, status), backlinks. `parts` = comma-separated body,summary,coverage,cited,backlinks."
      ) { in =>
        articles
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

    contribution(ResearchAgentTools.CheckEdits) {
      AgentTool.fromFunction[CheckEditsInput](
        ResearchAgentTools.CheckEdits.value,
        "Dry-run edits (or a whole draft `body`) against the stored article: which edits apply, and what the " +
          "review checks find in the result. Nothing is written."
      )(in => cap(ArticleTools.checkEdits(articles, in), max, "dry-run fewer edits at once"))
    },

    contribution(ResearchAgentTools.ArticleCoverage) {
      AgentTool.fromFunction[ArticleCoverageInput](
        ResearchAgentTools.ArticleCoverage.value,
        "An article's recorded filter: its sources, which are cited, each other one's stored reason, the " +
          "drift (sources with no reason), and the generated Not used list and Method note."
      )(in =>
        cap(
          ArticleTools.articleCoverageMarkdown(articles, in),
          max,
          "read one source's record with get_source"
        )
      )
    }
  )

object ResearchAgentTools:
  val GetArticle: ToolName = ToolName("get_article")
  val CheckEdits: ToolName = ToolName("check_edits")
  val ArticleCoverage: ToolName = ToolName("article_coverage")

/** Scala defaults hold when an agent's arguments are decoded — `get_article {"slug":…}` needs no `parts` (without it
  * every optional argument was required, and an agent spent turns on "Missing required field", 2026-10-16). Not strict:
  * a stray argument is ignored rather than costing a turn.
  */
private given hearth.kindlings.circederivation.Configuration =
  hearth.kindlings.circederivation.Configuration.default.withDefaults
