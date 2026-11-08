package com.histmersion
package domain.research.workflows

import com.histmersion.api.automation.Agents.Origin
import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.{AgentsControllers, StepAgent}
import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.automation.workflows.{ToolContribution, WorkflowDefinition}
import com.histmersion.domain.library.workflows.SourceProposals

/** What research contributes to the workflow engine: its four pipelines as workflows (review-article with its review
  * comparison, compile-article, infer-filter, propose-articles), the review manager kind, its measure of a goal's
  * progress, the seeding manager kind and its agent tools. The review proposes source statuses and edges through the
  * library's `proposals`.
  */
final class ResearchWorkflows(
    cfg: () => AppConfig,
    site: ResearchSite,
    a: AgentsControllers,
    proposals: SourceProposals
):

  val reviewArticle: WorkflowDefinition = ResearchWorkflow(
    ReviewArticlePipeline(site, site.ledgers, cfg().researchAgents, cfg().libraryAgents, proposals, a.definitions),
    List(StepAgent.RulesReviewer, StepAgent.ColdReader, StepAgent.Skeptic, StepAgent.Fixer),
    a.definitions,
    ReviewComparison
  )

  val compileArticle: WorkflowDefinition = ResearchWorkflow(
    CompileArticlePipeline(
      site,
      a.goals,
      cfg().agents,
      cfg().researchAgents,
      cfg().libraryAgents,
      (req, parent) => a.startRunAs(req, Origin.Run, Some(parent))
    ),
    List(StepAgent.CompileExtractor, StepAgent.CompileWriter),
    a.definitions
  )

  val inferFilter: WorkflowDefinition = ResearchWorkflow(
    InferFilterPipeline(site, cfg().agents, cfg().researchAgents),
    List(StepAgent.FilterInferrer),
    a.definitions
  )

  val proposeArticles: WorkflowDefinition = ResearchWorkflow(
    ProposeArticlesPipeline(site, a.goals, a.managerStore, cfg().libraryAgents),
    List(StepAgent.ArticlePlanner),
    a.definitions
  )

  /** A goal's seeding manager: its articles proposed, then compiled once a person approved them. */
  val seedKind: SeedManagerKind = SeedManagerKind(site, cfg().libraryAgents)

  /** The library-wide review manager. */
  val reviewKind: ReviewManagerKind = ReviewManagerKind(site, site.ledgers, cfg().researchAgents)

  /** A goal's articles, finished once reviewed. */
  val objectives: ObjectiveProgress = ArticleObjectives(site.articleStore, site.ledgers)

  /** An article, a dry run of edits to it and its coverage, as an agent reads them. */
  val tools: List[ToolContribution] = ResearchAgentTools(site.articles, cfg().agents.tools).contributions
