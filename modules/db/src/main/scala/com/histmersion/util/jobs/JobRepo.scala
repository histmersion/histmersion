package com.histmersion.util.jobs

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.{Jsonb, Keyset, ListSql, Param, Sql}
import com.histmersion.api.automation.Agents
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult}
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}

/** OCR jobs (plan §1.8). The row is the durable part of a job; the queue itself is in memory, which is precisely why a
  * restart must reconcile: [[failInterrupted]] turns every `running` row into `failed: interrupted` at startup, so no
  * job is left claiming to be in progress by a process that no longer exists. Re-running is idempotent — the same file
  * has the same sha256.
  */
object JobRepo:

  /** ONE projection, reused by both reads (AGENTS.md: no duplicated column lists). Magnum's `sql""` cannot splice a
    * `Frag`, so composition goes through the module's own [[Sql]] fragment type.
    */
  type FacetsFromExcept = FacetsFromExcept.T
  object FacetsFromExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type JsonResult = JsonResult.T
  object JsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateKind = CreateKind.T
  object CreateKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateCitekey = CreateCitekey.T
  object CreateCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateSha256 = CreateSha256.T
  object CreateSha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateAcquireItem = CreateAcquireItem.T
  object CreateAcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetDetailId = SetDetailId.T
  object SetDetailId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListStatus = ListStatus.T
  object ListStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResultPart1 = PageResultPart1.T
  object PageResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResultPart2Part2 = PageResultPart2Part2.T
  object PageResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageStatus = PageStatus.T
  object PageStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLimit = PageLimit.T
  object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBefore = PageBefore.T
  object PageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageUpTo = PageUpTo.T
  object PageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatesResultPart1 = StatesResultPart1.T
  object StatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatesResultPart2 = StatesResultPart2.T
  object StatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateCountsResultPart1 = StateCountsResultPart1.T
  object StateCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateCountsResultPart2 = StateCountsResultPart2.T
  object StateCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsResultPart1Part1 = FacetsResultPart1Part1.T
  object FacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsResultPart1Part2 = FacetsResultPart1Part2.T
  object FacetsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsResultPart2Part1 = FacetsResultPart2Part1.T
  object FacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsResultPart2Part2Part1 = FacetsResultPart2Part2Part1.T
  object FacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsResultPart2Part2Part2 = FacetsResultPart2Part2Part2.T
  object FacetsResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsFacetMax = FacetsFacetMax.T
  object FacetsFacetMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressId = ProgressId.T
  object ProgressId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressText = ProgressText.T
  object ProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TransitionId = TransitionId.T
  object TransitionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TransitionProgress = TransitionProgress.T
  object TransitionProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TransitionError = TransitionError.T
  object TransitionError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailInterruptedResult = FailInterruptedResult.T
  object FailInterruptedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailInterruptedExcept = FailInterruptedExcept.T
  object FailInterruptedExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InsertRunResult = InsertRunResult.T
  object InsertRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunPipelineVersionResult = RunPipelineVersionResult.T
  object RunPipelineVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunPipelineVersionId = RunPipelineVersionId.T
  object RunPipelineVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunHeartbeatKind = RunHeartbeatKind.T
  object RunHeartbeatKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunHeartbeatId = RunHeartbeatId.T
  object RunHeartbeatId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsInResult = RunsInResult.T
  object RunsInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsInKind = RunsInKind.T
  object RunsInKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsInStatusesPart1 = RunsInStatusesPart1.T
  object RunsInStatusesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsInStatusesPart2 = RunsInStatusesPart2.T
  object RunsInStatusesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunResult = MoveRunResult.T
  object MoveRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunKind = MoveRunKind.T
  object MoveRunKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunId = MoveRunId.T
  object MoveRunId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunFrom = MoveRunFrom.T
  object MoveRunFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunTo = MoveRunTo.T
  object MoveRunTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunError = MoveRunError.T
  object MoveRunError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunProgress = MoveRunProgress.T
  object MoveRunProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveRunResumeAt = MoveRunResumeAt.T
  object MoveRunResumeAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsDueResult = RunsDueResult.T
  object RunsDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsDueKind = RunsDueKind.T
  object RunsDueKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsDuePaused = RunsDuePaused.T
  object RunsDuePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsDueAt = RunsDueAt.T
  object RunsDueAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeResultPart1 = RunsPausedLikeResultPart1.T
  object RunsPausedLikeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeResultPart2 = RunsPausedLikeResultPart2.T
  object RunsPausedLikeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeResultPart3 = RunsPausedLikeResultPart3.T
  object RunsPausedLikeResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeResultPart4 = RunsPausedLikeResultPart4.T
  object RunsPausedLikeResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeKind = RunsPausedLikeKind.T
  object RunsPausedLikeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikePaused = RunsPausedLikePaused.T
  object RunsPausedLikePaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeLike = RunsPausedLikeLike.T
  object RunsPausedLikeLike extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsPausedLikeAt = RunsPausedLikeAt.T
  object RunsPausedLikeAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunProgressKind = RunProgressKind.T
  object RunProgressKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunProgressId = RunProgressId.T
  object RunProgressId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunProgressText = RunProgressText.T
  object RunProgressText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunWarningKind = RunWarningKind.T
  object RunWarningKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunWarningId = RunWarningId.T
  object RunWarningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunWarningW = RunWarningW.T
  object RunWarningW extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunBudgetKind = RunBudgetKind.T
  object RunBudgetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunBudgetId = RunBudgetId.T
  object RunBudgetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunBudgetTokens = RunBudgetTokens.T
  object RunBudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledResultPart1Part1 = RunsReconciledResultPart1Part1.T
  object RunsReconciledResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledResultPart1Part2 = RunsReconciledResultPart1Part2.T
  object RunsReconciledResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledResultPart2 = RunsReconciledResultPart2.T
  object RunsReconciledResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledKind = RunsReconciledKind.T
  object RunsReconciledKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledRunning = RunsReconciledRunning.T
  object RunsReconciledRunning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledPaused = RunsReconciledPaused.T
  object RunsReconciledPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledError = RunsReconciledError.T
  object RunsReconciledError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledQueued = RunsReconciledQueued.T
  object RunsReconciledQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsReconciledExcept = RunsReconciledExcept.T
  object RunsReconciledExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResumesAtResult = RunResumesAtResult.T
  object RunResumesAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResumesAtKind = RunResumesAtKind.T
  object RunResumesAtKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResumesAtId = RunResumesAtId.T
  object RunResumesAtId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResumesAtPaused = RunResumesAtPaused.T
  object RunResumesAtPaused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResumesAtAt = RunResumesAtAt.T
  object RunResumesAtAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartMergeApplyResult = StartMergeApplyResult.T
  object StartMergeApplyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartMergeApplyBundle = StartMergeApplyBundle.T
  object StartMergeApplyBundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartMergeApplyPlanHash = StartMergeApplyPlanHash.T
  object StartMergeApplyPlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishMergeApplyId = FinishMergeApplyId.T
  object FinishMergeApplyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishMergeApplyOutcomePart1 = FinishMergeApplyOutcomePart1.T
  object FinishMergeApplyOutcomePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeApplyId = MergeApplyId.T
  object MergeApplyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeApplyRunningResult = MergeApplyRunningResult.T
  object MergeApplyRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val select = Sql.raw("""SELECT id, kind, status, citekey, progress, error, sha256,
              to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              acquire_item, detail::text
       FROM jobs""")

  /** A stored detail this build cannot read (written by a newer one) is left out, not fatal. */
  private def toInfo(r: JobRow): JobInfo =
    JobInfo(
      com.histmersion.api.SpatialHierarchyResult.Job.Id(r.id),
      com.histmersion.api.jobs.JobKinds.Wire(r.kind),
      r.status,
      (r.citekey).map(com.histmersion.api.library.Citekey.apply),
      r.progress,
      r.error,
      (r.sha256).map(com.histmersion.api.Sha256.apply),
      r.createdAt,
      r.updatedAt,
      r.acquireItem,
      r.detail.flatMap(Jsonb.read[JobDetail])
    )

  private def json(d: JobDetail): com.histmersion.util.jobs.JobRepo.JsonResult = Jsonb.write(d)

  /** Create a `queued` job. The id is opaque to clients; a UUID keeps it unguessable and unique. `acquireItem` is the
    * acquisition item the job ticks off when it succeeds; `detail` what the job is about beyond its citekey.
    */
  def create(
      kind: com.histmersion.util.jobs.JobRepo.CreateKind,
      citekey: Option[com.histmersion.util.jobs.JobRepo.CreateCitekey],
      sha256: Option[com.histmersion.util.jobs.JobRepo.CreateSha256],
      acquireItem: Option[com.histmersion.util.jobs.JobRepo.CreateAcquireItem] = None,
      detail: Option[JobDetail] = None
  )(using DbCon): JobInfo =
    val id = java.util.UUID.randomUUID().toString
    val js = detail.map(json)
    sql"""INSERT INTO jobs (id, kind, citekey, status, sha256, acquire_item, detail)
          VALUES ($id, $kind, $citekey, ${JobState.Queued.wire}, $sha256, $acquireItem, $js::jsonb)""".update.run()
    get(id).getOrElse(
      JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id(id), com.histmersion.api.jobs.JobKinds.Wire(kind), JobState.Queued.wire, (citekey).map(com.histmersion.api.library.Citekey.apply), None, None, (sha256).map(com.histmersion.api.Sha256.apply), acquireItem = acquireItem, detail = detail)
    )

  /** Replace a job's [[JobDetail]] — e.g. an init job adding the periods it skipped. */
  def setDetail(id: com.histmersion.util.jobs.JobRepo.SetDetailId, detail: JobDetail)(using DbCon): Unit =
    val js = json(detail)
    val _ = (sql"UPDATE jobs SET detail = $js::jsonb WHERE id = $id".update.run())

  def get(id: com.histmersion.util.jobs.JobRepo.GetId)(using DbCon): Option[JobInfo] =
    (select ++ Sql.of(" WHERE id = ?", Param.S(id))).frag.query[JobRow].run().headOption.map(toInfo)

  /** Newest first; `status` (when given) narrows to one state. */
  def list(status: Option[com.histmersion.util.jobs.JobRepo.ListStatus], limit: com.histmersion.util.jobs.JobRepo.ListLimit)(using DbCon): Vector[JobInfo] =
    val where = status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => Sql.of(" WHERE status = ?", Param.S(s))
      case None    => Sql.empty
    val q = select ++ where ++ Keyset.orderBy("") ++ Sql.of(" LIMIT ?", Param.I(math.max(1, limit)))
    (q.frag.query[JobRow].run().map(toInfo))

  /** One page of the newest-first list ([[com.histmersion.api.Paging]]): `before` = only jobs older than that one,
    * `upTo` = only jobs down to and including it. Left: a cursor that names no job. Returns the page and whether older
    * jobs exist.
    */
  def page(
      status: Option[com.histmersion.util.jobs.JobRepo.PageStatus],
      limit: com.histmersion.util.jobs.JobRepo.PageLimit,
      before: Option[com.histmersion.util.jobs.JobRepo.PageBefore] = None,
      upTo: Option[com.histmersion.util.jobs.JobRepo.PageUpTo] = None
  )(using DbCon): Either[com.histmersion.util.jobs.JobRepo.PageResultPart1, (Vector[JobInfo], com.histmersion.util.jobs.JobRepo.PageResultPart2Part2)] =
    page(JobQuery(status = status.map(_.trim.toLowerCase).filter(_.nonEmpty).toList), limit, before, upTo)

  /** The job queue by state, on the caller's connection (the jobs' figures). */
  def states(using DbCon): List[(com.histmersion.util.jobs.JobRepo.StatesResultPart1, com.histmersion.util.jobs.JobRepo.StatesResultPart2)] =
    sql"SELECT status, count(*)::int FROM jobs GROUP BY status".query[(String, Int)].run().toList

  /** How many jobs are in each state — the whole table (one `GROUP BY` over the status index). */
  def stateCounts(using DbCon): List[(com.histmersion.util.jobs.JobRepo.StateCountsResultPart1, com.histmersion.util.jobs.JobRepo.StateCountsResultPart2)] = Keyset.countsOn(Sql.raw(" FROM jobs"), "status")

  private def conds(q: JobQuery): ListSql.Conds =
    val text = q.q.fold(Sql.empty) { t =>
      val p = Param.S(ListSql.contains(t))
      Sql.of(
        " AND (citekey ILIKE ? OR target_id ILIKE ? OR pipeline ILIKE ? OR detail::text ILIKE ? OR id LIKE ?)",
        p,
        p,
        p,
        p,
        Param.S(ListSql.contains(t).drop(1))
      )
    }
    ListSql.Conds(
      List(
        "status" -> ListSql.anyOf("status", q.status),
        "kind" -> ListSql.anyOf("kind", q.kinds),
        "time" -> ListSql.between("created_at", q.since, q.until),
        "q" -> text
      )
    )

  /** One page of the jobs that match `q`, newest first. Left: a cursor that names no job. */
  def page(
      q: JobQuery,
      limit: com.histmersion.util.jobs.JobRepo.PageLimit,
      before: Option[com.histmersion.util.jobs.JobRepo.PageBefore],
      upTo: Option[com.histmersion.util.jobs.JobRepo.PageUpTo]
  )(using DbCon): Either[com.histmersion.util.jobs.JobRepo.PageResultPart1, (Vector[JobInfo], com.histmersion.util.jobs.JobRepo.PageResultPart2Part2)] =
    (before.toList ++ upTo.toList).find(c => !Keyset.existsOn("jobs", Param.S(c))) match
      case Some(c) => Left(s"No job '$c' to page from")
      case None    =>
        val (rows, more) = Keyset.pageOn[JobRow](
          select,
          Sql.raw(" WHERE true") ++ conds(q).all,
          "",
          "jobs",
          math.max(1, limit),
          before.map(Param.S(_)),
          upTo.map(Param.S(_)),
          r => Param.S(r.id)
        )
        Right((rows.map(toInfo), more))

  /** The counts that go with a filtered page: per state, and the `kind` facet (at most `facetMax` kinds) — each under
    * every filter but its own.
    */
  def facets(q: JobQuery, facetMax: com.histmersion.util.jobs.JobRepo.FacetsFacetMax)(using DbCon): (List[(com.histmersion.util.jobs.JobRepo.FacetsResultPart1Part1, com.histmersion.util.jobs.JobRepo.FacetsResultPart1Part2)], List[(com.histmersion.util.jobs.JobRepo.FacetsResultPart2Part1, List[(com.histmersion.util.jobs.JobRepo.FacetsResultPart2Part2Part1, com.histmersion.util.jobs.JobRepo.FacetsResultPart2Part2Part2)])]) =
    val c = conds(q)
    def from(except: com.histmersion.util.jobs.JobRepo.FacetsFromExcept) = Sql.raw(" FROM jobs WHERE true") ++ c.except(except)
    (Keyset.countsOn(from("status"), "status"), List("kind" -> ListSql.facetOn(from("kind"), "kind", facetMax)))

  /** Free-text progress on a `running` job — what the CLI's progress line prints. */
  def progress(id: com.histmersion.util.jobs.JobRepo.ProgressId, text: com.histmersion.util.jobs.JobRepo.ProgressText)(using DbCon): Unit =
    val _ =
      sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND status = ${JobState.Running.wire}".update
        .run()

  /** Apply a [[JobState]] transition, guarded BY THE STORE as well as by the state machine: the `status = <from>`
    * predicate makes the update a compare-and-set, so two workers cannot both claim the same job. Returns the new
    * state, or `None` when the transition was refused.
    */
  def transition(
      id: com.histmersion.util.jobs.JobRepo.TransitionId,
      from: JobState,
      to: JobState,
      progress: Option[com.histmersion.util.jobs.JobRepo.TransitionProgress] = None,
      error: Option[com.histmersion.util.jobs.JobRepo.TransitionError] = None
  )(using DbCon): Option[JobState] =
    JobState.next(from, to).flatMap { target =>
      val updated =
        sql"""UPDATE jobs SET status = ${target.wire}, progress = $progress, error = $error, updated_at = now()
              WHERE id = $id AND status = ${from.wire}""".update.run()
      if updated > 0 then Some(target) else None
    }

  /** Startup reconciliation: nothing survives the process, so anything still `running` failed with it — and a
    * background job a person paused, whose way to go on was the process's. Returns how many rows were corrected. Agent
    * runs are NOT touched: their finished steps are checkpointed, so an interrupted run is paused and resumable
    * (`JobRuns.reconcile`). * `except`: the jobs this process is still running or holding queued — a config reload that
    * swapped back to a database (docs/research-plugins.md §3.6) must not fail its own work there.
    */
  def failInterrupted(except: List[com.histmersion.util.jobs.JobRepo.FailInterruptedExcept] = Nil)(using DbCon): com.histmersion.util.jobs.JobRepo.FailInterruptedResult =
    sql"""UPDATE jobs SET status = ${JobState.Failed.wire}, error = ${JobState.Interrupted},
                          progress = NULL, updated_at = now()
          WHERE status IN (${JobState.Running.wire}, ${JobState.Queued.wire}, ${JobState.Paused.wire})
            AND kind <> ${com.histmersion.api.automation.AgentJobKinds.Agent.kind}
            AND NOT (id = ANY($except::text[]))""".update.run()

  // ── runs: jobs that run as checkpointed steps (agent runs) ───────────────────────────────────

  /** A new run's row; its id. */
  def insertRun(r: RunRow)(using DbCon): com.histmersion.util.jobs.JobRepo.InsertRunResult =
    val id = java.util.UUID.randomUUID().toString
    sql"""INSERT INTO jobs (id, kind, citekey, status, pipeline, pipeline_version, target_kind, target_id,
                            model, budget_usd, budget_tokens, params, acquire_item, goal_slug, parent_job, origin,
                            dry_run, manager_id, manager_item)
          VALUES ($id, ${r.kind}, ${r.citekey}, ${r.status},
                  ${r.pipeline}, ${r.pipelineVersion}, ${r.targetKind}, ${r.target}, ${r.model}, ${r.budgetUsd},
                  ${r.budgetTokens}, ${r.params}::jsonb, ${r.acquireItem}, ${r.goal}, ${r.parent}, ${r.origin},
                  ${r.dryRun}, ${r.managerId}, ${r.managerItem})""".update
      .run()
    id

  /** The pipeline version a run was started with. */
  def runPipelineVersion(id: com.histmersion.util.jobs.JobRepo.RunPipelineVersionId)(using DbCon): Option[com.histmersion.util.jobs.JobRepo.RunPipelineVersionResult] =
    sql"SELECT pipeline_version FROM jobs WHERE id = $id".query[Option[Int]].run().headOption.flatten

  def runHeartbeat(kind: com.histmersion.util.jobs.JobRepo.RunHeartbeatKind, id: com.histmersion.util.jobs.JobRepo.RunHeartbeatId)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET heartbeat_at = now() WHERE id = $id AND kind = $kind".update.run()

  /** How many runs of `kind` are in one of `statuses`. */
  def runsIn(kind: com.histmersion.util.jobs.JobRepo.RunsInKind, statuses: (com.histmersion.util.jobs.JobRepo.RunsInStatusesPart1, com.histmersion.util.jobs.JobRepo.RunsInStatusesPart2))(using DbCon): com.histmersion.util.jobs.JobRepo.RunsInResult =
    sql"""SELECT count(*)::int FROM jobs WHERE kind = $kind
          AND status IN (${statuses._1}, ${statuses._2})""".query[Int].run().head

  /** Compare-and-set: `from` → `to`, setting the error, progress and resume time (None clears them). */
  def moveRun(
      kind: com.histmersion.util.jobs.JobRepo.MoveRunKind,
      id: com.histmersion.util.jobs.JobRepo.MoveRunId,
      from: com.histmersion.util.jobs.JobRepo.MoveRunFrom,
      to: com.histmersion.util.jobs.JobRepo.MoveRunTo,
      error: Option[com.histmersion.util.jobs.JobRepo.MoveRunError],
      progress: Option[com.histmersion.util.jobs.JobRepo.MoveRunProgress],
      resumeAt: Option[com.histmersion.util.jobs.JobRepo.MoveRunResumeAt]
  )(using DbCon): com.histmersion.util.jobs.JobRepo.MoveRunResult =
    sql"""UPDATE jobs SET status = $to, error = $error, progress = $progress,
                          resume_at = $resumeAt::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $from""".update.run() > 0

  /** The `paused` runs whose resume time has come, oldest resume first. */
  def runsDue(kind: com.histmersion.util.jobs.JobRepo.RunsDueKind, paused: com.histmersion.util.jobs.JobRepo.RunsDuePaused, at: com.histmersion.util.jobs.JobRepo.RunsDueAt)(using DbCon): Vector[com.histmersion.util.jobs.JobRepo.RunsDueResult] =
    sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $paused
                                AND resume_at IS NOT NULL AND resume_at <= $at::timestamptz
          ORDER BY resume_at, created_at""".query[String].run()

  /** The `paused` runs whose error starts like `like` and that do not resume by themselves before `at`: (id, pipeline,
    * model, error).
    */
  def runsPausedLike(
      kind: com.histmersion.util.jobs.JobRepo.RunsPausedLikeKind,
      paused: com.histmersion.util.jobs.JobRepo.RunsPausedLikePaused,
      like: com.histmersion.util.jobs.JobRepo.RunsPausedLikeLike,
      at: com.histmersion.util.jobs.JobRepo.RunsPausedLikeAt
  )(using DbCon): Vector[(com.histmersion.util.jobs.JobRepo.RunsPausedLikeResultPart1, com.histmersion.util.jobs.JobRepo.RunsPausedLikeResultPart2, Option[com.histmersion.util.jobs.JobRepo.RunsPausedLikeResultPart3], com.histmersion.util.jobs.JobRepo.RunsPausedLikeResultPart4)] =
    sql"""SELECT id, coalesce(pipeline, ''), model, coalesce(error, '') FROM jobs
          WHERE kind = $kind AND status = $paused AND error LIKE $like
            AND (resume_at IS NULL OR resume_at > $at::timestamptz)
          ORDER BY updated_at, created_at""".query[(String, String, Option[String], String)].run()

  def runProgress(kind: com.histmersion.util.jobs.JobRepo.RunProgressKind, id: com.histmersion.util.jobs.JobRepo.RunProgressId, text: com.histmersion.util.jobs.JobRepo.RunProgressText)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND kind = $kind".update.run()

  def runWarning(kind: com.histmersion.util.jobs.JobRepo.RunWarningKind, id: com.histmersion.util.jobs.JobRepo.RunWarningId, w: com.histmersion.util.jobs.JobRepo.RunWarningW)(using DbCon): Unit =
    val _ = sql"""UPDATE jobs SET warnings = array_append(warnings, $w), updated_at = now()
                  WHERE id = $id AND kind = $kind""".update.run()

  def runBudget(kind: com.histmersion.util.jobs.JobRepo.RunBudgetKind, id: com.histmersion.util.jobs.JobRepo.RunBudgetId, usd: Option[BigDecimal], tokens: Option[com.histmersion.util.jobs.JobRepo.RunBudgetTokens])(using DbCon): Unit =
    val _ =
      sql"""UPDATE jobs SET budget_usd = coalesce($usd, budget_usd), budget_tokens = coalesce($tokens, budget_tokens),
                          updated_at = now() WHERE id = $id AND kind = $kind""".update.run()

  /** The `running` runs (but `except`) moved to `paused` with `error`, returned with their origin; then the `queued`
    * ones (but `except`), oldest first.
    */
  def runsReconciled(
      kind: com.histmersion.util.jobs.JobRepo.RunsReconciledKind,
      running: com.histmersion.util.jobs.JobRepo.RunsReconciledRunning,
      paused: com.histmersion.util.jobs.JobRepo.RunsReconciledPaused,
      error: com.histmersion.util.jobs.JobRepo.RunsReconciledError,
      queued: com.histmersion.util.jobs.JobRepo.RunsReconciledQueued,
      except: List[com.histmersion.util.jobs.JobRepo.RunsReconciledExcept]
  )(using DbCon): (Vector[(com.histmersion.util.jobs.JobRepo.RunsReconciledResultPart1Part1, Option[com.histmersion.util.jobs.JobRepo.RunsReconciledResultPart1Part2])], Vector[com.histmersion.util.jobs.JobRepo.RunsReconciledResultPart2]) =
    val moved = sql"""UPDATE jobs SET status = $paused, error = $error, updated_at = now()
                      WHERE kind = $kind AND status = $running AND NOT (id = ANY($except::text[]))
                      RETURNING id, origin""".query[(String, Option[String])].run()
    val waiting = sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $queued
                          AND NOT (id = ANY($except::text[]))
                        ORDER BY created_at""".query[String].run()
    (moved, waiting)

  /** A `paused` run's resume time (unless it moved on meanwhile). */
  def runResumesAt(kind: com.histmersion.util.jobs.JobRepo.RunResumesAtKind, id: com.histmersion.util.jobs.JobRepo.RunResumesAtId, paused: com.histmersion.util.jobs.JobRepo.RunResumesAtPaused, at: com.histmersion.util.jobs.JobRepo.RunResumesAtAt)(using DbCon): com.histmersion.util.jobs.JobRepo.RunResumesAtResult =
    sql"""UPDATE jobs SET resume_at = $at::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $paused""".update.run() > 0

  // ── merge apply (#888) ─────────────────────────────────────────────────────────────────────────

  /** Start a merge apply: a `running` row at once (it runs on its own thread, never queued), its bundle in `sha256` and
    * its plan in `detail`. A restart fails it as `interrupted` like every other unfinished job ([[failInterrupted]]),
    * so a poll never sees it running forever.
    */
  def startMergeApply(bundle: com.histmersion.util.jobs.JobRepo.StartMergeApplyBundle, planHash: com.histmersion.util.jobs.JobRepo.StartMergeApplyPlanHash)(using DbCon): com.histmersion.util.jobs.JobRepo.StartMergeApplyResult =
    val id = java.util.UUID.randomUUID().toString
    val js = json(com.histmersion.api.exchange.ExchangeJobKinds.PlanHash(Some(planHash)))
    sql"""INSERT INTO jobs (id, kind, status, sha256, detail)
          VALUES ($id, ${com.histmersion.api.exchange.ExchangeJobKinds.MergeApply.kind}, ${JobState.Running.wire}, $bundle, $js::jsonb)""".update.run()
    id

  /** Record how a merge apply ended: `done` with its report (applied or refused), or `failed` with why. A row a restart
    * already failed is left as it is.
    */
  def finishMergeApply(id: com.histmersion.util.jobs.JobRepo.FinishMergeApplyId, outcome: Either[com.histmersion.util.jobs.JobRepo.FinishMergeApplyOutcomePart1, MergeApplyResult])(using DbCon): Unit =
    val (status, error, result) = outcome match
      case Right(r) => (JobState.Done, None, Some(Jsonb.write(r)))
      case Left(e)  => (JobState.Failed, Some(e), None)
    val _ =
      sql"""UPDATE jobs SET status = ${status.wire}, error = $error, result = $result::jsonb, updated_at = now()
            WHERE id = $id AND kind = ${com.histmersion.api.exchange.ExchangeJobKinds.MergeApply.kind} AND status = ${JobState.Running.wire}""".update.run()

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). The time so far of a
    * running one is measured against the database's clock.
    */
  def mergeApply(id: com.histmersion.util.jobs.JobRepo.MergeApplyId)(using DbCon): Option[MergeApplyJob] =
    sql"""SELECT id, status, coalesce(sha256, ''), coalesce(detail->>'planHash', ''),
                 (extract(epoch FROM (CASE WHEN status = ${JobState.Running.wire} THEN clock_timestamp()
                                          ELSE updated_at END) - created_at) * 1000)::bigint,
                 error, result::text
          FROM jobs WHERE id = $id AND kind = ${com.histmersion.api.exchange.ExchangeJobKinds.MergeApply.kind}"""
      .query[(String, String, String, String, Long, Option[String], Option[String])]
      .run()
      .headOption
      .map { (id, status, bundle, plan, ms, error, result) =>
        MergeApplyJob.of(
          id,
          JobState.parse(status).getOrElse(JobState.Failed),
          bundle,
          plan,
          math.max(0L, ms),
          error,
          result.flatMap(Jsonb.read[MergeApplyResult])
        )
      }

  /** Is a merge apply running? One at a time. */
  def mergeApplyRunning(using DbCon): com.histmersion.util.jobs.JobRepo.MergeApplyRunningResult =
    sql"SELECT 1 FROM jobs WHERE kind = ${com.histmersion.api.exchange.ExchangeJobKinds.MergeApply.kind} AND status = ${JobState.Running.wire} LIMIT 1"
      .query[Int]
      .run()
      .nonEmpty
