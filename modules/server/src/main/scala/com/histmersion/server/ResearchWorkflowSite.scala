package com.histmersion.server

import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.SearchControllers
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.domain.research.workflows.{ResearchSite, ReviewLedgerService}
import com.histmersion.api.Api

/** Research's workflows' site over the composition: research's and the library's wiring, the work lists and the goals
  * as the composition wires them.
  */
final class ResearchWorkflowSite(h: ServerWiring, deps: Deps) extends ResearchSite:
  def workLists: WorkListControllers = deps.workLists
  def queues: QueueService = deps.queues
  def articles: ArticlesControllers = h.research.articles
  def articleStore: ArticleService = h.research.articleStore
  def ledgers: ReviewLedgerService = h.research.ledgers
  def sources: SourcesControllers = h.library.sources
  def sourceStore: SourceService = h.library.sourceStore
  def citations: CitationsControllers = h.library.citations
  def search: SearchControllers = h.library.search
  def labels: LabelsControllers = h.library.labels
  def goal(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal] = h.goals.get(slug)

object ResearchWorkflowSite:
