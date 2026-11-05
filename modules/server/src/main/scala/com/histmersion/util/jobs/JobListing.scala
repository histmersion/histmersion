package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.{Paging, Write}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.config.AppConfig

import javax.sql.DataSource
import com.histmersion.api.Api

/** The background jobs as `job_status` and `GET /api/jobs` list them: one job, or a page newest first under a query,
  * with the per-state and per-kind counts under the same query.
  */
final class JobListing(ds: DataSource, cfg: () => AppConfig):

  def job(id: com.histmersion.util.jobs.JobListing.JobId): Either[Api.Error, JobInfo] =
    Transactor(ds).read(JobRepo.get(id)).toRight(Api.Error(s"No job '$id'", Some(Write.CodeNotFound)))

  def jobs(status: Option[com.histmersion.util.jobs.JobListing.JobsStatus], limit: Option[com.histmersion.util.jobs.JobListing.JobsLimit]): List[JobInfo] =
    val c = cfg()
    Transactor(ds).read(JobRepo.list(status, c.limits.page(limit, c.limits.search))).toList

  /** One page under `q`: `before` = only jobs older than that one, `upTo` = only jobs down to it; at most `facetMax`
    * kinds counted.
    */
  def page(
      q: JobQuery,
      limit: Option[com.histmersion.util.jobs.JobListing.PageLimit],
      before: Option[com.histmersion.util.jobs.JobListing.PageBefore],
      upTo: Option[com.histmersion.util.jobs.JobListing.PageUpTo],
      facetMax: com.histmersion.util.jobs.JobListing.PageFacetMax
  ): Either[Api.Error, (List[JobInfo], Paging.Page)] =
    val c = cfg()
    Transactor(ds)
      .read(JobRepo.page(q, c.limits.page(limit, c.limits.search), clean(before), clean(upTo)))
      .left
      .map(Api.Error(_, Some(Write.CodeNotFound)))
      .map { (rows, more) =>
        val (counts, facets) = Transactor(ds).read(JobRepo.facets(q, facetMax))
        (rows.toList, Paging.Page.filtered(counts, q.status, Option.when(more)(rows.last.id), facets))
      }

  /** The newest job of `kind`. */
  def newest(kind: com.histmersion.util.jobs.JobListing.NewestKind): Option[JobInfo] =
    Transactor(ds).read(JobRepo.page(JobQuery(kinds = List(kind)), 1, None, None)).toOption.flatMap(_._1.headOption)

  private def clean(cursor: Option[com.histmersion.util.jobs.JobListing.CleanCursor]): Option[com.histmersion.util.jobs.JobListing.CleanResult] = cursor.map(_.trim).filter(_.nonEmpty)

object JobListing:
  type JobId = JobId.T
  object JobId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsStatus = JobsStatus.T
  object JobsStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobsLimit = JobsLimit.T
  object JobsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLimit = PageLimit.T
  object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBefore = PageBefore.T
  object PageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUpTo = PageUpTo.T
  object PageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageFacetMax = PageFacetMax.T
  object PageFacetMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewestKind = NewestKind.T
  object NewestKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanResult = CleanResult.T
  object CleanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CleanCursor = CleanCursor.T
  object CleanCursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
