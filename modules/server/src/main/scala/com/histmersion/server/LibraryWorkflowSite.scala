package com.histmersion
package server

import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.api.catalogue.geocode.Resolution
import com.histmersion.api.catalogue.geocode.Attempt

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
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The library's workflows' site over the composition: its own wiring, the work lists, the write gate, the jobs, the
  * catalogue's geocoder and research's articles as the composition wires them.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@movesTo(FeaturePlace.Server)
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def geocode(name: LibrarySite.PlaceName, hint: KindHint, at: Option[Period]): Option[Resolution] =
    h.geocode(Attempt.Name(name.value), hint, at)

  def articleSources(slug: Slug): Either[Api.Error, List[Citekey]] =
    h.research.articles.get(slug).map(v => h.research.articles.citedSources(v.article.body))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citedSources(slug: Slug): List[Citekey] =
    h.research.articles
      .get(slug)
      .toOption
      .toList
      .flatMap(_.sourcesCited.filter(_.title.isDefined).flatMap(c => Citekey.option(c.citekey.value)))

  def articleExists(slug: Slug): Boolean = h.research.articleStore.exists(slug)

  def upstreamQuotas(now: Instant): List[UpstreamQuota] = h.upstreamQuotas(now)
