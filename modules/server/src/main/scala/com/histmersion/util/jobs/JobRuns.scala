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
  def create(run: RunRow): String

  /** [[create]] inside the caller's transaction (a manager's dispatch commits the run with its item). */
  def createIn(run: RunRow)(using DbCon): String

  def pipelineVersion(id: String): Option[Int]
  def heartbeat(kind: String, id: String): Unit

  /** How many runs of `kind` are in either of two states. */
  def count(kind: String, statuses: (String, String)): Int

  /** Compare-and-set `from` → `to`; true when the row moved. */
  def move(
      kind: String,
      id: String,
      from: String,
      to: String,
      error: Option[String],
      progress: Option[String],
      resumeAt: Option[Instant]
  ): Boolean

  /** The `paused` runs whose resume time has come by `now`, oldest resume first. */
  def due(kind: String, paused: String, now: Instant): Vector[String]

  /** The `paused` runs whose error starts with `prefix` and that do not resume by themselves before `now`: (id,
    * pipeline, model, error), the longest waiting first.
    */
  def pausedWith(
      kind: String,
      paused: String,
      prefix: String,
      now: Instant
  ): Vector[(String, String, Option[String], String)]

  def progress(kind: String, id: String, text: String): Unit
  def warn(kind: String, id: String, warning: String): Unit
  def budget(kind: String, id: String, usd: Option[BigDecimal], tokens: Option[Long]): Unit

  /** At a start: the `running` runs (but `except`) paused with `error`, with their origin; the `queued` ones (but
    * `except`), oldest first.
    */
  def reconcile(
      kind: String,
      running: String,
      paused: String,
      error: String,
      queued: String,
      except: List[String]
  ): (Vector[(String, Option[String])], Vector[String])

  /** A `paused` run goes on by itself at `at` (unless it moved on meanwhile). */
  def resumeAt(kind: String, id: String, paused: String, at: Instant): Boolean

