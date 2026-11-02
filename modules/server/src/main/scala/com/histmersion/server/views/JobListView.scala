package com.histmersion.server.views

import com.histmersion.api.{ApiError, JobInfo, JobState, ListFilters, Paging}
import com.histmersion.api.Agents.RunState
import com.histmersion.api.ListFilters.JobFilter
import com.histmersion.util.jobs.{JobListing, JobQuery}

import java.time.Instant

/** `GET /api/jobs` (and `job_status` without an id) filtered ([[ListFilters.JobFilter]]): the background jobs and the
  * agent runs, which are jobs too, in one page, newest first, with the per-state and the kind counts under the same
  * filter.
  */
final class JobListView(jobs: JobListing):
  def page(
      f: JobFilter,
      now: Instant,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String]
  ): Either[ApiError, (List[JobInfo], Paging.Page)] =
    JobListView.query(f, now).flatMap(jobs.page(_, limit, before, upTo, ListFilters.FacetMax))

object JobListView:

  /** The states a job can be in: a background job's, and — the agent runs being jobs too — a run's. */
  val JobStatuses: Seq[String] = (JobState.values.map(_.wire) ++ RunState.values.map(_.wire)).distinct.toSeq

  /** A client's job filter checked and turned into the query: a state outside [[JobStatuses]] is a 400 naming the
    * allowed ones, a time bound is read once, against `now`; any kind passes (plugins add their own). Pure.
    */
  def query(f: JobFilter, now: Instant): Either[ApiError, JobQuery] =
    val n = f.normalized
    for
      st <- ListFilters.known("status", n.status, JobStatuses).left.map(ApiError(_))
      tr <- ListFilters.range(n.since, n.until, now).left.map(ApiError(_))
    yield JobQuery(st, n.kind, tr._1, tr._2, n.subject)
