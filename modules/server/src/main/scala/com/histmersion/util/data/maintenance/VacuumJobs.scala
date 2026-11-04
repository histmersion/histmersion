package com.histmersion.util.data.maintenance

import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.util.data.DataContributions
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{BackgroundJob, WorkCarrier}

import javax.sql.DataSource
import scala.annotation.tailrec
import com.histmersion.api.Api

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
    defaults: () => List[String],
    pins: WorkCarrier = WorkCarrier.none
):
  private val transactor = Transactor(ds)

  /** One vacuum at a time ([[BackgroundJob]]). */
  private val slot = BackgroundJob.Slot()

  /** Is a vacuum job running? A restore waits for it rather than drop tables under it. */
  def isRunning: Boolean = slot.isRunning

  /** The tables a vacuum may name: the registered ones the database has. */
  private def vacuumable: Set[String] =
    transactor.read(VacuumRepo.tables).intersect(registry.tables.map(t => t._2.name.value: String).toSet)

  def start(in: Vacuum.Request): Either[Api.Error, JobInfo] =
    gate.guard("maintenance/vacuum"):
      Vacuum.plan(in, defaults(), vacuumable).flatMap { plan =>
        BackgroundJob.start(
          ds,
          Vacuum.JobKind,
          Some(VacuumPlan.Detail(Some(plan))),
          slot,
          "a vacuum job",
          Some(Backup.CodeMaintenance),
          pins
        )((_, progress) => run(plan, progress))
      }

  private def run(plan: VacuumPlan, progress: String => Unit): BackgroundJob.Outcome =
    @tailrec def loop(rest: List[(String, Int)], lines: List[String]): BackgroundJob.Outcome =
      def outcome(failed: Option[String]) =
        BackgroundJob.Outcome(Option.when(lines.nonEmpty)(lines.mkString("; ")), failed)
      rest match
        case Nil                            => outcome(None)
        case (table, _) :: _ if gate.active => outcome(Some(s"$table: a restore started; stopped"))
        case (table, i) :: tail             =>
          progress(s"${Vacuum.describe(VacuumPlan(plan.analyze, List(table)))} (${i + 1}/${plan.tables.size})")
          val before = transactor.read(VacuumRepo.state(table))
          val started = System.nanoTime()
          scala.util.Try(transactor.autocommit(VacuumRepo.vacuum(table, plan.analyze))).toEither match
            case Left(e)  => outcome(Some(s"$table: ${e.getMessage}"))
            case Right(_) =>
              val after = transactor.read(VacuumRepo.state(table))
              val ms = (System.nanoTime() - started) / 1000000L
              val line = Vacuum.tableLine(table, before.deadRows, after.deadRows, before.bytes, after.bytes, ms)
              scribe.info(s"[vacuum] $line")
              loop(tail, lines :+ line)
    loop(plan.tables.zipWithIndex, Nil)
