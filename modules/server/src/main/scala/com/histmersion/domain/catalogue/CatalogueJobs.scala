package com.histmersion
package domain.catalogue
import com.histmersion.util.problem.Problems

import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, JobStops, WorkCarrier}

import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.util.jobs.Job
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** The catalogue's background runs — an event catalogue's import, a re-derivation, the event links' candidates, the
  * refinement of site types — one at a time (a run's pace is per run; two would ask twice as fast). Starting one takes
  * the maintenance guard. `stoppable` names the kinds a person may pause and cancel.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class CatalogueJobs(
    ds: DataSource,
    maintenance: WriteGate,
    pins: WorkCarrier,
    stops: JobStops,
    stoppable: Set[Job.Kind]
):
  private val slot = BackgroundJob.Slot()

  /** Start `body` as a job of `kind`; `body` answers the summary, or every problem the run failed with. The job's row
    * is written under the maintenance gate (refused while a restore runs: a dry run's too, its row is a write); `body`
    * holds the job's unit permit.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start(kind: Job.Kind, describe: JobKinds.Subject)(
      body: (JobInfo, JobInfo.Progress => Unit) => WritePermit ?=> Either[Problems, BackgroundJob.Outcome.Summary]
  )(using Creator.New): Either[Api.Error, JobInfo] =
    maintenance.guard(WriteGate.OperationName(kind.value)):
      BackgroundJob.start(
        ds,
        maintenance.workers,
        kind,
        Some(EventsJobKinds.Catalogue(Some(describe))),
        slot,
        BackgroundJob.Work("an events run"),
        pins = pins,
        stops = Option.when(stoppable(kind))(stops)
      )((job, progress) => BackgroundJob.Outcome.of(body(job, progress)))

  /** Change the detail of the job `id` records. */
  def amend(id: Job.Id)(change: JobDetail => JobDetail)(using WritePermit): Unit =
    BackgroundJob.amendDetail(ds, id)(change)

  /** Whether a maintenance run holds the lock: a long run stops between its parts. */
  def held: Boolean = maintenance.active
