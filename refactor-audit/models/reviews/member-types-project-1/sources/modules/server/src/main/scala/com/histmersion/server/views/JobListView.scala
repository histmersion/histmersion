package com.histmersion.server.views

import com.histmersion.api.Paging
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.automation.ListFilters
import com.histmersion.api.automation.Agents.RunState
import com.histmersion.api.automation.ListFilters.JobFilter
import com.histmersion.util.jobs.{JobListing, JobQuery}

import java.time.Instant
import com.histmersion.api.Api

/** `GET /api/jobs` (and `job_status` without an id) filtered ([[ListFilters.JobFilter]]): the background jobs and the
  * agent runs, which are jobs too, in one page, newest first, with the per-state and the kind counts under the same
  * filter.
  */
final class JobListView(jobs: JobListing):
  def page(
      f: JobFilter,
      now: Instant,
      limit: Option[com.histmersion.server.views.JobListView.PageLimit],
      before: Option[com.histmersion.server.views.JobListView.PageBefore],
      upTo: Option[com.histmersion.server.views.JobListView.PageUpTo]
  ): Either[Api.Error, (List[JobInfo], Paging.Page)] =
    JobListView.query(f, now).flatMap(jobs.page(_, limit, before, upTo, ListFilters.FacetMax))

object JobListView:

  /** The states a job can be in: a background job's, and — the agent runs being jobs too — a run's. */
  type PageLimit = PageLimit.T
  object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBefore = PageBefore.T
  object PageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUpTo = PageUpTo.T
  object PageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobStatuses = JobStatuses.T
  object JobStatuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val JobStatuses: Seq[com.histmersion.server.views.JobListView.JobStatuses] = (JobState.values.map(_.wire) ++ RunState.values.map(_.wire)).distinct.toSeq

  /** A client's job filter checked and turned into the query: a state outside [[JobStatuses]] is a 400 naming the
    * allowed ones, a time bound is read once, against `now`; any kind passes (plugins add their own). Pure.
    */
  def query(f: JobFilter, now: Instant): Either[Api.Error, JobQuery] =
    val n = f.normalized
    for
      st <- ListFilters.known("status", n.status, JobStatuses).left.map(Api.Error(_))
      tr <- ListFilters.range(n.since, n.until, now).left.map(Api.Error(_))
    yield JobQuery(st, n.kind, tr._1, tr._2, n.subject)
