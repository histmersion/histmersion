package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor

import com.histmersion.api.{ApiError, JobInfo, Paging, Write}
import com.histmersion.config.AppConfig

import javax.sql.DataSource

/** The background jobs as `job_status` and `GET /api/jobs` list them: one job, or a page newest first under a query,
  * with the per-state and per-kind counts under the same query.
  */
final class JobListing(ds: DataSource, cfg: () => AppConfig):

  def job(id: String): Either[ApiError, JobInfo] =
    Transactor(ds).read(JobRepo.get(id)).toRight(ApiError(s"No job '$id'", Some(Write.CodeNotFound)))

  def jobs(status: Option[String], limit: Option[Int]): List[JobInfo] =
    val c = cfg()
    Transactor(ds).read(JobRepo.list(status, c.limits.page(limit, c.limits.search))).toList

  /** One page under `q`: `before` = only jobs older than that one, `upTo` = only jobs down to it; at most `facetMax`
    * kinds counted.
    */
  def page(
      q: JobQuery,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String],
      facetMax: Int
  ): Either[ApiError, (List[JobInfo], Paging.Page)] =
    val c = cfg()
    Transactor(ds)
      .read(JobRepo.page(q, c.limits.page(limit, c.limits.search), clean(before), clean(upTo)))
      .left
      .map(ApiError(_, Some(Write.CodeNotFound)))
      .map { (rows, more) =>
        val (counts, facets) = Transactor(ds).read(JobRepo.facets(q, facetMax))
        (rows.toList, Paging.Page.filtered(counts, q.status, Option.when(more)(rows.last.id), facets))
      }

  /** The newest job of `kind`. */
  def newest(kind: String): Option[JobInfo] =
    Transactor(ds).read(JobRepo.page(JobQuery(kinds = List(kind)), 1, None, None)).toOption.flatMap(_._1.headOption)

  private def clean(cursor: Option[String]): Option[String] = cursor.map(_.trim).filter(_.nonEmpty)
