package com.histmersion
package util.data.maintenance

import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.util.data.DataContributions
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{BackgroundJob, WorkCarrier}

import javax.sql.DataSource
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.util.jobs.Job
import com.histmersion.api.ByteCount
import com.histmersion.api.maintenance.Storage

/** `POST /api/maintenance/vacuum`: VACUUM of registered tables as a [[BackgroundJob]] (kind `vacuum`) — a vacuum of
  * `place_names` (3.7 GB) runs for minutes, past any request timeout. It answers at once with the job; `histmersion
  * jobs <id>` follows it, and a restart fails it as `interrupted` like every other unfinished job. One at a time; each
  * table is its own statement outside any transaction, so a failure or a restore stops the job between tables, never
  * inside one.
  */
final class VacuumJobs(
    ds: DataSource,
    gate: WriteGate,
    registry: DataContributions,
    defaults: () => List[Vacuum.Table],
    pins: WorkCarrier = WorkCarrier.none
):
  private val transactor = Transactor(ds)

  /** One vacuum at a time ([[BackgroundJob]]). */
  private val slot = BackgroundJob.Slot()

  /** Is a vacuum job running? A restore waits for it rather than drop tables under it. */
  def isRunning: Boolean = slot.isRunning

  /** The tables a vacuum may name: the registered ones the database has. */
  private def vacuumable: Set[Vacuum.Table] =
    transactor
      .read(VacuumRepo.tables)
      .intersect(registry.tables.map(t => t._2.name.value: String).toSet)
      .map(Vacuum.Table(_))

  def start(in: Vacuum.Request): Either[Api.Error, JobInfo] =
    gate.guard(WriteGate.OperationName("maintenance/vacuum")):
      Vacuum.plan(in, defaults(), vacuumable).flatMap { plan =>
        BackgroundJob.start(
          ds,
          Job.Kind.applyUnsafe(Vacuum.JobKind),
          Some(VacuumPlan.Detail(Some(plan))),
          slot,
          BackgroundJob.Work("a vacuum job"),
          Some(Api.Error.Code(Backup.CodeMaintenance)),
          pins
        )((_, progress) => run(plan, progress))
      }

  private def run(plan: VacuumPlan, progress: JobInfo.Progress => Unit): BackgroundJob.Outcome =
    @tailrec def loop(rest: List[(VacuumPlan.Tables, Int)], lines: List[String]): BackgroundJob.Outcome =
      def outcome(failed: Option[String]) =
        BackgroundJob.Outcome(
          Option.when(lines.nonEmpty)(BackgroundJob.Outcome.Summary(lines.mkString("; "))),
          failed.map(BackgroundJob.Outcome.Error(_))
        )
      rest match
        case Nil                            => outcome(None)
        case (table, _) :: _ if gate.active => outcome(Some(s"$table: a restore started; stopped"))
        case (table, i) :: tail             =>
          progress(
            JobInfo.Progress(
              s"${Vacuum.describe(VacuumPlan(plan.analyze, List(table)))} (${i + 1}/${plan.tables.size})"
            )
          )
          val name = table.value
          val before = transactor.read(VacuumRepo.state(name))
          val started = System.nanoTime()
          scala.util
            .Try(transactor.autocommit(VacuumRepo.vacuum(name, VacuumRepo.Analyze(plan.analyze.value))))
            .toEither match
            case Left(e)  => outcome(Some(s"$table: ${e.getMessage}"))
            case Right(_) =>
              val after = transactor.read(VacuumRepo.state(name))
              val ms = (System.nanoTime() - started) / 1000000L
              val line = Vacuum.tableLine(
                Vacuum.Table(name),
                Vacuum.DeadRows(before.deadRows.value),
                Vacuum.DeadRows(after.deadRows.value),
                ByteCount(before.bytes.value),
                ByteCount(after.bytes.value),
                Storage.Report.DurationMillis(ms)
              )
              scribe.info(s"[vacuum] $line")
              loop(tail, lines :+ line)
    loop(plan.tables.zipWithIndex, Nil)
