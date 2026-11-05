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
  def geocode(name: com.histmersion.domain.library.workflows.LibrarySite.GeocodeName, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution]

  /** The sources the body of article `slug` cites, as its links resolve (research's article); why not, when none. */
  def articleSources(slug: com.histmersion.domain.library.workflows.LibrarySite.ArticleSourcesSlug): Either[Api.Error, List[com.histmersion.domain.library.workflows.LibrarySite.ArticleSourcesResultPart2]]

  /** The stored sources article `slug` cites, its record's list (none when there is no such article). */
  def citedSources(slug: com.histmersion.domain.library.workflows.LibrarySite.CitedSourcesSlug): List[com.histmersion.domain.library.workflows.LibrarySite.CitedSourcesResult]

  /** Whether an article has slug `slug`. */
  def articleExists(slug: com.histmersion.domain.library.workflows.LibrarySite.ArticleExistsSlug): com.histmersion.domain.library.workflows.LibrarySite.ArticleExistsResult

  /** The upstreams' kept quotas now. */
  def upstreamQuotas(now: Instant): List[UpstreamQuota]

object LibrarySite:
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
