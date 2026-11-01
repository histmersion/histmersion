package com.histmersion.domain.catalogue

import com.histmersion.api.{ApiError, JobDetail, JobInfo}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, JobStops, WorkCarrier}

import javax.sql.DataSource

/** The catalogue's background runs — an event catalogue's import, a re-derivation, the event links' candidates, the
  * refinement of site types — one at a time (a run's pace is per run; two would ask twice as fast). A run that writes
  * takes the maintenance guard; a dry run does not. `stoppable` names the kinds a person may pause and cancel.
  */
final class CatalogueJobs(
    ds: DataSource,
    maintenance: WriteGate,
    pins: WorkCarrier,
    stops: JobStops,
    stoppable: Set[String]
):
  private val slot = BackgroundJob.Slot()

  /** Start `body` as a job of `kind`; `body` answers the summary, or why the run failed. */
  def start(kind: String, describe: String, write: Boolean)(
      body: (JobInfo, String => Unit) => Either[String, String]
  ): Either[ApiError, JobInfo] =
    def go: Either[ApiError, JobInfo] =
      BackgroundJob.start(
        ds,
        kind,
        Some(JobDetail(events = Some(describe))),
        slot,
        "an events run",
        pins = pins,
        stops = Option.when(stoppable(kind))(stops)
      )((job, progress) => BackgroundJob.Outcome.of(body(job, progress)))
    if write then maintenance.guard(kind)(go) else go

  /** Change the detail of the job `id` records. */
  def amend(id: String)(change: JobDetail => JobDetail): Unit = BackgroundJob.amendDetail(ds, id)(change)

  /** Whether a maintenance run holds the lock: a long run stops between its parts. */
  def held: Boolean = maintenance.active
