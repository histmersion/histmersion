package com.histmersion.domain.catalogue

import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.jobs.{BackgroundJob, JobStops, WorkCarrier}

import javax.sql.DataSource
import com.histmersion.api.Api

/** The catalogue's background runs — an event catalogue's import, a re-derivation, the event links' candidates, the
  * refinement of site types — one at a time (a run's pace is per run; two would ask twice as fast). A run that writes
  * takes the maintenance guard; a dry run does not. `stoppable` names the kinds a person may pause and cancel.
  */
final class CatalogueJobs(
    ds: DataSource,
    maintenance: WriteGate,
    pins: WorkCarrier,
    stops: JobStops,
    stoppable: Set[CatalogueJobs.Stoppable]
):
  private val slot = BackgroundJob.Slot()

  /** Start `body` as a job of `kind`; `body` answers the summary, or why the run failed. */
  def start(kind: com.histmersion.util.jobs.Job.Kind, describe: com.histmersion.domain.catalogue.CatalogueJobs.StartDescribe, write: com.histmersion.domain.catalogue.CatalogueJobs.StartWrite)(
      body: (JobInfo, com.histmersion.domain.catalogue.CatalogueJobs.StartBodyArg2Arg1 => Unit) => Either[com.histmersion.domain.catalogue.CatalogueJobs.StartBodyResultPart1, com.histmersion.domain.catalogue.CatalogueJobs.StartBodyResultPart2]
  ): Either[Api.Error, JobInfo] =
    def go: Either[Api.Error, JobInfo] =
      BackgroundJob.start(
        ds,
        kind,
        Some(com.histmersion.api.catalogue.EventsJobKinds.Catalogue(Some(describe))),
        slot,
        "an events run",
        pins = pins,
        stops = Option.when(stoppable(kind))(stops)
      )((job, progress) => BackgroundJob.Outcome.of(body(job, progress)))
    if write then maintenance.guard(kind)(go) else go

  /** Change the detail of the job `id` records. */
  def amend(id: com.histmersion.util.jobs.Job.Id)(change: JobDetail => JobDetail): Unit = BackgroundJob.amendDetail(ds, id)(change)

  /** Whether a maintenance run holds the lock: a long run stops between its parts. */
  def held: Boolean = maintenance.active

object CatalogueJobs:
  type Stoppable = Stoppable.T
  object Stoppable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type StartDescribe = StartDescribe.T
  object StartDescribe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartWrite = StartWrite.T
  object StartWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyArg2Arg1 = StartBodyArg2Arg1.T
  object StartBodyArg2Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart1 = StartBodyResultPart1.T
  object StartBodyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart2 = StartBodyResultPart2.T
  object StartBodyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
