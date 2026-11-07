package com.histmersion.domain.library.workflows

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.library.UpstreamQuota
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.{QueryControllers, SearchControllers}
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.JobListing

import java.time.Instant
import com.histmersion.api.Api

/** What the library's workflows are given by the composition: the work lists, the write gate, the library's own
  * controllers, the jobs, and what they ask of the other domains — a place name's point, the stored sources an article
  * cites, whether an article exists — and of the acquisition's upstreams, their quotas.
  */
trait LibrarySite:
  def workLists: WorkListControllers
  def queues: QueueService
  def maintenance: WriteGate
  def sources: SourcesControllers
  def sourceStore: SourceService
  def citations: CitationsControllers
  def search: SearchControllers
  def labels: LabelsControllers
  def query: QueryControllers
  def jobs: JobListing

  /** Where a place name is, for a finding of period `at` about a place of kind `hint` (the catalogue's geocoder). */
  def geocode(name: com.histmersion.domain.library.workflows.LibrarySite.PlaceName, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution]

  /** The sources the body of article `slug` cites, as its links resolve (research's article); why not, when none. */
  def articleSources(slug: com.histmersion.domain.research.Slug): Either[Api.Error, List[com.histmersion.domain.library.Citekey]]

  /** The stored sources article `slug` cites, its record's list (none when there is no such article). */
  def citedSources(slug: com.histmersion.domain.research.Slug): List[com.histmersion.domain.library.Citekey]

  /** Whether an article has slug `slug`. */
  def articleExists(slug: com.histmersion.domain.research.Slug): Boolean

  /** The upstreams' kept quotas now. */
  def upstreamQuotas(now: Instant): List[UpstreamQuota]

object LibrarySite:
  type PlaceName = PlaceName.T
  object PlaceName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
