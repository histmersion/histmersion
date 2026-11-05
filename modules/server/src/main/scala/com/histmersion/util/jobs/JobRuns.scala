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
  def create(run: RunRow): com.histmersion.util.jobs.JobRuns.CreateResult

  /** [[create]] inside the caller's transaction (a manager's dispatch commits the run with its item). */
  def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.JobRuns.CreateInResult

  def pipelineVersion(id: com.histmersion.util.jobs.JobRuns.PipelineVersionId): Option[com.histmersion.util.jobs.JobRuns.PipelineVersionResult]
  def heartbeat(kind: com.histmersion.util.jobs.JobRuns.HeartbeatKind, id: com.histmersion.util.jobs.JobRuns.HeartbeatId): Unit

  /** How many runs of `kind` are in either of two states. */
  def count(kind: com.histmersion.util.jobs.JobRuns.CountKind, statuses: (com.histmersion.util.jobs.JobRuns.CountStatusesPart1, com.histmersion.util.jobs.JobRuns.CountStatusesPart2)): com.histmersion.util.jobs.JobRuns.CountResult

  /** Compare-and-set `from` → `to`; true when the row moved. */
  def move(
      kind: com.histmersion.util.jobs.JobRuns.MoveKind,
      id: com.histmersion.util.jobs.JobRuns.MoveId,
      from: com.histmersion.util.jobs.JobRuns.MoveFrom,
      to: com.histmersion.util.jobs.JobRuns.MoveTo,
      error: Option[com.histmersion.util.jobs.JobRuns.MoveError],
      progress: Option[com.histmersion.util.jobs.JobRuns.MoveProgress],
      resumeAt: Option[Instant]
  ): com.histmersion.util.jobs.JobRuns.MoveResult

  /** The `paused` runs whose resume time has come by `now`, oldest resume first. */
  def due(kind: com.histmersion.util.jobs.JobRuns.DueKind, paused: com.histmersion.util.jobs.JobRuns.DuePaused, now: Instant): Vector[com.histmersion.util.jobs.JobRuns.DueResult]

  /** The `paused` runs whose error starts with `prefix` and that do not resume by themselves before `now`: (id,
    * pipeline, model, error), the longest waiting first.
    */
  def pausedWith(
      kind: com.histmersion.util.jobs.JobRuns.PausedWithKind,
      paused: com.histmersion.util.jobs.JobRuns.PausedWithPaused,
      prefix: com.histmersion.util.jobs.JobRuns.PausedWithPrefix,
      now: Instant
  ): Vector[(com.histmersion.util.jobs.JobRuns.PausedWithResultPart1, com.histmersion.util.jobs.JobRuns.PausedWithResultPart2, Option[com.histmersion.util.jobs.JobRuns.PausedWithResultPart3], com.histmersion.util.jobs.JobRuns.PausedWithResultPart4)]

  def progress(kind: com.histmersion.util.jobs.JobRuns.ProgressKind, id: com.histmersion.util.jobs.JobRuns.ProgressId, text: com.histmersion.util.jobs.JobRuns.ProgressText): Unit
  def warn(kind: com.histmersion.util.jobs.JobRuns.WarnKind, id: com.histmersion.util.jobs.JobRuns.WarnId, warning: com.histmersion.util.jobs.JobRuns.WarnWarning): Unit
  def budget(kind: com.histmersion.util.jobs.JobRuns.BudgetKind, id: com.histmersion.util.jobs.JobRuns.BudgetId, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRuns.BudgetTokens]): Unit

  /** At a start: the `running` runs (but `except`) paused with `error`, with their origin; the `queued` ones (but
    * `except`), oldest first.
    */
  def reconcile(
      kind: com.histmersion.util.jobs.JobRuns.ReconcileKind,
      running: com.histmersion.util.jobs.JobRuns.ReconcileRunning,
      paused: com.histmersion.util.jobs.JobRuns.ReconcilePaused,
      error: com.histmersion.util.jobs.JobRuns.ReconcileError,
      queued: com.histmersion.util.jobs.JobRuns.ReconcileQueued,
      except: List[com.histmersion.util.jobs.JobRuns.ReconcileExcept]
  ): (Vector[(com.histmersion.util.jobs.JobRuns.ReconcileResultPart1Part1, Option[com.histmersion.util.jobs.JobRuns.ReconcileResultPart1Part2])], Vector[com.histmersion.util.jobs.JobRuns.ReconcileResultPart2])

  /** A `paused` run goes on by itself at `at` (unless it moved on meanwhile). */
  def resumeAt(kind: com.histmersion.util.jobs.JobRuns.ResumeAtKind, id: com.histmersion.util.jobs.JobRuns.ResumeAtId, paused: com.histmersion.util.jobs.JobRuns.ResumeAtPaused, at: Instant): com.histmersion.util.jobs.JobRuns.ResumeAtResult