object JobRuns:
  def apply(db: Transactor): JobRuns = Implementation(db)

  final private class Implementation(db: Transactor) extends JobRuns:
    def create(run: RunRow): String = db.transact(JobRepo.insertRun(run))
    def createIn(run: RunRow)(using DbCon): String = JobRepo.insertRun(run)
    def pipelineVersion(id: String): Option[Int] = db.read(JobRepo.runPipelineVersion(id))
    def heartbeat(kind: String, id: String): Unit = db.transact(JobRepo.runHeartbeat(kind, id))
    def count(kind: String, statuses: (String, String)): Int = db.read(JobRepo.runsIn(kind, statuses))
    def move(
        kind: String,
        id: String,
        from: String,
        to: String,
        error: Option[String],
        progress: Option[String],
        resumeAt: Option[Instant]
    ): Boolean = db.transact(JobRepo.moveRun(kind, id, from, to, error, progress, resumeAt.map(_.toString)))
    def due(kind: String, paused: String, now: Instant): Vector[String] =
      db.read(JobRepo.runsDue(kind, paused, now.toString))
    def pausedWith(
        kind: String,
        paused: String,
        prefix: String,
        now: Instant
    ): Vector[(String, String, Option[String], String)] =
      db.read(JobRepo.runsPausedLike(kind, paused, s"$prefix%", now.toString))
    def progress(kind: String, id: String, text: String): Unit = db.transact(JobRepo.runProgress(kind, id, text))
    def warn(kind: String, id: String, warning: String): Unit = db.transact(JobRepo.runWarning(kind, id, warning))
    def budget(kind: String, id: String, usd: Option[BigDecimal], tokens: Option[Long]): Unit =
      db.transact(JobRepo.runBudget(kind, id, usd, tokens))
    def reconcile(
        kind: String,
        running: String,
        paused: String,
        error: String,
        queued: String,
        except: List[String]
    ): (Vector[(String, Option[String])], Vector[String]) =
      db.transact(JobRepo.runsReconciled(kind, running, paused, error, queued, except))
    def resumeAt(kind: String, id: String, paused: String, at: Instant): Boolean =
      db.transact(JobRepo.runResumesAt(kind, id, paused, at.toString))

  /** The same rows in memory, stamped by `now` (the database's `now()`), each with the order it was made and last
    * changed in; behaves as the database does.
    */
  final class InMemory(state: AtomicReference[Map[String, JobRunState]], now: () => Instant) extends JobRuns:
    private val stamp = java.util.concurrent.atomic.AtomicLong(0L)
    private def touched(r: JobRunState): JobRunState =
      r.modify(_.updated).setTo(stamp.incrementAndGet()).modify(_.updatedAt).setTo(now())
    private def change(kind: String, id: String)(f: JobRunState => JobRunState): Boolean =
      val before = state.get
      val after = state.updateAndGet(m => m.get(id).filter(_.row.kind == kind).fold(m)(r => m.updated(id, f(r))))
      before.get(id) != after.get(id)

    /** A run as it stands. */
    def get(id: String): Option[JobRunState] = state.get.get(id)

    /** Every run, in the order they were made. */
    def all: List[JobRunState] = state.get.values.toList.sortBy(_.created)

    def create(run: RunRow): String =
      val id = java.util.UUID.randomUUID().toString
      val n = stamp.incrementAndGet()
      val _ = state.updateAndGet(
        _.updated(id, { val at = now(); JobRunState(id, run, None, None, None, Nil, None, n, n, at, at) })
      )
      id
    def createIn(run: RunRow)(using DbCon): String = create(run)
    def pipelineVersion(id: String): Option[Int] = get(id).map(_.row.pipelineVersion)
    def heartbeat(kind: String, id: String): Unit =
      val _ = change(kind, id)(_.modify(_.heartbeatAt).setTo(Some(now())))
    def count(kind: String, statuses: (String, String)): Int =
      state.get.values.count(r => r.row.kind == kind && (r.row.status == statuses._1 || r.row.status == statuses._2))
    def move(
        kind: String,
        id: String,
        from: String,
        to: String,
        error: Option[String],
        progress: Option[String],
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
    private def mine(kind: String): List[JobRunState] = state.get.values.toList.filter(_.row.kind == kind)
    def due(kind: String, paused: String, now: Instant): Vector[String] =
      mine(kind)
        .filter(r => r.row.status == paused && r.resumeAt.exists(!_.isAfter(now)))
        .sortBy(r => (r.resumeAt.get, r.created))
        .map(_.id)
        .toVector
    def pausedWith(
        kind: String,
        paused: String,
        prefix: String,
        now: Instant
    ): Vector[(String, String, Option[String], String)] =
      mine(kind)
        .filter(r =>
          r.row.status == paused && r.error.exists(_.startsWith(prefix)) && r.resumeAt.forall(_.isAfter(now))
        )
        .sortBy(r => (r.updated, r.created))
        .map(r => (r.id, r.row.pipeline, r.row.model, r.error.getOrElse("")))
        .toVector
    def progress(kind: String, id: String, text: String): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.progress).setTo(Some(text))))
    def warn(kind: String, id: String, warning: String): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.warnings).using(_ :+ warning)))
    def budget(kind: String, id: String, usd: Option[BigDecimal], tokens: Option[Long]): Unit =
      val _ = change(kind, id)(r =>
        touched(
          r.modify(_.row.budgetUsd)
            .using(b => usd.getOrElse(b))
            .modify(_.row.budgetTokens)
            .using(t => tokens.getOrElse(t))
        )
      )
    def reconcile(
        kind: String,
        running: String,
        paused: String,
        error: String,
        queued: String,
        except: List[String]
    ): (Vector[(String, Option[String])], Vector[String]) =
      val moving = mine(kind).filter(r => r.row.status == running && !except.contains(r.id)).sortBy(_.created)
      moving.foreach(r =>
        change(kind, r.id)(x => touched(x.modify(_.row.status).setTo(paused).modify(_.error).setTo(Some(error)))): Unit
      )
      val waiting = mine(kind).filter(r => r.row.status == queued && !except.contains(r.id)).sortBy(_.created)
      (moving.map(r => (r.id, Some(r.row.origin))).toVector, waiting.map(_.id).toVector)
    def resumeAt(kind: String, id: String, paused: String, at: Instant): Boolean =
      get(id).exists(_.row.status == paused) && change(kind, id)(r => touched(r.modify(_.resumeAt).setTo(Some(at))))
