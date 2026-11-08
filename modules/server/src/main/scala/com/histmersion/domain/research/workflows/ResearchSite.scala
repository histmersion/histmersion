package com.histmersion
package domain.research.workflows

import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.SearchControllers
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.api.Api

/** What research's workflows are given by the composition: the work lists, research's own articles (their controllers
  * and their store) and review ledgers, and what they read of the library — its sources, citations, search and labels —
  * and of the engine's goals.
  */
trait ResearchSite:
  def workLists: WorkListControllers
  def queues: QueueService
  def articles: ArticlesControllers
  def articleStore: ArticleService
  def ledgers: ReviewLedgerService
  def sources: SourcesControllers
  def sourceStore: SourceService
  def citations: CitationsControllers
  def search: SearchControllers
  def labels: LabelsControllers

  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal]

