package com.histmersion
package util.jobs
import com.histmersion.util.jobs.Job
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.Problem

import com.histmersion.util.database.Transactor

import com.histmersion.api.{Paging, Write}
import com.histmersion.PageSize
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.config.AppConfig

import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.lint.{LintRule, tolerated}

/** The background jobs as `job_status` and `GET /api/jobs` list them: one job, or a page newest first under a query,
  * with the per-state and per-kind counts under the same query.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
final class JobListing(ds: DataSource, cfg: () => AppConfig):

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def searchPage(c: AppConfig): PageSize = PageSize.applyUnsafe(math.max(1, c.limits.search.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def job(id: Job.Id): Either[Api.Error, JobInfo] =
    Transactor(ds)
      .read(JobRepo.get(id))
      .toRight(
        Api.Error(
          Problem.NotFound.Entity(Problem.TypeName.of(Job.Id), Problem.Raw(s"$id")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  def jobs(status: Option[JobState.Wire], limit: Option[PageSize]): List[JobInfo] =
    val c = cfg()
    Transactor(ds).read(JobRepo.list(status, c.limits.page(limit, searchPage(c)))).toList

  /** One page under `q`: `before` = only jobs older than that one, `upTo` = only jobs down to it; at most `facetMax`
    * kinds counted.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(
      q: JobQuery,
      limit: Option[PageSize],
      before: Option[Paging.Cursor],
      upTo: Option[Paging.Cursor],
      facetMax: PageSize
  ): Either[Api.Error, (List[JobInfo], Paging.Page)] =
    val c = cfg()
    Transactor(ds)
      .read(JobRepo.page(q, c.limits.page(limit, searchPage(c)), clean(before), clean(upTo)))
      .left
      .map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeNotFound))))
      .map { (rows, more) =>
        val (counts, facets) = Transactor(ds).read(JobRepo.facets(q, facetMax))
        (
          rows.toList,
          Paging.Page.filtered(
            counts.map((state, n) => (Paging.Page.CountState(state.value), Paging.Count(n.value))),
            q.status.map(st => Paging.Page.CountState(st.value)),
            Option.when(more)(Filter.Last(rows.last.id.value)),
            facets
              .map((name, values) => (Facet.Name(name), values.map((v, n) => (Facet.Value(v), Facet.Count(n.value)))))
          )
        )
      }

  /** The newest job of `kind`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def newest(kind: Job.Kind): Option[JobInfo] =
    Transactor(ds)
      .read(JobRepo.page(JobQuery(kinds = List(JobQuery.Kinds(kind.value))), PageSize(1), None, None))
      .toOption
      .flatMap(_._1.headOption)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def clean(cursor: Option[Paging.Cursor]): Option[JobRepo.PageCursor] =
    cursor.map(_.value.trim).filter(_.nonEmpty).map(JobRepo.PageCursor(_))
