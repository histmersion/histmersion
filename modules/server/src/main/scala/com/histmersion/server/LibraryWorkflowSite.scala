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

  def geocode(name: com.histmersion.domain.library.workflows.LibrarySite.PlaceName, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    h.geocode(Geocode.Attempt.Name(name.value), hint, at)

  def articleSources(slug: com.histmersion.domain.research.Slug): Either[Api.Error, List[com.histmersion.domain.library.Citekey]] =
    h.research.articles.get(slug).map(v => h.research.articles.citedSources(v.article.body))

  def citedSources(slug: com.histmersion.domain.research.Slug): List[com.histmersion.domain.library.Citekey] =
    h.research.articles.get(slug).toOption.toList.flatMap(_.sourcesCited.filter(_.title.isDefined).flatMap(c => com.histmersion.domain.library.Citekey.option(c.citekey.value)))

  def articleExists(slug: com.histmersion.domain.research.Slug): Boolean = h.research.articleStore.exists(slug)

  def upstreamQuotas(now: Instant): List[UpstreamQuota] = h.upstreamQuotas(now)

