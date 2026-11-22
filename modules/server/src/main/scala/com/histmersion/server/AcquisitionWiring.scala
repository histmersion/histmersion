package com.histmersion
package server

import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.library.acquisition.{
  AcquireCheckService,
  AcquireClients,
  AcquisitionControllers,
  AssessmentService,
  CandidateService
}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.research.articles.{ArticleCitedSources, ArticleService}
import com.histmersion.server.hot.Hot

/** The library's acquisition over one server's `deps`: its stores, and the operations over the acquisition node's
  * clients (a hot node, read at each use: a reload that changed `acquire.*` or the plugins takes effect without
  * rebuilding this), handing fetched copies to the ingest. The goals are the automation's, an article's citations
  * research's.
  */
final class AcquisitionWiring(deps: Deps, sources: SourceService, ingest: IngestWiring, clients: Hot[AcquireClients]):
  val assessments: AssessmentService = AssessmentService(deps.transactor)
  val candidates: CandidateService = CandidateService(deps.transactor)
  val attempts: AcquireCheckService = AcquireCheckService(deps.transactor)

  val controllers: AcquisitionControllers = AcquisitionControllers(
    sources,
    assessments,
    candidates,
    deps.queues,
    () => ingest.spool,
    () => deps.cfg,
    deps.maintenance,
    deps.pins,
    GoalService(deps.transactor),
    ArticleCitedSources(ArticleService(deps.transactor)),
    discovery = clients.peek.discovery,
    citations = clients.peek.citations,
    oa = clients.peek.oa,
    wiki = clients.peek.wiki,
    retractionWatch = clients.peek.retractionWatch,
    jobs = ingest.jobs,
    attempts = attempts,
    indexes = clients.peek.indexes,
    alerts = clients.peek.alerts,
    generation = clients.peek.generation.value
  )(using deps.clock)
