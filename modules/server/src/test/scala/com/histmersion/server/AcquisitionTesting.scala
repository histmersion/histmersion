package com.histmersion
package server

import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.library.acquisition.*
import com.histmersion.domain.library.ingest.{IngestJobs, SkeletonIngest}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.research.articles.ArticleCitedSources
import com.histmersion.pluginhost.OaFetcher
import com.histmersion.util.database.Transactor
import com.histmersion.domain.library.acquisition.Spool
import com.histmersion.util.storage.Location

import javax.sql.DataSource
import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.util.database.TestCreators

/** The library's acquisition as the server wires it, over one test's `deps` and its own upstream fakes. */
object AcquisitionTesting:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def sources(deps: Deps): SourceService =
    SourceService(deps.transactor, Deps.sourceReferrers, () => LibraryWiring.settings(deps.cfg), TestCreators.clock)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def skeleton(deps: Deps): SkeletonIngest = SkeletonIngest(sources(deps))

  def assessments(ds: DataSource): AssessmentService = AssessmentService(Transactor(ds))
  def candidates(ds: DataSource): CandidateService = CandidateService(Transactor(ds))
  def attempts(ds: DataSource): AcquireCheckService = AcquireCheckService(Transactor(ds))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def controllers(
      deps: Deps,
      discovery: => DiscoveryClient,
      citations: => CitationsClient,
      oa: => OaFetcher,
      wiki: => WikiClient,
      retractionWatch: => RetractionWatchClient,
      jobs: IngestJobs,
      indexes: => List[ScholarlyIndex] = Nil,
      alerts: => CitationAlerts = NoAlerts,
      generation: => String = ""
  ): AcquisitionControllers =
    AcquisitionControllers(
      sources(deps),
      AssessmentService(deps.transactor),
      CandidateService(deps.transactor),
      deps.queues,
      () => Spool(deps.storage.store(Location.Spool)),
      () => deps.cfg,
      deps.maintenance,
      deps.pins,
      GoalService(deps.transactor, TestCreators.clock),
      ArticleCitedSources(ArticleService(deps.transactor, TestCreators.clock)),
      discovery,
      citations,
      oa,
      wiki,
      retractionWatch,
      jobs,
      AcquireCheckService(deps.transactor),
      indexes,
      alerts,
      generation
    )(using deps.clock)
