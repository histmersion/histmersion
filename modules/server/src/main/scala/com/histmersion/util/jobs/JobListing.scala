package com.histmersion.util.jobs

import com.histmersion.api.{ApiError, JobInfo, Paging, Write}
import com.histmersion.config.AppConfig

import javax.sql.DataSource

/** The background jobs as `job_status` and `GET /api/jobs` list them: one job, or a page newest first under a query,
  * with the per-state and per-kind counts under the same query.
  */
final class JobListing(ds: DataSource, cfg: () => AppConfig):

  def job(id: String): Either[ApiError, JobInfo] =
    JobRepo.get(ds, id).toRight(ApiError(s"No job '$id'", Some(Write.CodeNotFound)))

  def jobs(status: Option[String], limit: Option[Int]): List[JobInfo] =
    val c = cfg()
    JobRepo.list(ds, status, c.limits.page(limit, c.limits.search)).toList

  /** One page under `q`: `before` = only jobs older than that one, `upTo` = only jobs down to it. */
  def page(
      q: JobQuery,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String]
  ): Either[ApiError, (List[JobInfo], Paging.Page)] =
    val c = cfg()
    JobRepo
      .page(ds, q, c.limits.page(limit, c.limits.search), clean(before), clean(upTo))
      .left
      .map(ApiError(_, Some(Write.CodeNotFound)))
      .map { (rows, more) =>
        val (counts, facets) = JobRepo.facets(ds, q)
        (rows.toList, Paging.Page.filtered(counts, q.status, Option.when(more)(rows.last.id), facets))
      }

  /** The newest job of `kind`. */
  def newest(kind: String): Option[JobInfo] =
    JobRepo.page(ds, JobQuery(kinds = List(kind)), 1, None, None).toOption.flatMap(_._1.headOption)

  private def clean(cursor: Option[String]): Option[String] = cursor.map(_.trim).filter(_.nonEmpty)
