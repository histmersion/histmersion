package com.histmersion.domain.library.workflows

import com.histmersion.api.{ApiError, Geocode, Period, UpstreamQuota}
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.{QueryControllers, SearchControllers}
import com.histmersion.domain.library.sources.{SourceService, SourcesControllers}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.JobListing

import java.time.Instant

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
  def geocode(name: String, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution]

  /** The sources the body of article `slug` cites, as its links resolve (research's article); why not, when none. */
  def articleSources(slug: String): Either[ApiError, List[String]]

  /** The stored sources article `slug` cites, its record's list (none when there is no such article). */
  def citedSources(slug: String): List[String]

  /** Whether an article has slug `slug`. */
  def articleExists(slug: String): Boolean

  /** The upstreams' kept quotas now. */
  def upstreamQuotas(now: Instant): List[UpstreamQuota]
