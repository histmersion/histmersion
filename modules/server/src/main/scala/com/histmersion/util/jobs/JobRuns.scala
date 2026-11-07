package com.histmersion.util.jobs

import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** The rows of the jobs that run as checkpointed steps (agent runs), recorded through the jobs area that owns the
  * `jobs` table: created, moved from state to state by compare-and-set, marked alive, annotated, reconciled at a start.
  * Every operation is about the runs of one `kind`; the states are the owner's words, passed in.
  */
trait JobRuns:
  /** A new run; its id. */
  def create(run: RunRow): com.histmersion.util.jobs.Job.Id

  /** [[create]] inside the caller's transaction (a manager's dispatch commits the run with its item). */
  def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.Job.Id

  def pipelineVersion(id: com.histmersion.util.jobs.Job.Id): Option[com.histmersion.domain.automation.Pipeline.Version]
  def heartbeat(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id): Unit

  /** How many runs of `kind` are in either of two states. */
  def count(kind: com.histmersion.util.jobs.Job.Kind, statuses: (com.histmersion.util.jobs.JobRow.Status, com.histmersion.util.jobs.JobRow.Status)): com.histmersion.RowCount

  /** Compare-and-set `from` → `to`; true when the row moved. */
  def move(
      kind: com.histmersion.util.jobs.Job.Kind,
      id: com.histmersion.util.jobs.Job.Id,
      from: com.histmersion.util.jobs.JobRow.Status,
      to: com.histmersion.util.jobs.JobRow.Status,
      error: Option[com.histmersion.util.jobs.JobRow.Error],
      progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText],
      resumeAt: Option[Instant]
  ): Boolean

  /** The `paused` runs whose resume time has come by `now`, oldest resume first. */
  def due(kind: com.histmersion.util.jobs.Job.Kind, paused: com.histmersion.util.jobs.JobRow.Status, now: Instant): Vector[com.histmersion.util.jobs.Job.Id]

  /** The `paused` runs whose error starts with `prefix` and that do not resume by themselves before `now`: (id,
    * pipeline, model, error), the longest waiting first.
    */
  def pausedWith(
      kind: com.histmersion.util.jobs.Job.Kind,
      paused: com.histmersion.util.jobs.JobRow.Status,
      prefix: com.histmersion.util.jobs.JobRuns.Prefix,
      now: Instant
  ): Vector[(com.histmersion.util.jobs.Job.Kind, com.histmersion.util.jobs.JobRow.Status, Option[com.histmersion.util.jobs.JobRuns.SqlFragment], com.histmersion.util.jobs.JobRuns.SqlFragment)]

  def progress(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit
  def warn(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, warning: com.histmersion.util.jobs.JobRepo.WarningText): Unit
  def budget(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRepo.TokenBudget]): Unit

  /** At a start: the `running` runs (but `except`) paused with `error`, with their origin; the `queued` ones (but
    * `except`), oldest first.
    */
  def reconcile(
      kind: com.histmersion.util.jobs.Job.Kind,
      running: com.histmersion.util.jobs.JobRow.Status,
      paused: com.histmersion.util.jobs.JobRow.Status,
      error: com.histmersion.util.jobs.JobRow.Error,
      queued: com.histmersion.util.jobs.JobRow.Status,
      except: List[com.histmersion.util.jobs.Job.Id]
  ): (Vector[(com.histmersion.util.jobs.Job.Id, Option[com.histmersion.util.jobs.Job.Id])], Vector[com.histmersion.util.jobs.Job.Id])

  /** A `paused` run goes on by itself at `at` (unless it moved on meanwhile). */
  def resumeAt(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, paused: com.histmersion.util.jobs.JobRow.Status, at: Instant): Boolean

