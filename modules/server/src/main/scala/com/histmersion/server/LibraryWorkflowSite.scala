package com.histmersion.server

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.library.UpstreamQuota
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.{QueryControllers, SearchControllers}
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.domain.library.workflows.LibrarySite
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.JobListing

import java.time.Instant
import com.histmersion.api.Api

/** The library's workflows' site over the composition: its own wiring, the work lists, the write gate, the jobs, the
  * catalogue's geocoder and research's articles as the composition wires them.
  */
final class LibraryWorkflowSite(h: ServerWiring, deps: Deps) extends LibrarySite:
  def workLists: WorkListControllers = deps.workLists
  def queues: QueueService = deps.queues
  def maintenance: WriteGate = h.maintenance
  def sources: SourcesControllers = h.library.sources
  def sourceStore: SourceService = h.library.sourceStore
  def citations: CitationsControllers = h.library.citations
  def search: SearchControllers = h.library.search
  def labels: LabelsControllers = h.library.labels
  def query: QueryControllers = h.library.query
  def jobs: JobListing = h.jobs

  def geocode(name: com.histmersion.server.LibraryWorkflowSite.GeocodeName, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    h.geocode(name, hint, at)

  def articleSources(slug: com.histmersion.server.LibraryWorkflowSite.ArticleSourcesSlug): Either[Api.Error, List[com.histmersion.server.LibraryWorkflowSite.ArticleSourcesResultPart2]] =
    h.research.articles.get(slug).map(v => h.research.articles.citedSources(v.article.body))

  def citedSources(slug: com.histmersion.server.LibraryWorkflowSite.CitedSourcesSlug): List[com.histmersion.server.LibraryWorkflowSite.CitedSourcesResult] =
    h.research.articles.get(slug).toOption.toList.flatMap(_.sourcesCited.filter(_.title.isDefined).map(_.citekey))

  def articleExists(slug: com.histmersion.server.LibraryWorkflowSite.ArticleExistsSlug): com.histmersion.server.LibraryWorkflowSite.ArticleExistsResult = h.research.articleStore.exists(slug)

  def upstreamQuotas(now: Instant): List[UpstreamQuota] = h.upstreamQuotas(now)

object LibraryWorkflowSite:
  type GeocodeName = GeocodeName.T
  object GeocodeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSourcesResultPart2 = ArticleSourcesResultPart2.T
  object ArticleSourcesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSourcesSlug = ArticleSourcesSlug.T
  object ArticleSourcesSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSourcesResult = CitedSourcesResult.T
  object CitedSourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSourcesSlug = CitedSourcesSlug.T
  object CitedSourcesSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleExistsResult = ArticleExistsResult.T
  object ArticleExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleExistsSlug = ArticleExistsSlug.T
  object ArticleExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
