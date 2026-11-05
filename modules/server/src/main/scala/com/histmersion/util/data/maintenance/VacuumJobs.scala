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
    defaults: () => List[VacuumJobs.DefaultsResult],
    pins: WorkCarrier = WorkCarrier.none
):
  private val transactor = Transactor(ds)

  /** One vacuum at a time ([[BackgroundJob]]). */
  private val slot = BackgroundJob.Slot()

  /** Is a vacuum job running? A restore waits for it rather than drop tables under it. */
  def isRunning: com.histmersion.util.data.maintenance.VacuumJobs.IsRunningResult = slot.isRunning

  /** The tables a vacuum may name: the registered ones the database has. */
  private def vacuumable: Set[com.histmersion.util.data.maintenance.VacuumJobs.VacuumableResult] =
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

  private def run(plan: VacuumPlan, progress: com.histmersion.util.data.maintenance.VacuumJobs.RunProgressArg1 => Unit): BackgroundJob.Outcome =
    @tailrec def loop(rest: List[(com.histmersion.util.data.maintenance.VacuumJobs.RunLoopRestPart1, com.histmersion.util.data.maintenance.VacuumJobs.RunLoopRestPart2)], lines: List[com.histmersion.util.data.maintenance.VacuumJobs.RunLoopLines]): BackgroundJob.Outcome =
      def outcome(failed: Option[com.histmersion.util.data.maintenance.VacuumJobs.RunLoopOutcomeFailed]) =
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

object VacuumJobs:
  type RunLoopRestPart1 = RunLoopRestPart1.T
  object RunLoopRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunLoopRestPart2 = RunLoopRestPart2.T
  object RunLoopRestPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunLoopLines = RunLoopLines.T
  object RunLoopLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunLoopOutcomeFailed = RunLoopOutcomeFailed.T
  object RunLoopOutcomeFailed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DefaultsResult = DefaultsResult.T
  object DefaultsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsRunningResult = IsRunningResult.T
  object IsRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VacuumableResult = VacuumableResult.T
  object VacuumableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunProgressArg1 = RunProgressArg1.T
  object RunProgressArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