object JobRuns:
  type CreateResult = CreateResult.T
  object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateInResult = CreateInResult.T
  object CreateInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineVersionResult = PipelineVersionResult.T
  object PipelineVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PipelineVersionId = PipelineVersionId.T
  object PipelineVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeartbeatKind = HeartbeatKind.T
  object HeartbeatKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeartbeatId = HeartbeatId.T
  object HeartbeatId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountKind = CountKind.T
  object CountKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountStatusesPart1 = CountStatusesPart1.T
  object CountStatusesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountStatusesPart2 = CountStatusesPart2.T
  object CountStatusesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveResult = MoveResult.T
  object MoveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveKind = MoveKind.T
  object MoveKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveId = MoveId.T
  object MoveId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveFrom = MoveFrom.T
  object MoveFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveTo = MoveTo.T
  object MoveTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveError = MoveError.T
  object MoveError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveProgress = MoveProgress.T
  object MoveProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueResult = DueResult.T
  object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueKind = DueKind.T
  object DueKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuePaused = DuePaused.T
  object DuePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithResultPart1 = PausedWithResultPart1.T
  object PausedWithResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithResultPart2 = PausedWithResultPart2.T
  object PausedWithResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithResultPart3 = PausedWithResultPart3.T
  object PausedWithResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithResultPart4 = PausedWithResultPart4.T
  object PausedWithResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithKind = PausedWithKind.T
  object PausedWithKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithPaused = PausedWithPaused.T
  object PausedWithPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PausedWithPrefix = PausedWithPrefix.T
  object PausedWithPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressKind = ProgressKind.T
  object ProgressKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressId = ProgressId.T
  object ProgressId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressText = ProgressText.T
  object ProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarnKind = WarnKind.T
  object WarnKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarnId = WarnId.T
  object WarnId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarnWarning = WarnWarning.T
  object WarnWarning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetKind = BudgetKind.T
  object BudgetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetId = BudgetId.T
  object BudgetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetTokens = BudgetTokens.T
  object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileResultPart1Part1 = ReconcileResultPart1Part1.T
  object ReconcileResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileResultPart1Part2 = ReconcileResultPart1Part2.T
  object ReconcileResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileResultPart2 = ReconcileResultPart2.T
  object ReconcileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileKind = ReconcileKind.T
  object ReconcileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileRunning = ReconcileRunning.T
  object ReconcileRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcilePaused = ReconcilePaused.T
  object ReconcilePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileError = ReconcileError.T
  object ReconcileError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileQueued = ReconcileQueued.T
  object ReconcileQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileExcept = ReconcileExcept.T
  object ReconcileExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeAtResult = ResumeAtResult.T
  object ResumeAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeAtKind = ResumeAtKind.T
  object ResumeAtKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeAtId = ResumeAtId.T
  object ResumeAtId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumeAtPaused = ResumeAtPaused.T
  object ResumeAtPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): JobRuns = Implementation(db)

  final private class Implementation(db: Transactor) extends JobRuns:
    def create(run: RunRow): com.histmersion.util.jobs.JobRuns.Implementation.CreateResult = db.transact(JobRepo.insertRun(run))
    def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.JobRuns.Implementation.CreateInResult = JobRepo.insertRun(run)
    def pipelineVersion(id: com.histmersion.util.jobs.JobRuns.Implementation.PipelineVersionId): Option[com.histmersion.util.jobs.JobRuns.Implementation.PipelineVersionResult] = db.read(JobRepo.runPipelineVersion(id))
    def heartbeat(kind: com.histmersion.util.jobs.JobRuns.Implementation.HeartbeatKind, id: com.histmersion.util.jobs.JobRuns.Implementation.HeartbeatId): Unit = db.transact(JobRepo.runHeartbeat(kind, id))
    def count(kind: com.histmersion.util.jobs.JobRuns.Implementation.CountKind, statuses: (com.histmersion.util.jobs.JobRuns.Implementation.CountStatusesPart1, com.histmersion.util.jobs.JobRuns.Implementation.CountStatusesPart2)): com.histmersion.util.jobs.JobRuns.Implementation.CountResult = db.read(JobRepo.runsIn(kind, statuses))
    def move(
        kind: com.histmersion.util.jobs.JobRuns.Implementation.MoveKind,
        id: com.histmersion.util.jobs.JobRuns.Implementation.MoveId,
        from: com.histmersion.util.jobs.JobRuns.Implementation.MoveFrom,
        to: com.histmersion.util.jobs.JobRuns.Implementation.MoveTo,
        error: Option[com.histmersion.util.jobs.JobRuns.Implementation.MoveError],
        progress: Option[com.histmersion.util.jobs.JobRuns.Implementation.MoveProgress],
        resumeAt: Option[Instant]
    ): com.histmersion.util.jobs.JobRuns.Implementation.MoveResult = db.transact(JobRepo.moveRun(kind, id, from, to, error, progress, resumeAt.map(_.toString)))
    def due(kind: com.histmersion.util.jobs.JobRuns.Implementation.DueKind, paused: com.histmersion.util.jobs.JobRuns.Implementation.DuePaused, now: Instant): Vector[com.histmersion.util.jobs.JobRuns.Implementation.DueResult] =
      db.read(JobRepo.runsDue(kind, paused, now.toString))
    def pausedWith(
        kind: com.histmersion.util.jobs.JobRuns.Implementation.PausedWithKind,
        paused: com.histmersion.util.jobs.JobRuns.Implementation.PausedWithPaused,
        prefix: com.histmersion.util.jobs.JobRuns.Implementation.PausedWithPrefix,
        now: Instant
    ): Vector[(com.histmersion.util.jobs.JobRuns.Implementation.PausedWithResultPart1, com.histmersion.util.jobs.JobRuns.Implementation.PausedWithResultPart2, Option[com.histmersion.util.jobs.JobRuns.Implementation.PausedWithResultPart3], com.histmersion.util.jobs.JobRuns.Implementation.PausedWithResultPart4)] =
      db.read(JobRepo.runsPausedLike(kind, paused, s"$prefix%", now.toString))
    def progress(kind: com.histmersion.util.jobs.JobRuns.Implementation.ProgressKind, id: com.histmersion.util.jobs.JobRuns.Implementation.ProgressId, text: com.histmersion.util.jobs.JobRuns.Implementation.ProgressText): Unit = db.transact(JobRepo.runProgress(kind, id, text))
    def warn(kind: com.histmersion.util.jobs.JobRuns.Implementation.WarnKind, id: com.histmersion.util.jobs.JobRuns.Implementation.WarnId, warning: com.histmersion.util.jobs.JobRuns.Implementation.WarnWarning): Unit = db.transact(JobRepo.runWarning(kind, id, warning))
    def budget(kind: com.histmersion.util.jobs.JobRuns.Implementation.BudgetKind, id: com.histmersion.util.jobs.JobRuns.Implementation.BudgetId, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRuns.Implementation.BudgetTokens]): Unit =
      db.transact(JobRepo.runBudget(kind, id, usd, tokens))
    def reconcile(
        kind: com.histmersion.util.jobs.JobRuns.Implementation.ReconcileKind,
        running: com.histmersion.util.jobs.JobRuns.Implementation.ReconcileRunning,
        paused: com.histmersion.util.jobs.JobRuns.Implementation.ReconcilePaused,
        error: com.histmersion.util.jobs.JobRuns.Implementation.ReconcileError,
        queued: com.histmersion.util.jobs.JobRuns.Implementation.ReconcileQueued,
        except: List[com.histmersion.util.jobs.JobRuns.Implementation.ReconcileExcept]
    ): (Vector[(com.histmersion.util.jobs.JobRuns.Implementation.ReconcileResultPart1Part1, Option[com.histmersion.util.jobs.JobRuns.Implementation.ReconcileResultPart1Part2])], Vector[com.histmersion.util.jobs.JobRuns.Implementation.ReconcileResultPart2]) =
      db.transact(JobRepo.runsReconciled(kind, running, paused, error, queued, except))
    def resumeAt(kind: com.histmersion.util.jobs.JobRuns.Implementation.ResumeAtKind, id: com.histmersion.util.jobs.JobRuns.Implementation.ResumeAtId, paused: com.histmersion.util.jobs.JobRuns.Implementation.ResumeAtPaused, at: Instant): com.histmersion.util.jobs.JobRuns.Implementation.ResumeAtResult =
      db.transact(JobRepo.runResumesAt(kind, id, paused, at.toString))

  object Implementation:
    type CreateResult = CreateResult.T
    object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateInResult = CreateInResult.T
    object CreateInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipelineVersionResult = PipelineVersionResult.T
    object PipelineVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipelineVersionId = PipelineVersionId.T
    object PipelineVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatKind = HeartbeatKind.T
    object HeartbeatKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatId = HeartbeatId.T
    object HeartbeatId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountKind = CountKind.T
    object CountKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountStatusesPart1 = CountStatusesPart1.T
    object CountStatusesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountStatusesPart2 = CountStatusesPart2.T
    object CountStatusesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveResult = MoveResult.T
    object MoveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveKind = MoveKind.T
    object MoveKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveId = MoveId.T
    object MoveId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveFrom = MoveFrom.T
    object MoveFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveTo = MoveTo.T
    object MoveTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveError = MoveError.T
    object MoveError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveProgress = MoveProgress.T
    object MoveProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DueResult = DueResult.T
    object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DueKind = DueKind.T
    object DueKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DuePaused = DuePaused.T
    object DuePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart1 = PausedWithResultPart1.T
    object PausedWithResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart2 = PausedWithResultPart2.T
    object PausedWithResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart3 = PausedWithResultPart3.T
    object PausedWithResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart4 = PausedWithResultPart4.T
    object PausedWithResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithKind = PausedWithKind.T
    object PausedWithKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithPaused = PausedWithPaused.T
    object PausedWithPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithPrefix = PausedWithPrefix.T
    object PausedWithPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressKind = ProgressKind.T
    object ProgressKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressId = ProgressId.T
    object ProgressId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressText = ProgressText.T
    object ProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnKind = WarnKind.T
    object WarnKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnId = WarnId.T
    object WarnId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnWarning = WarnWarning.T
    object WarnWarning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetKind = BudgetKind.T
    object BudgetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetId = BudgetId.T
    object BudgetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart1Part1 = ReconcileResultPart1Part1.T
    object ReconcileResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart1Part2 = ReconcileResultPart1Part2.T
    object ReconcileResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart2 = ReconcileResultPart2.T
    object ReconcileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileKind = ReconcileKind.T
    object ReconcileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileRunning = ReconcileRunning.T
    object ReconcileRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcilePaused = ReconcilePaused.T
    object ReconcilePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileError = ReconcileError.T
    object ReconcileError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileQueued = ReconcileQueued.T
    object ReconcileQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileExcept = ReconcileExcept.T
    object ReconcileExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtResult = ResumeAtResult.T
    object ResumeAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtKind = ResumeAtKind.T
    object ResumeAtKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtId = ResumeAtId.T
    object ResumeAtId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtPaused = ResumeAtPaused.T
    object ResumeAtPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rows in memory, stamped by `now` (the database's `now()`), each with the order it was made and last
    * changed in; behaves as the database does.
    */
  final class InMemory(state: AtomicReference[Map[InMemory.StateKey, JobRunState]], now: () => Instant) extends JobRuns:
    private val stamp = java.util.concurrent.atomic.AtomicLong(0L)
    private def touched(r: JobRunState): JobRunState =
      r.modify(_.updated).setTo(stamp.incrementAndGet()).modify(_.updatedAt).setTo(now())
    private def change(kind: com.histmersion.util.jobs.JobRuns.InMemory.ChangeKind, id: com.histmersion.util.jobs.JobRuns.InMemory.ChangeId)(f: JobRunState => JobRunState): com.histmersion.util.jobs.JobRuns.InMemory.ChangeResult =
      val before = state.get
      val after = state.updateAndGet(m => m.get(id).filter(_.row.kind == kind).fold(m)(r => m.updated(id, f(r))))
      before.get(id) != after.get(id)

    /** A run as it stands. */
    def get(id: com.histmersion.util.jobs.JobRuns.InMemory.GetId): Option[JobRunState] = state.get.get(id)

    /** Every run, in the order they were made. */
    def all: List[JobRunState] = state.get.values.toList.sortBy(_.created)

    /** A run's row changed by its owner's own tables (a step recorded): stamped as the database's `updated_at = now()`.
      */
    def stamped(id: com.histmersion.util.jobs.JobRuns.InMemory.StampedId): Unit = state.get.get(id).foreach(r => change(r.row.kind, id)(touched): Unit)

    def create(run: RunRow): com.histmersion.util.jobs.JobRuns.InMemory.CreateResult =
      val id = java.util.UUID.randomUUID().toString
      val n = stamp.incrementAndGet()
      val _ = state.updateAndGet(
        _.updated(id, { val at = now(); JobRunState(id, run, None, None, None, Nil, None, n, n, at, at) })
      )
      id
    def createIn(run: RunRow)(using DbCon): com.histmersion.util.jobs.JobRuns.InMemory.CreateInResult = create(run)
    def pipelineVersion(id: com.histmersion.util.jobs.JobRuns.InMemory.PipelineVersionId): Option[com.histmersion.util.jobs.JobRuns.InMemory.PipelineVersionResult] = get(id).map(_.row.pipelineVersion)
    def heartbeat(kind: com.histmersion.util.jobs.JobRuns.InMemory.HeartbeatKind, id: com.histmersion.util.jobs.JobRuns.InMemory.HeartbeatId): Unit =
      val _ = change(kind, id)(_.modify(_.heartbeatAt).setTo(Some(now())))
    def count(kind: com.histmersion.util.jobs.JobRuns.InMemory.CountKind, statuses: (com.histmersion.util.jobs.JobRuns.InMemory.CountStatusesPart1, com.histmersion.util.jobs.JobRuns.InMemory.CountStatusesPart2)): com.histmersion.util.jobs.JobRuns.InMemory.CountResult =
      state.get.values.count(r => r.row.kind == kind && (r.row.status == statuses._1 || r.row.status == statuses._2))
    def move(
        kind: com.histmersion.util.jobs.JobRuns.InMemory.MoveKind,
        id: com.histmersion.util.jobs.JobRuns.InMemory.MoveId,
        from: com.histmersion.util.jobs.JobRuns.InMemory.MoveFrom,
        to: com.histmersion.util.jobs.JobRuns.InMemory.MoveTo,
        error: Option[com.histmersion.util.jobs.JobRuns.InMemory.MoveError],
        progress: Option[com.histmersion.util.jobs.JobRuns.InMemory.MoveProgress],
        resumeAt: Option[Instant]
    ): com.histmersion.util.jobs.JobRuns.InMemory.MoveResult =
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
    private def mine(kind: com.histmersion.util.jobs.JobRuns.InMemory.MineKind): List[JobRunState] = state.get.values.toList.filter(_.row.kind == kind)
    def due(kind: com.histmersion.util.jobs.JobRuns.InMemory.DueKind, paused: com.histmersion.util.jobs.JobRuns.InMemory.DuePaused, now: Instant): Vector[com.histmersion.util.jobs.JobRuns.InMemory.DueResult] =
      mine(kind)
        .filter(r => r.row.status == paused && r.resumeAt.exists(!_.isAfter(now)))
        .sortBy(r => (r.resumeAt.get, r.created))
        .map(_.id)
        .toVector
    def pausedWith(
        kind: com.histmersion.util.jobs.JobRuns.InMemory.PausedWithKind,
        paused: com.histmersion.util.jobs.JobRuns.InMemory.PausedWithPaused,
        prefix: com.histmersion.util.jobs.JobRuns.InMemory.PausedWithPrefix,
        now: Instant
    ): Vector[(com.histmersion.util.jobs.JobRuns.InMemory.PausedWithResultPart1, com.histmersion.util.jobs.JobRuns.InMemory.PausedWithResultPart2, Option[com.histmersion.util.jobs.JobRuns.InMemory.PausedWithResultPart3], com.histmersion.util.jobs.JobRuns.InMemory.PausedWithResultPart4)] =
      mine(kind)
        .filter(r =>
          r.row.status == paused && r.error.exists(_.startsWith(prefix)) && r.resumeAt.forall(_.isAfter(now))
        )
        .sortBy(r => (r.updated, r.created))
        .map(r => (r.id, r.row.pipeline, r.row.model, r.error.getOrElse("")))
        .toVector
    def progress(kind: com.histmersion.util.jobs.JobRuns.InMemory.ProgressKind, id: com.histmersion.util.jobs.JobRuns.InMemory.ProgressId, text: com.histmersion.util.jobs.JobRuns.InMemory.ProgressText): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.progress).setTo(Some(text))))
    def warn(kind: com.histmersion.util.jobs.JobRuns.InMemory.WarnKind, id: com.histmersion.util.jobs.JobRuns.InMemory.WarnId, warning: com.histmersion.util.jobs.JobRuns.InMemory.WarnWarning): Unit =
      val _ = change(kind, id)(r => touched(r.modify(_.warnings).using(_ :+ warning)))
    def budget(kind: com.histmersion.util.jobs.JobRuns.InMemory.BudgetKind, id: com.histmersion.util.jobs.JobRuns.InMemory.BudgetId, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRuns.InMemory.BudgetTokens]): Unit =
      val _ = change(kind, id)(r =>
        touched(
          r.modify(_.row.budgetUsd)
            .using(b => usd.getOrElse(b))
            .modify(_.row.budgetTokens)
            .using(t => tokens.getOrElse(t))
        )
      )
    def reconcile(
        kind: com.histmersion.util.jobs.JobRuns.InMemory.ReconcileKind,
        running: com.histmersion.util.jobs.JobRuns.InMemory.ReconcileRunning,
        paused: com.histmersion.util.jobs.JobRuns.InMemory.ReconcilePaused,
        error: com.histmersion.util.jobs.JobRuns.InMemory.ReconcileError,
        queued: com.histmersion.util.jobs.JobRuns.InMemory.ReconcileQueued,
        except: List[com.histmersion.util.jobs.JobRuns.InMemory.ReconcileExcept]
    ): (Vector[(com.histmersion.util.jobs.JobRuns.InMemory.ReconcileResultPart1Part1, Option[com.histmersion.util.jobs.JobRuns.InMemory.ReconcileResultPart1Part2])], Vector[com.histmersion.util.jobs.JobRuns.InMemory.ReconcileResultPart2]) =
      val moving = mine(kind).filter(r => r.row.status == running && !except.contains(r.id)).sortBy(_.created)
      moving.foreach(r =>
        change(kind, r.id)(x => touched(x.modify(_.row.status).setTo(paused).modify(_.error).setTo(Some(error)))): Unit
      )
      val waiting = mine(kind).filter(r => r.row.status == queued && !except.contains(r.id)).sortBy(_.created)
      (moving.map(r => (r.id, Some(r.row.origin))).toVector, waiting.map(_.id).toVector)
    def resumeAt(kind: com.histmersion.util.jobs.JobRuns.InMemory.ResumeAtKind, id: com.histmersion.util.jobs.JobRuns.InMemory.ResumeAtId, paused: com.histmersion.util.jobs.JobRuns.InMemory.ResumeAtPaused, at: Instant): com.histmersion.util.jobs.JobRuns.InMemory.ResumeAtResult =
      get(id).exists(_.row.status == paused) && change(kind, id)(r => touched(r.modify(_.resumeAt).setTo(Some(at))))

  object InMemory:
    type StateKey = StateKey.T
    object StateKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ChangeResult = ChangeResult.T
    object ChangeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeKind = ChangeKind.T
    object ChangeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeId = ChangeId.T
    object ChangeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StampedId = StampedId.T
    object StampedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateResult = CreateResult.T
    object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateInResult = CreateInResult.T
    object CreateInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipelineVersionResult = PipelineVersionResult.T
    object PipelineVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PipelineVersionId = PipelineVersionId.T
    object PipelineVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatKind = HeartbeatKind.T
    object HeartbeatKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatId = HeartbeatId.T
    object HeartbeatId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountKind = CountKind.T
    object CountKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountStatusesPart1 = CountStatusesPart1.T
    object CountStatusesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountStatusesPart2 = CountStatusesPart2.T
    object CountStatusesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveResult = MoveResult.T
    object MoveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveKind = MoveKind.T
    object MoveKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveId = MoveId.T
    object MoveId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveFrom = MoveFrom.T
    object MoveFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveTo = MoveTo.T
    object MoveTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveError = MoveError.T
    object MoveError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MoveProgress = MoveProgress.T
    object MoveProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MineKind = MineKind.T
    object MineKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DueResult = DueResult.T
    object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DueKind = DueKind.T
    object DueKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DuePaused = DuePaused.T
    object DuePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart1 = PausedWithResultPart1.T
    object PausedWithResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart2 = PausedWithResultPart2.T
    object PausedWithResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart3 = PausedWithResultPart3.T
    object PausedWithResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithResultPart4 = PausedWithResultPart4.T
    object PausedWithResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithKind = PausedWithKind.T
    object PausedWithKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithPaused = PausedWithPaused.T
    object PausedWithPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PausedWithPrefix = PausedWithPrefix.T
    object PausedWithPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressKind = ProgressKind.T
    object ProgressKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressId = ProgressId.T
    object ProgressId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProgressText = ProgressText.T
    object ProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnKind = WarnKind.T
    object WarnKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnId = WarnId.T
    object WarnId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnWarning = WarnWarning.T
    object WarnWarning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetKind = BudgetKind.T
    object BudgetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetId = BudgetId.T
    object BudgetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart1Part1 = ReconcileResultPart1Part1.T
    object ReconcileResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart1Part2 = ReconcileResultPart1Part2.T
    object ReconcileResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileResultPart2 = ReconcileResultPart2.T
    object ReconcileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileKind = ReconcileKind.T
    object ReconcileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileRunning = ReconcileRunning.T
    object ReconcileRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcilePaused = ReconcilePaused.T
    object ReconcilePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileError = ReconcileError.T
    object ReconcileError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileQueued = ReconcileQueued.T
    object ReconcileQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReconcileExcept = ReconcileExcept.T
    object ReconcileExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtResult = ResumeAtResult.T
    object ResumeAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtKind = ResumeAtKind.T
    object ResumeAtKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtId = ResumeAtId.T
    object ResumeAtId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAtPaused = ResumeAtPaused.T
    object ResumeAtPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
