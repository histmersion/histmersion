package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.VacuumRepo

import javax.sql.DataSource

/** `POST /api/maintenance/vacuum` (#1445): VACUUM as a [[BackgroundJob]] (kind `vacuum`) — a vacuum of `place_names`
  * (3.7 GB) runs for minutes, past any request timeout. It answers at once with the job; `histmersion jobs <id>`
  * follows it, and a restart fails it as `interrupted` like every other unfinished job. One at a time; each table is
  * its own statement outside any transaction, so a failure or a restore stops the job between tables, never inside one.
  */
final class VacuumJobs(
    ds: DataSource,
    maintenance: Maintenance,
    defaults: () => List[String],
    pins: com.histmersion.server.hot.Pinning = com.histmersion.server.hot.Pinning.none
):

  /** One vacuum at a time ([[BackgroundJob]]). */
  private val slot = BackgroundJob.Slot()

  /** Is a vacuum job running? A restore waits for it rather than drop tables under it. */
  def isRunning: Boolean = slot.isRunning

  def start(in: VacuumRequest): Either[ApiError, JobInfo] =
    maintenance.guard("maintenance/vacuum"):
      Vacuum.plan(in, defaults(), VacuumRepo.tables(ds)).flatMap { plan =>
        BackgroundJob.start(
          ds,
          Vacuum.JobKind,
          Some(JobDetail(vacuum = Some(plan))),
          slot,
          "a vacuum job",
          Some(Backup.CodeMaintenance),
          pins
        )((_, progress) => run(plan, progress))
      }

  private def run(plan: VacuumPlan, progress: String => Unit): BackgroundJob.Outcome =
    val (done, failed) = plan.tables.zipWithIndex.foldLeft((List.empty[String], Option.empty[String])) {
      case (acc @ (_, Some(_)), _)     => acc // stop at the first failure
      case ((lines, None), (table, i)) =>
        if maintenance.active then (lines, Some(s"$table: a restore started; stopped"))
        else
          progress(s"${Vacuum.describe(plan.copy(tables = List(table)))} (${i + 1}/${plan.tables.size})")
          val before = VacuumRepo.state(ds, table)
          val started = System.nanoTime()
          scala.util.Try(VacuumRepo.vacuum(ds, table, plan.analyze)).toEither match
            case Left(e)  => (lines, Some(s"$table: ${e.getMessage}"))
            case Right(_) =>
              val after = VacuumRepo.state(ds, table)
              val ms = (System.nanoTime() - started) / 1000000L
              val line = Vacuum.tableLine(table, before.deadRows, after.deadRows, before.bytes, after.bytes, ms)
              scribe.info(s"[vacuum] $line")
              (lines :+ line, None)
    }
    BackgroundJob.Outcome(Option.when(done.nonEmpty)(done.mkString("; ")), failed)
