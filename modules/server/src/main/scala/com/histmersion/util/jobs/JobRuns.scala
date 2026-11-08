package com.histmersion
package util.jobs

import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.DbCon
import com.histmersion.domain.automation.{Pipeline, Run}

import hearth.kindlings.optics.syntax.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure

/** The rows of the jobs that run as checkpointed steps (agent runs), recorded through the jobs area that owns the
  * `jobs` table: created, moved from state to state by compare-and-set, marked alive, annotated, reconciled at a start.
  * Every operation is about the runs of one `kind`; the states are the owner's words, passed in.
  */
trait JobRuns:
  /** A new run; its id. */
  def create(run: RunRow): Run.Id

  /** [[create]] inside the caller's transaction (a manager's dispatch commits the run with its item). */
  def createIn(run: RunRow)(using DbCon): Run.Id

  def pipelineVersion(id: Run.Id): Option[Pipeline.Version]
  def heartbeat(kind: Job.Kind, id: Run.Id): Unit

  /** How many runs of `kind` are in either of two states. */
  def count(kind: Job.Kind, statuses: (RunRow.Status, RunRow.Status)): RowCount

  /** Compare-and-set `from` → `to`; true when the row moved. */
  def move(
      kind: Job.Kind,
      id: Run.Id,
      from: RunRow.Status,
      to: RunRow.Status,
      error: Option[JobRow.Error],
      progress: Option[JobRow.Progress],
      resumeAt: Option[Instant]
  ): Boolean

  /** The `paused` runs whose resume time has come by `now`, oldest resume first. */
  def due(kind: Job.Kind, paused: RunRow.Status, now: Instant): Vector[Run.Id]

  /** The `paused` runs whose error starts with `prefix` and that do not resume by themselves before `now`: (id,
    * pipeline, model, error), the longest waiting first.
    */
  def pausedWith(
      kind: Job.Kind,
      paused: RunRow.Status,
      prefix: JobRuns.Prefix,
      now: Instant
  ): Vector[(Run.Id, Pipeline.Wire, Option[RunRow.Model], JobRepo.Problem)]

  def progress(kind: Job.Kind, id: Run.Id, text: JobRow.Progress): Unit
  def warn(kind: Job.Kind, id: Run.Id, warning: JobRepo.WarningText): Unit
  def budget(kind: Job.Kind, id: Run.Id, usd: Option[BigDecimal], tokens: Option[JobRepo.TokenBudget]): Unit

  /** At a start: the `running` runs (but `except`) paused with `error`, with their origin; the `queued` ones (but
    * `except`), oldest first.
    */
  def reconcile(
      kind: Job.Kind,
      running: RunRow.Status,
      paused: RunRow.Status,
      error: JobRow.Error,
      queued: RunRow.Status,
      except: List[Run.Id]
  ): (Vector[(Run.Id, Option[RunRow.Origin])], Vector[Run.Id])

  /** A `paused` run goes on by itself at `at` (unless it moved on meanwhile). */
  def resumeAt(kind: Job.Kind, id: Run.Id, paused: RunRow.Status, at: Instant): Boolean

