package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{JobRepo, VacuumRepo}

import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** `POST /api/maintenance/vacuum` (#1445): VACUUM as a `jobs` row (kind `vacuum`), on its own daemon
  * thread — a vacuum of `place_names` (3.7 GB) runs for minutes, past any request timeout. It answers
  * at once with the job; `histmersion jobs <id>` follows it, and a restart fails it as `interrupted`
  * like every other unfinished job. One at a time; each table is its own statement outside any
  * transaction, so a failure or a restore stops the job between tables, never inside one. */
final class VacuumJobs(ds: DataSource, maintenance: Maintenance, defaults: () => List[String]):

  /** The id of the running job; `None` when idle. */
  private val running = AtomicReference[Option[String]](None)

  /** Is a vacuum job running? A restore waits for it rather than drop tables under it. */
  def isRunning: Boolean = running.get().isDefined

  def start(in: VacuumRequest): Either[ApiError, JobInfo] =
    maintenance.guard("maintenance/vacuum"):
      Vacuum.plan(in, defaults(), VacuumRepo.tables(ds)).flatMap { plan =>
        if !running.compareAndSet(None, Some("starting")) then
          Left(ApiError(s"a vacuum job is already running (${running.get.getOrElse("?")}) — see `histmersion jobs`",
            Some(Backup.CodeMaintenance)))
        else
          val job = JobRepo.create(ds, Vacuum.JobKind, None, None, detail = Some(JobDetail(vacuum = Some(plan))))
          running.set(Some(job.id))
          val t = Thread.ofPlatform().daemon(true).name("histmersion-vacuum").unstarted { () =>
            try run(job.id, plan)
            catch
              case e: Throwable =>
                scribe.error(s"vacuum job ${job.id} died: ${e.getMessage}", e)
                val _ = JobRepo.transition(ds, job.id, JobState.Running, JobState.Failed,
                  error = Some(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
            finally running.set(None)
          }
          t.start()
          Right(JobRepo.get(ds, job.id).getOrElse(job))
      }

  private def run(id: String, plan: VacuumPlan): Unit =
    val _ = JobRepo.transition(ds, id, JobState.Queued, JobState.Running, Some("starting"))
    val (done, failed) = plan.tables.zipWithIndex.foldLeft((List.empty[String], Option.empty[String])) {
      case (acc @ (_, Some(_)), _) => acc                       // stop at the first failure
      case ((lines, None), (table, i)) =>
        if maintenance.active then (lines, Some(s"$table: a restore started; stopped"))
        else
          JobRepo.progress(ds, id, s"${Vacuum.describe(plan.copy(tables = List(table)))} (${i + 1}/${plan.tables.size})")
          val before  = VacuumRepo.state(ds, table)
          val started = System.nanoTime()
          scala.util.Try(VacuumRepo.vacuum(ds, table, plan.analyze)).toEither match
            case Left(e) => (lines, Some(s"$table: ${e.getMessage}"))
            case Right(_) =>
              val after = VacuumRepo.state(ds, table)
              val ms    = (System.nanoTime() - started) / 1000000L
              val line  = Vacuum.tableLine(table, before.deadRows, after.deadRows, before.bytes, after.bytes, ms)
              scribe.info(s"[vacuum] $line")
              (lines :+ line, None)
    }
    val summary = if done.isEmpty then None else Some(done.mkString("; "))
    val _ = failed match
      case None    => JobRepo.transition(ds, id, JobState.Running, JobState.Done, summary)
      case Some(e) => JobRepo.transition(ds, id, JobState.Running, JobState.Failed, summary, Some(e))