object JobRuns:
  type Prefix = Prefix.T
  object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqlFragment = SqlFragment.T
  object SqlFragment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): JobRuns = Implementation(db)

  final private class Implementation(db: Transactor) extends JobRuns:
    def create(run: RunRow): com.histmersion.util.jobs.Job.Id = db.transact(JobRepo.insertRun(run))
    def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.Job.Id = JobRepo.insertRun(run)
    def pipelineVersion(id: com.histmersion.util.jobs.Job.Id): Option[com.histmersion.domain.automation.Pipeline.Version] = db.read(JobRepo.runPipelineVersion(id))
    def heartbeat(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id): Unit = db.transact(JobRepo.runHeartbeat(kind, id))
    def count(kind: com.histmersion.util.jobs.Job.Kind, statuses: (com.histmersion.util.jobs.JobRow.Status, com.histmersion.util.jobs.JobRow.Status)): com.histmersion.RowCount = db.read(JobRepo.runsIn(kind, statuses))
    def move(
        kind: com.histmersion.util.jobs.Job.Kind,
        id: com.histmersion.util.jobs.Job.Id,
        from: com.histmersion.util.jobs.JobRow.Status,
        to: com.histmersion.util.jobs.JobRow.Status,
        error: Option[com.histmersion.util.jobs.JobRow.Error],
        progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText],
        resumeAt: Option[Instant]
    ): Boolean = db.transact(JobRepo.moveRun(kind, id, from, to, error, progress, resumeAt.map(_.toString)))
    def due(kind: com.histmersion.util.jobs.Job.Kind, paused: com.histmersion.util.jobs.JobRow.Status, now: Instant): Vector[com.histmersion.util.jobs.Job.Id] =
      db.read(JobRepo.runsDue(kind, paused, now.toString))
    def pausedWith(
        kind: com.histmersion.util.jobs.Job.Kind,
        paused: com.histmersion.util.jobs.JobRow.Status,
        prefix: com.histmersion.util.jobs.JobRuns.Prefix,
        now: Instant
    ): Vector[(com.histmersion.util.jobs.Job.Kind, com.histmersion.util.jobs.JobRow.Status, Option[com.histmersion.util.jobs.JobRuns.SqlFragment], com.histmersion.util.jobs.JobRuns.SqlFragment)] =
      db.read(JobRepo.runsPausedLike(kind, paused, s"$prefix%", now.toString))
    def progress(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit = db.transact(JobRepo.runProgress(kind, id, text))
    def warn(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, warning: com.histmersion.util.jobs.JobRepo.WarningText): Unit = db.transact(JobRepo.runWarning(kind, id, warning))
    def budget(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRepo.TokenBudget]): Unit =
      db.transact(JobRepo.runBudget(kind, id, usd, tokens))
    def reconcile(
        kind: com.histmersion.util.jobs.Job.Kind,
        running: com.histmersion.util.jobs.JobRow.Status,
        paused: com.histmersion.util.jobs.JobRow.Status,
        error: com.histmersion.util.jobs.JobRow.Error,
        queued: com.histmersion.util.jobs.JobRow.Status,
        except: List[com.histmersion.util.jobs.Job.Id]
    ): (Vector[(com.histmersion.util.jobs.Job.Id, Option[com.histmersion.util.jobs.Job.Id])], Vector[com.histmersion.util.jobs.Job.Id]) =
      db.transact(JobRepo.runsReconciled(kind, running, paused, error, queued, except))
    def resumeAt(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, paused: com.histmersion.util.jobs.JobRow.Status, at: Instant): Boolean =
      db.transact(JobRepo.runResumesAt(kind, id, paused, at.toString))


  /** The same rows in memory, stamped by `now` (the database's `now()`), each with the order it was made and last
    * changed in; behaves as the database does.
    */
  final class InMemory(state: AtomicReference[Map[InMemory.StateKey, JobRunState]], now: () => Instant) extends JobRuns:
    private val stamp = java.util.concurrent.atomic.AtomicLong(0L)
    private def touched(r: JobRunState): JobRunState =
      r.modify(_.updated).setTo(stamp.incrementAndGet()).modify(_.updatedAt).setTo(now())
    private def change(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id)(f: JobRunState => JobRunState): Boolean =
      val before = state.get
      val after = state.updateAndGet(m => m.get(id).filter(_.row.kind == kind).fold(m)(r => m.updated(id, f(r))))
      before.get(id) != after.get(id)

    /** A run as it stands. */
    def get(id: com.histmersion.util.jobs.Job.Id): Option[JobRunState] = state.get.get(id)

    /** Every run, in the order they were made. */
    def all: List[JobRunState] = state.get.values.toList.sortBy(_.created)

    /** A run's row changed by its owner's own tables (a step recorded): stamped as the database's `updated_at = now()`.
      */
    def stamped(id: com.histmersion.util.jobs.Job.Id): Unit = state.get.get(id).foreach(r => change(r.row.kind, id)(touched): Unit)

    def create(run: RunRow): com.histmersion.util.jobs.Job.Id =
      val id = java.util.UUID.randomUUID().toString
      val n = stamp.incrementAndGet()
      val _ = state.updateAndGet(
        _.updated(id, { val at = now(); JobRunState(id, run, None, None, None, Nil, None, n, n, at, at) })
      )
      id
    def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.Job.Id = create(run)
    def pipelineVersion(id: com.histmersion.util.jobs.Job.Id): Option[com.histmersion.domain.automation.Pipeline.Version] = get(id).map(_.row.pipelineVersion)
    def heartbeat(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id): Unit =
      val _ = change(kind, id)(_.modify(_.heartbeatAt).setTo(Some(now())))
    def count(kind: com.histmersion.util.jobs.Job.Kind, statuses: (com.histmersion.util.jobs.JobRow.Status, com.histmersion.util.jobs.JobRow.Status)): com.histmersion.RowCount =
      state.get.values.count(r => r.row.kind == kind && (r.row.status == statuses._1 || r.row.status == statuses._2))
    def move(
        kind: com.histmersion.util.jobs.Job.Kind,
        id: com.histmersion.util.jobs.Job.Id,
        from: com.histmersion.util.jobs.JobRow.Status,
        to: com.histmersion.util.jobs.JobRow.Status,
        error: Option[com.histmersion.util.jobs.JobRow.Error],
        progress: Option[com.histmersion.util.jobs.JobRepo.ProgressText],
        resumeAt: Option[Instant]
    ): Boolean =
      get(id).exists(_.row.status == from) && change(kind, id) { r =>
        if r.row.status != from then r
        else
          touched(
            r.modify(_.row.status)
              .setTo(to)
              .modify(_.error)
              .setTo(error)
              .modify(_.progress)
              .setTo(progress)
              .modify(_.resumeAt)
              .setTo(resumeAt)
          )
      }
    private def mine(kind: com.histmersion.util.jobs.Job.Kind): List[JobRunState] = state.get.values.toList.filter(_.row.kind == kind)
    def due(kind: com.histmersion.util.jobs.Job.Kind, paused: com.histmersion.util.jobs.JobRow.Status, now: Instant): Vector[com.histmersion.util.jobs.Job.Id] =
      mine(kind)
        .filter(r => r.row.status == paused && r.resumeAt.exists(!_.isAfter(now)))
        .sortBy(r => (r.resumeAt.get, r.created))
        .map(_.id)
        .toVector
    def pausedWith(
        kind: com.histmersion.util.jobs.Job.Kind,
        paused: com.histmersion.util.jobs.JobRow.Status,
        prefix: com.histmersion.util.jobs.JobRuns.Prefix,
        now: Instant
    ): Vector[(com.histmersion.util.jobs.Job.Kind, com.histmersion.util.jobs.JobRow.Status, Option[com.histmersion.util.jobs.JobRuns.SqlFragment], com.histmersion.util.jobs.JobRuns.SqlFragment)] =
      mine(kind)
        .filter(r =>
          r.row.status == paused && r.error.exists(_.startsWith(prefix)) && r.resumeAt.forall(_.isAfter(now))
        )
        .sortBy(r => (r.updated, r.created))
        .map(r => (r.id, r.row.pipeline, r.row.model, r.error.getOrElse("")))
        .toVector
    def progress(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, text: com.histmersion.util.jobs.JobRepo.ProgressText): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.progress).setTo(Some(text))))
    def warn(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, warning: com.histmersion.util.jobs.JobRepo.WarningText): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.warnings).using(_ :+ warning)))
    def budget(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRepo.TokenBudget]): Unit =
      val _ = change(kind, id)(r =>
        touched(
          r.modify(_.row.budgetUsd)
            .using(b => usd.getOrElse(b))
            .modify(_.row.budgetTokens)
            .using(t => tokens.getOrElse(t))
        )
      )
    def reconcile(
        kind: com.histmersion.util.jobs.Job.Kind,
        running: com.histmersion.util.jobs.JobRow.Status,
        paused: com.histmersion.util.jobs.JobRow.Status,
        error: com.histmersion.util.jobs.JobRow.Error,
        queued: com.histmersion.util.jobs.JobRow.Status,
        except: List[com.histmersion.util.jobs.Job.Id]
    ): (Vector[(com.histmersion.util.jobs.Job.Id, Option[com.histmersion.util.jobs.Job.Id])], Vector[com.histmersion.util.jobs.Job.Id]) =
      val moving = mine(kind).filter(r => r.row.status == running && !except.contains(r.id)).sortBy(_.created)
      moving.foreach(r =>
        change(kind, r.id)(x => touched(x.modify(_.row.status).setTo(paused).modify(_.error).setTo(Some(error)))): Unit
      )
      val waiting = mine(kind).filter(r => r.row.status == queued && !except.contains(r.id)).sortBy(_.created)
      (moving.map(r => (r.id, Some(r.row.origin))).toVector, waiting.map(_.id).toVector)
    def resumeAt(kind: com.histmersion.util.jobs.Job.Kind, id: com.histmersion.util.jobs.Job.Id, paused: com.histmersion.util.jobs.JobRow.Status, at: Instant): Boolean =
      get(id).exists(_.row.status == paused) && change(kind, id)(r => touched(r.modify(_.resumeAt).setTo(Some(at))))

  object InMemory:
    type StateKey = StateKey.T
    object StateKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