object JobRuns:
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]

  def apply(db: Transactor): JobRuns = Implementation(db)

  final private class Implementation(db: Transactor) extends JobRuns:
    def create(run: RunRow): Run.Id = db.transact(JobRepo.insertRun(run))
    def createIn(run: RunRow)(using DbCon): Run.Id = JobRepo.insertRun(run)
    def pipelineVersion(id: Run.Id): Option[Pipeline.Version] = db.read(JobRepo.runPipelineVersion(id))
    def heartbeat(kind: Job.Kind, id: Run.Id): Unit = db.transact(JobRepo.runHeartbeat(kind, id))
    def count(kind: Job.Kind, statuses: (RunRow.Status, RunRow.Status)): RowCount =
      db.read(JobRepo.runsIn(kind, statuses))
    def move(
        kind: Job.Kind,
        id: Run.Id,
        from: RunRow.Status,
        to: RunRow.Status,
        error: Option[JobRow.Error],
        progress: Option[JobRow.Progress],
        resumeAt: Option[Instant]
    ): Boolean =
      db.transact(JobRepo.moveRun(kind, id, from, to, error, progress, resumeAt.map(t => JobRepo.ResumeAt(t.toString))))
    def due(kind: Job.Kind, paused: RunRow.Status, now: Instant): Vector[Run.Id] =
      db.read(JobRepo.runsDue(kind, paused, JobRepo.DueAt(now.toString)))
    def pausedWith(
        kind: Job.Kind,
        paused: RunRow.Status,
        prefix: Prefix,
        now: Instant
    ): Vector[(Run.Id, Pipeline.Wire, Option[RunRow.Model], JobRepo.Problem)] =
      db.read(JobRepo.runsPausedLike(kind, paused, s"$prefix%", JobRepo.DueAt(now.toString)))
    def progress(kind: Job.Kind, id: Run.Id, text: JobRow.Progress): Unit =
      db.transact(JobRepo.runProgress(kind, id, text))
    def warn(kind: Job.Kind, id: Run.Id, warning: JobRepo.WarningText): Unit =
      db.transact(JobRepo.runWarning(kind, id, warning))
    def budget(kind: Job.Kind, id: Run.Id, usd: Option[BigDecimal], tokens: Option[JobRepo.TokenBudget]): Unit =
      db.transact(JobRepo.runBudget(kind, id, usd, tokens))
    def reconcile(
        kind: Job.Kind,
        running: RunRow.Status,
        paused: RunRow.Status,
        error: JobRow.Error,
        queued: RunRow.Status,
        except: List[Run.Id]
    ): (Vector[(Run.Id, Option[RunRow.Origin])], Vector[Run.Id]) =
      db.transact(JobRepo.runsReconciled(kind, running, paused, error, queued, except))
    def resumeAt(kind: Job.Kind, id: Run.Id, paused: RunRow.Status, at: Instant): Boolean =
      db.transact(JobRepo.runResumesAt(kind, id, paused, JobRepo.ResumeAt(at.toString)))

  /** The same rows in memory, stamped by `now` (the database's `now()`), each with the order it was made and last
    * changed in; behaves as the database does.
    */
  final class InMemory(state: AtomicReference[Map[Run.Id, JobRunState]], now: () => Instant) extends JobRuns:
    private val stamp = java.util.concurrent.atomic.AtomicLong(0L)
    private def touched(r: JobRunState): JobRunState =
      r.modify(_.updated).setTo(JobRunState.Updated(stamp.incrementAndGet())).modify(_.updatedAt).setTo(now())
    private def change(kind: Job.Kind, id: Run.Id)(f: JobRunState => JobRunState): Boolean = changeOf(kind.value, id)(f)
    private def changeOf(kind: String, id: Run.Id)(f: JobRunState => JobRunState): Boolean =
      val before = state.get
      val after = state.updateAndGet(m => m.get(id).filter(_.row.kind.value == kind).fold(m)(r => m.updated(id, f(r))))
      before.get(id) != after.get(id)

    /** A run as it stands. */
    def get(id: Run.Id): Option[JobRunState] = state.get.get(id)

    /** Every run, in the order they were made. */
    def all: List[JobRunState] = state.get.values.toList.sortBy[Long](_.created.value)

    /** A run's row changed by its owner's own tables (a step recorded): stamped as the database's `updated_at = now()`.
      */
    def stamped(id: Run.Id): Unit = state.get.get(id).foreach(r => changeOf(r.row.kind.value, id)(touched): Unit)

    def create(run: RunRow): Run.Id =
      val id = Run.Id.applyUnsafe(java.util.UUID.randomUUID().toString)
      val n = stamp.incrementAndGet()
      val _ = state.updateAndGet(
        _.updated(
          id, {
            val at = now()
            JobRunState(
              JobRunState.Id(id.value),
              run,
              None,
              None,
              None,
              Nil,
              None,
              JobRunState.Created(n),
              JobRunState.Updated(n),
              at,
              at
            )
          }
        )
      )
      id
    def createIn(run: RunRow)(using DbCon): Run.Id = create(run)
    def pipelineVersion(id: Run.Id): Option[Pipeline.Version] = get(id).map(_.row.pipelineVersion)
    def heartbeat(kind: Job.Kind, id: Run.Id): Unit =
      val _ = change(kind, id)(_.modify(_.heartbeatAt).setTo(Some(now())))
    def count(kind: Job.Kind, statuses: (RunRow.Status, RunRow.Status)): RowCount =
      RowCount(
        state.get.values.count(r =>
          r.row.kind.value == kind.value && (r.row.status == statuses._1 || r.row.status == statuses._2)
        )
      )
    def move(
        kind: Job.Kind,
        id: Run.Id,
        from: RunRow.Status,
        to: RunRow.Status,
        error: Option[JobRow.Error],
        progress: Option[JobRow.Progress],
        resumeAt: Option[Instant]
    ): Boolean =
      get(id).exists(_.row.status == from) && change(kind, id) { r =>
        if r.row.status != from then r
        else
          touched(
            r.modify(_.row.status)
              .setTo(to)
              .modify(_.error)
              .setTo(error.map(e => JobRunState.Error(e.value)))
              .modify(_.progress)
              .setTo(progress.map(p => JobRunState.Progress(p.value)))
              .modify(_.resumeAt)
              .setTo(resumeAt)
          )
      }
    private def mine(kind: Job.Kind): List[(Run.Id, JobRunState)] =
      state.get.toList.filter(_._2.row.kind.value == kind.value)
    def due(kind: Job.Kind, paused: RunRow.Status, now: Instant): Vector[Run.Id] =
      mine(kind)
        .filter((_, r) => r.row.status == paused && r.resumeAt.exists(!_.isAfter(now)))
        .sortBy[(Instant, Long)]((_, r) => (r.resumeAt.get, r.created.value))
        .map(_._1)
        .toVector
    def pausedWith(
        kind: Job.Kind,
        paused: RunRow.Status,
        prefix: Prefix,
        now: Instant
    ): Vector[(Run.Id, Pipeline.Wire, Option[RunRow.Model], JobRepo.Problem)] =
      mine(kind)
        .filter((_, r) =>
          r.row.status == paused && r.error.exists(_.value.startsWith(prefix.value)) && r.resumeAt.forall(
            _.isAfter(now)
          )
        )
        .sortBy[(Long, Long)]((_, r) => (r.updated.value, r.created.value))
        .map((id, r) =>
          (id, Pipeline.Wire(r.row.pipeline.value), r.row.model, JobRepo.Problem(r.error.fold("")(_.value)))
        )
        .toVector
    def progress(kind: Job.Kind, id: Run.Id, text: JobRow.Progress): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.progress).setTo(Some(JobRunState.Progress(text.value)))))
    def warn(kind: Job.Kind, id: Run.Id, warning: JobRepo.WarningText): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.warnings).using(_ :+ JobRunState.Warnings(warning.value))))
    def budget(kind: Job.Kind, id: Run.Id, usd: Option[BigDecimal], tokens: Option[JobRepo.TokenBudget]): Unit =
      val _ = change(kind, id)(r =>
        touched(
          r.modify(_.row.budgetUsd)
            .using(b => usd.getOrElse(b))
            .modify(_.row.budgetTokens)
            .using(t => tokens.getOrElse(t))
        )
      )
    def reconcile(
        kind: Job.Kind,
        running: RunRow.Status,
        paused: RunRow.Status,
        error: JobRow.Error,
        queued: RunRow.Status,
        except: List[Run.Id]
    ): (Vector[(Run.Id, Option[RunRow.Origin])], Vector[Run.Id]) =
      val moving = mine(kind)
        .filter((id, r) => r.row.status == running && !except.contains(id))
        .sortBy[Long]((_, r) => r.created.value)
      moving.foreach((id, _) =>
        change(kind, id)(x =>
          touched(x.modify(_.row.status).setTo(paused).modify(_.error).setTo(Some(JobRunState.Error(error.value))))
        ): Unit
      )
      val waiting = mine(kind)
        .filter((id, r) => r.row.status == queued && !except.contains(id))
        .sortBy[Long]((_, r) => r.created.value)
      (moving.map((id, r) => (id, Some(r.row.origin))).toVector, waiting.map(_._1).toVector)
    def resumeAt(kind: Job.Kind, id: Run.Id, paused: RunRow.Status, at: Instant): Boolean =
      get(id).exists(_.row.status == paused) && change(kind, id)(r => touched(r.modify(_.resumeAt).setTo(Some(at))))
