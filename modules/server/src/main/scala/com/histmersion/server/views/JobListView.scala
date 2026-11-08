package com.histmersion
package server.views

import com.histmersion.api.Problem
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
      limit: Option[PageSize],
      before: Option[Paging.Cursor],
      upTo: Option[Paging.Cursor]
  ): Either[Api.Error, (List[JobInfo], Paging.Page)] =
    JobListView.query(f, now).flatMap(jobs.page(_, limit, before, upTo, PageSize.assume(ListFilters.FacetMax)))

object JobListView:
  /** The states a job can be in: a background job's, and — the agent runs being jobs too — a run's. */
  val JobStatuses: Seq[ListFilters.Term] =
    (JobState.values.map(_.wire.value) ++ RunState.values.map(_.wire.value)).distinct.toSeq.map(ListFilters.Term(_))

  /** A client's job filter checked and turned into the query: a state outside [[JobStatuses]] is a 400 naming the
    * allowed ones, a time bound is read once, against `now`; any kind passes (plugins add their own). Pure.
    */
  def query(f: JobFilter, now: Instant): Either[Api.Error, JobQuery] =
    val n = f.normalized
    for
      st <- ListFilters
        .known(ListFilters.FacetName("status"), n.status.map(s => ListFilters.Term(s.value)), JobStatuses)
        .left
        .map(p => Api.Error(Problem(p.value)))
      tr <- ListFilters
        .range(n.since.map(s => ListFilters.TimeBound(s.value)), n.until.map(u => ListFilters.TimeBound(u.value)), now)
        .left
        .map(p => Api.Error(Problem(p.value)))
    yield JobQuery(
      st.map(t => JobQuery.Status(t.value)),
      n.kind.map(k => JobQuery.Kinds(k.value)),
      tr._1,
      tr._2,
      n.subject.map(s => JobQuery.Q(s.value))
    )
