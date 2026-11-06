package com.histmersion.domain.automation.agents

import com.histmersion.api.Paging
import com.histmersion.api.automation.{ApprovalDigest, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState}
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.JobRuns
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference



/** The approvals, as the engine's features reach them: what a run proposed for a person to decide — open until decided
  * (once), or recorded approved when it was applied at once — and the reads the lists, the digest, the autopilot and
  * the scheduler make of them.
  */
trait ApprovalService:

  def approvalIn(id: com.histmersion.domain.automation.agents.ApprovalService.ApprovalInId)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo]

  def payload(id: com.histmersion.domain.automation.agents.ApprovalService.PayloadId): Option[com.histmersion.domain.automation.agents.ApprovalService.PayloadResult]



  def approval(id: com.histmersion.domain.automation.agents.ApprovalService.ApprovalId): Option[ApprovalInfo]

  /** Newest first, of one state or every one. */
  def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.agents.ApprovalService.ApprovalsLimit): List[ApprovalInfo]

  /** One newest-first page of one state or every one; `before`/`upTo` are approval ids (Left: one that is none). */
  def approvalPage(
      state: Option[ApprovalState],
      limit: com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageLimit,
      before: Option[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageBefore] = None,
      upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageUpTo] = None
  ): Either[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageResultPart2Part2)]

  /** One newest-first page of the approvals `q` matches. */
  def approvalPage(
      q: ApprovalQuery,
      limit: com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageLimit,
      before: Option[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageBefore],
      upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageUpTo]
  ): Either[com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.ApprovalPageResultPart2Part2)]

  /** Per state (every filter but the state's), and the kind and decided-by facets (each without its own filter). */
  def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart1Part2)], List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart2Part2Part2)])])

  /** Every approval counted per state. */
  def approvalCounts(): List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalCountsResultPart1, com.histmersion.domain.automation.agents.ApprovalService.ApprovalCountsResultPart2)]

  /** The approval step `seq` of run `jobId` waits on, opened unless it is there already. */
  def openForStep(
      jobId: com.histmersion.util.jobs.Job.Id,
      seq: com.histmersion.domain.automation.agents.ApprovalService.OpenForStepSeq,
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.agents.ApprovalService.OpenForStepSubject,
      payload: com.histmersion.domain.automation.agents.ApprovalService.OpenForStepPayload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[com.histmersion.domain.automation.agents.ApprovalService.OpenForStepResult] = None
  ): ApprovalInfo

  /** A proposal about a goal or an article that no run waits for. */
  def openSubject(
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.agents.ApprovalService.OpenSubjectSubject,
      payload: com.histmersion.domain.automation.agents.ApprovalService.OpenSubjectPayload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[com.histmersion.domain.automation.agents.ApprovalService.OpenSubjectResult] = None
  ): ApprovalInfo

  /** What run `jobId` opened (of `kind`), oldest first. */
  def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind] = None): List[ApprovalInfo]

  /** Of `kind`, approved by the autopilot since `since`. */
  def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo]

  /** The open ones of these kinds, oldest first. */
  def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.agents.ApprovalService.OpenOfKindsLimit): List[ApprovalInfo]

  /** Every open one, light, oldest first. */
  def openLight(): List[ApprovalDigest.Open]
  def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.OpenCountResult

  /** Open ones per subject. */
  def openBySubject(): Map[com.histmersion.domain.automation.agents.ApprovalService.OpenBySubjectResultPart1, com.histmersion.domain.automation.agents.ApprovalService.OpenBySubjectResultPart2]

  /** The approval step `seq` of run `jobId` opened. */
  def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.ForStepSeq): Option[ApprovalInfo]

  /** A cancelled run's open step approvals rejected with `why`; how many. */
  def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.ApprovalService.CloseForRunWhy): com.histmersion.domain.automation.agents.ApprovalService.CloseForRunResult

  /** Compare-and-set an open one to its decision; false when it was decided already. */
  def decide(id: com.histmersion.domain.automation.agents.ApprovalService.DecideId, to: ApprovalState, note: Option[com.histmersion.domain.automation.agents.ApprovalService.DecideNote], result: Option[String]): com.histmersion.domain.automation.agents.ApprovalService.DecideResult

  /** After [[decide]]: the final state and what applying it did. */
  def settle(id: com.histmersion.domain.automation.agents.ApprovalService.SettleId, to: ApprovalState, result: com.histmersion.domain.automation.agents.ApprovalService.SettleResult): Unit

object ApprovalService:
  type ApprovalInId = ApprovalInId.T
  object ApprovalInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PayloadResult = PayloadResult.T
  object PayloadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PayloadId = PayloadId.T
  object PayloadId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalId = ApprovalId.T
  object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsLimit = ApprovalsLimit.T
  object ApprovalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalPageResultPart1 = ApprovalPageResultPart1.T
  object ApprovalPageResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalPageResultPart2Part2 = ApprovalPageResultPart2Part2.T
  object ApprovalPageResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalPageLimit = ApprovalPageLimit.T
  object ApprovalPageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalPageBefore = ApprovalPageBefore.T
  object ApprovalPageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalPageUpTo = ApprovalPageUpTo.T
  object ApprovalPageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
  object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart1Part2 = ApprovalFacetsResultPart1Part2.T
  object ApprovalFacetsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
  object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
  object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart2Part2Part2 = ApprovalFacetsResultPart2Part2Part2.T
  object ApprovalFacetsResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalCountsResultPart1 = ApprovalCountsResultPart1.T
  object ApprovalCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalCountsResultPart2 = ApprovalCountsResultPart2.T
  object ApprovalCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForStepSeq = OpenForStepSeq.T
  object OpenForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForStepSubject = OpenForStepSubject.T
  object OpenForStepSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForStepPayload = OpenForStepPayload.T
  object OpenForStepPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForStepResult = OpenForStepResult.T
  object OpenForStepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSubjectSubject = OpenSubjectSubject.T
  object OpenSubjectSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSubjectPayload = OpenSubjectPayload.T
  object OpenSubjectPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSubjectResult = OpenSubjectResult.T
  object OpenSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfKindsLimit = OpenOfKindsLimit.T
  object OpenOfKindsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenCountResult = OpenCountResult.T
  object OpenCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenBySubjectResultPart1 = OpenBySubjectResultPart1.T
  object OpenBySubjectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenBySubjectResultPart2 = OpenBySubjectResultPart2.T
  object OpenBySubjectResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForStepSeq = ForStepSeq.T
  object ForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CloseForRunResult = CloseForRunResult.T
  object CloseForRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CloseForRunWhy = CloseForRunWhy.T
  object CloseForRunWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideResult = DecideResult.T
  object DecideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideId = DecideId.T
  object DecideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideNote = DecideNote.T
  object DecideNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleId = SettleId.T
  object SettleId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleResult = SettleResult.T
  object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, clock: Clock): ApprovalService = Implementation(db, clock)

  final private class Implementation(db: Transactor, clock: Clock) extends ApprovalService:
    def approvalIn(id: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalInId)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] = AgentRepo.approval(id)
    private def now: Instant = Instant.now(clock)

    def payload(id: com.histmersion.domain.automation.agents.ApprovalService.Implementation.PayloadId): Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.PayloadResult] = db.read(AgentRepo.payload(id))

    def approval(id: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalId): Option[ApprovalInfo] = db.read(AgentRepo.approval(id))
    def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalsLimit): List[ApprovalInfo] =
      db.read(AgentRepo.approvals(state, limit))
    def approvalPage(
        state: Option[ApprovalState],
        limit: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageLimit,
        before: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageBefore],
        upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageUpTo]
    ): Either[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageResultPart2Part2)] = db.read(AgentRepo.approvalPage(state, limit, before, upTo))
    def approvalPage(
        q: ApprovalQuery,
        limit: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageLimit,
        before: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageBefore],
        upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageUpTo]
    ): Either[com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalPageResultPart2Part2)] = db.read(AgentRepo.approvalPage(q, limit, before, upTo))
    def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart1Part2)], List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart2Part2Part2)])]) =
      db.read(AgentRepo.approvalFacets(q))
    def approvalCounts(): List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalCountsResultPart1, com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalCountsResultPart2)] = db.read(AgentRepo.approvalCounts())
    def openForStep(
        jobId: com.histmersion.util.jobs.Job.Id,
        seq: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenForStepSeq,
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenForStepSubject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenForStepPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenForStepResult]
    ): ApprovalInfo = db.transact(AgentRepo.openForStep(jobId, seq, kind, subject, payload, state, result, now))
    def openSubject(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenSubjectSubject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenSubjectPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenSubjectResult]
    ): ApprovalInfo = db.transact(AgentRepo.openSubject(jobId, kind, subject, payload, state, result, now))
    def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      db.read(AgentRepo.approvalsOf(jobId, kind))
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      db.read(AgentRepo.autoApprovedSince(kind, since))
    def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenOfKindsLimit): List[ApprovalInfo] =
      db.read(AgentRepo.openOfKinds(kinds, limit))
    def openLight(): List[ApprovalDigest.Open] = db.read(AgentRepo.openLight())
    def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenCountResult = db.read(AgentRepo.openCount(kind))
    def openBySubject(): Map[com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenBySubjectResultPart1, com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenBySubjectResultPart2] = db.read(AgentRepo.openBySubject())
    def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.Implementation.ForStepSeq): Option[ApprovalInfo] = db.read(AgentRepo.forStep(jobId, seq))
    def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.ApprovalService.Implementation.CloseForRunWhy): com.histmersion.domain.automation.agents.ApprovalService.Implementation.CloseForRunResult = db.transact(AgentRepo.closeForRun(jobId, why))
    def decide(id: com.histmersion.domain.automation.agents.ApprovalService.Implementation.DecideId, to: ApprovalState, note: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.DecideNote], result: Option[String]): com.histmersion.domain.automation.agents.ApprovalService.Implementation.DecideResult =
      db.transact(AgentRepo.decide(id, to, note, result))
    def settle(id: com.histmersion.domain.automation.agents.ApprovalService.Implementation.SettleId, to: ApprovalState, result: com.histmersion.domain.automation.agents.ApprovalService.Implementation.SettleResult): Unit = db.transact(AgentRepo.settle(id, to, result))

  object Implementation:
    type ApprovalInId = ApprovalInId.T
    object ApprovalInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PayloadResult = PayloadResult.T
    object PayloadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PayloadId = PayloadId.T
    object PayloadId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalId = ApprovalId.T
    object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalsLimit = ApprovalsLimit.T
    object ApprovalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageResultPart1 = ApprovalPageResultPart1.T
    object ApprovalPageResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageResultPart2Part2 = ApprovalPageResultPart2Part2.T
    object ApprovalPageResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageLimit = ApprovalPageLimit.T
    object ApprovalPageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageBefore = ApprovalPageBefore.T
    object ApprovalPageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageUpTo = ApprovalPageUpTo.T
    object ApprovalPageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
    object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart1Part2 = ApprovalFacetsResultPart1Part2.T
    object ApprovalFacetsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
    object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
    object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part2 = ApprovalFacetsResultPart2Part2Part2.T
    object ApprovalFacetsResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalCountsResultPart1 = ApprovalCountsResultPart1.T
    object ApprovalCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalCountsResultPart2 = ApprovalCountsResultPart2.T
    object ApprovalCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepSeq = OpenForStepSeq.T
    object OpenForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepSubject = OpenForStepSubject.T
    object OpenForStepSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepPayload = OpenForStepPayload.T
    object OpenForStepPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepResult = OpenForStepResult.T
    object OpenForStepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectSubject = OpenSubjectSubject.T
    object OpenSubjectSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectPayload = OpenSubjectPayload.T
    object OpenSubjectPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectResult = OpenSubjectResult.T
    object OpenSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfKindsLimit = OpenOfKindsLimit.T
    object OpenOfKindsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenCountResult = OpenCountResult.T
    object OpenCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenBySubjectResultPart1 = OpenBySubjectResultPart1.T
    object OpenBySubjectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenBySubjectResultPart2 = OpenBySubjectResultPart2.T
    object OpenBySubjectResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ForStepSeq = ForStepSeq.T
    object ForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseForRunResult = CloseForRunResult.T
    object CloseForRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseForRunWhy = CloseForRunWhy.T
    object CloseForRunWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideResult = DecideResult.T
    object DecideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideId = DecideId.T
    object DecideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideNote = DecideNote.T
    object DecideNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleId = SettleId.T
    object SettleId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleResult = SettleResult.T
    object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The approvals in memory, with the runs they name (their goal and manager match a list's filters); `clock` stands
    * in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], runs: JobRuns.InMemory, clock: Clock)
      extends ApprovalService:
    def approvalIn(id: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalInId)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] = approval(id)
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalState.Open.wire
    private def info(r: ApprovalRecord): ApprovalInfo =
      AgentRepo.info(
        AgentRepo.ApprovalRow(
          r.id,
          r.jobId,
          r.kind,
          r.citekey,
          r.status,
          r.current,
          r.edgeKind,
          r.target,
          r.proposal,
          r.state,
          r.note,
          r.result,
          AutomationState.text(r.createdAt),
          r.decidedAt.map(AutomationState.text),
          r.subject,
          r.seq
        )
      )
    private def newest(rs: Vector[ApprovalRecord]): Vector[ApprovalRecord] =
      rs.sortBy(r => (r.createdAt, r.id)).reverse

    /** Insert `make(id)` unless `conflicts` holds of a row there; the new row's id. */

    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)


    def payload(id: com.histmersion.domain.automation.agents.ApprovalService.InMemory.PayloadId): Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.PayloadResult] = rows.find(_.id == id).map(_.proposal)

    def approval(id: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalId): Option[ApprovalInfo] = rows.find(_.id == id).map(info)
    def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalsLimit): List[ApprovalInfo] =
      newest(rows.filter(r => state.forall(_.wire == r.state))).take(math.max(1, limit)).toList.map(info)
    def approvalPage(
        state: Option[ApprovalState],
        limit: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageLimit,
        before: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageBefore],
        upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageUpTo]
    ): Either[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageResultPart2Part2)] = approvalPage(ApprovalQuery(state), limit, before, upTo)

    private def decidedBy(r: ApprovalRecord): Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.DecidedByResult] =
      if r.state == Open then None
      else if r.note.exists(_.startsWith(ListFilters.DecidedBy.AutopilotNote)) then
        Some(ListFilters.DecidedBy.Autopilot.wire)
      else if r.note.isEmpty && r.result.exists(_.startsWith(ListFilters.DecidedBy.AutoResult)) then
        Some(ListFilters.DecidedBy.Auto.wire)
      else Some(ListFilters.DecidedBy.Person.wire)
    private def contains(v: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ContainsV], t: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ContainsT): com.histmersion.domain.automation.agents.ApprovalService.InMemory.ContainsResult = v.exists(_.toLowerCase.contains(t.toLowerCase))

    /** Whether `r` passes every filter of `q` but `except`'s. */
    private def matches(q: ApprovalQuery, except: com.histmersion.domain.automation.agents.ApprovalService.InMemory.MatchesExcept)(r: ApprovalRecord): com.histmersion.domain.automation.agents.ApprovalService.InMemory.MatchesResult =
      lazy val run = r.jobId.flatMap(runs.get)
      (except == "state" || q.state.forall(_.wire == r.state)) &&
      (except == "kind" || q.kinds.isEmpty || q.kinds.exists(_.wire == r.kind)) &&
      q.subject.forall(t =>
        contains(r.subject, t) || contains(r.citekey, t) || contains(r.target, t) ||
          run.exists(j => contains(j.row.goal, t) || contains(j.row.managerId, t))
      ) &&
      q.goal.forall(g => r.subject.contains(g) || run.exists(_.row.goal.contains(g))) &&
      (except == "decidedBy" || q.decidedBy.isEmpty || decidedBy(r).exists(d => q.decidedBy.exists(_.wire == d)))

    def approvalPage(
        q: ApprovalQuery,
        limit: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageLimit,
        before: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageBefore],
        upTo: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageUpTo]
    ): Either[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageResultPart1, (List[ApprovalInfo], com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageResultPart2Part2)] =
      def cursor(c: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageCursorC): Either[com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalPageCursorResultPart1, ApprovalRecord] =
        c.trim.toLongOption.flatMap(id => rows.find(_.id == id)).toRight(s"No approval '$c' to page from")
      for
        b <- before.fold(Right(None))(cursor(_).map(Some(_)))
        u <- upTo.fold(Right(None))(cursor(_).map(Some(_)))
      yield
        def key(r: ApprovalRecord) = (r.createdAt, r.id)
        val ordering = summon[Ordering[(Instant, Long)]]
        val all = newest(rows.filter(matches(q, "")))
        val bounded =
          all.filter(r => b.forall(c => ordering.lt(key(r), key(c))) && u.forall(c => ordering.gteq(key(r), key(c))))
        val (shown, more) = Paging.cut(bounded.take(math.max(1, limit) + 1), math.max(1, limit))
        val older =
          if more || u.isEmpty then more
          else shown.lastOption.exists(last => all.exists(r => ordering.lt(key(r), key(last))))
        (shown.toList.map(info), older)

    private def facet(values: Vector[com.histmersion.domain.automation.agents.ApprovalService.InMemory.FacetValues]): List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.FacetResultPart1, com.histmersion.domain.automation.agents.ApprovalService.InMemory.FacetResultPart2)] =
      values.groupBy(identity).toList.map((v, n) => (v, n.size)).sortBy((v, n) => (-n, v)).take(ListFilters.FacetMax)
    def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart1Part2)], List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart2Part2Part2)])]) =
      (
        facet(rows.filter(matches(q, "state")).map(_.state)),
        List(
          "kind" -> facet(rows.filter(matches(q, "kind")).map(_.kind)),
          "decidedBy" -> facet(rows.filter(matches(q, "decidedBy")).flatMap(decidedBy))
        )
      )
    def approvalCounts(): List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalCountsResultPart1, com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalCountsResultPart2)] = facet(rows.map(_.state))
    def openForStep(
        jobId: com.histmersion.util.jobs.Job.Id,
        seq: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenForStepSeq,
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenForStepSubject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenForStepPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenForStepResult]
    ): ApprovalInfo =
      val at = now
      val _ = AutomationState.insertApproval(this.state)(r => r.jobId.contains(jobId) && r.seq.contains(seq))(id =>
        ApprovalRecord(
          id,
          Some(jobId),
          Some(seq),
          kind.wire,
          None,
          None,
          None,
          None,
          None,
          Some(subject),
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      )
      forStep(jobId, seq).get
    def openSubject(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenSubjectSubject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenSubjectPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenSubjectResult]
    ): ApprovalInfo =
      val at = now
      AutomationState.insertApproval(this.state)(_ => false)(id =>
        ApprovalRecord(
          id,
          jobId,
          None,
          kind.wire,
          None,
          None,
          None,
          None,
          None,
          Some(subject),
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      ).flatMap(approval).get
    def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      rows.filter(r => r.jobId.contains(jobId) && kind.forall(_.wire == r.kind)).sortBy(_.id).toList.map(info)
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      rows
        .filter(r =>
          r.kind == kind.wire && r.note.exists(_.startsWith("autopilot: approved")) && r.decidedAt.exists(
            !_.isBefore(since)
          )
        )
        .sortBy(_.id)
        .toList
        .map(info)
    def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenOfKindsLimit): List[ApprovalInfo] =
      rows
        .filter(r => r.state == Open && kinds.exists(_.wire == r.kind))
        .sortBy(_.id)
        .take(math.max(1, limit))
        .toList
        .map(info)
    def openLight(): List[ApprovalDigest.Open] =
      rows
        .filter(_.state == Open)
        .sortBy(_.id)
        .toList
        .map(r => ApprovalDigest.Open(r.id, r.kind, r.subject, AutomationState.text(r.createdAt)))
    def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenCountResult = rows.count(r => r.state == Open && r.kind == kind.wire)
    def openBySubject(): Map[com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenBySubjectResultPart1, com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenBySubjectResultPart2] =
      rows.filter(_.state == Open).flatMap(_.subject).groupBy(identity).view.mapValues(_.size).toMap
    def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.InMemory.ForStepSeq): Option[ApprovalInfo] =
      rows.find(r => r.jobId.contains(jobId) && r.seq.contains(seq)).map(info)

    /** Change the rows `which` selects by `f`; how many. */
    private def change(which: ApprovalRecord => com.histmersion.domain.automation.agents.ApprovalService.InMemory.ChangeWhichResult)(f: ApprovalRecord => ApprovalRecord): com.histmersion.domain.automation.agents.ApprovalService.InMemory.ChangeResult =
      state.getAndUpdate(_.modify(_.approvals).using(_.map(r => if which(r) then f(r) else r))).approvals.count(which)
    def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.ApprovalService.InMemory.CloseForRunWhy): com.histmersion.domain.automation.agents.ApprovalService.InMemory.CloseForRunResult =
      val at = now
      change(r => r.jobId.contains(jobId) && r.seq.isDefined && r.state == Open)(
        _.modify(_.state)
          .setTo(ApprovalState.Rejected.wire)
          .modify(_.result)
          .setTo(Some(why))
          .modify(_.decidedAt)
          .setTo(Some(at))
      )
    def decide(id: com.histmersion.domain.automation.agents.ApprovalService.InMemory.DecideId, to: ApprovalState, note: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.DecideNote], result: Option[String]): com.histmersion.domain.automation.agents.ApprovalService.InMemory.DecideResult =
      val at = now
      change(r => r.id == id && r.state == Open)(
        _.modify(_.state)
          .setTo(to.wire)
          .modify(_.note)
          .setTo(note)
          .modify(_.result)
          .setTo(result)
          .modify(_.decidedAt)
          .setTo(Some(at))
      ) > 0
    def settle(id: com.histmersion.domain.automation.agents.ApprovalService.InMemory.SettleId, to: ApprovalState, result: com.histmersion.domain.automation.agents.ApprovalService.InMemory.SettleResult): Unit =
      val _ = change(_.id == id)(_.modify(_.state).setTo(to.wire).modify(_.result).setTo(Some(result)))

  object InMemory:
    type ApprovalPageCursorResultPart1 = ApprovalPageCursorResultPart1.T
    object ApprovalPageCursorResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageCursorC = ApprovalPageCursorC.T
    object ApprovalPageCursorC extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ApprovalInId = ApprovalInId.T
    object ApprovalInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PayloadResult = PayloadResult.T
    object PayloadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PayloadId = PayloadId.T
    object PayloadId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalId = ApprovalId.T
    object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalsLimit = ApprovalsLimit.T
    object ApprovalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageResultPart1 = ApprovalPageResultPart1.T
    object ApprovalPageResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageResultPart2Part2 = ApprovalPageResultPart2Part2.T
    object ApprovalPageResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageLimit = ApprovalPageLimit.T
    object ApprovalPageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageBefore = ApprovalPageBefore.T
    object ApprovalPageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalPageUpTo = ApprovalPageUpTo.T
    object ApprovalPageUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedByResult = DecidedByResult.T
    object DecidedByResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsResult = ContainsResult.T
    object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsV = ContainsV.T
    object ContainsV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsT = ContainsT.T
    object ContainsT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MatchesResult = MatchesResult.T
    object MatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MatchesExcept = MatchesExcept.T
    object MatchesExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetResultPart1 = FacetResultPart1.T
    object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetResultPart2 = FacetResultPart2.T
    object FacetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetValues = FacetValues.T
    object FacetValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
    object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart1Part2 = ApprovalFacetsResultPart1Part2.T
    object ApprovalFacetsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
    object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
    object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part2 = ApprovalFacetsResultPart2Part2Part2.T
    object ApprovalFacetsResultPart2Part2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalCountsResultPart1 = ApprovalCountsResultPart1.T
    object ApprovalCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalCountsResultPart2 = ApprovalCountsResultPart2.T
    object ApprovalCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepSeq = OpenForStepSeq.T
    object OpenForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepSubject = OpenForStepSubject.T
    object OpenForStepSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepPayload = OpenForStepPayload.T
    object OpenForStepPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepResult = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    val OpenForStepResult: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    type OpenSubjectSubject = OpenSubjectSubject.T
    object OpenSubjectSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectPayload = OpenSubjectPayload.T
    object OpenSubjectPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectResult = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    val OpenSubjectResult: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    type OpenOfKindsLimit = OpenOfKindsLimit.T
    object OpenOfKindsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenCountResult = OpenCountResult.T
    object OpenCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenBySubjectResultPart1 = OpenBySubjectResultPart1.T
    object OpenBySubjectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenBySubjectResultPart2 = OpenBySubjectResultPart2.T
    object OpenBySubjectResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ForStepSeq = ForStepSeq.T
    object ForStepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeResult = ChangeResult.T
    object ChangeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeWhichResult = ChangeWhichResult.T
    object ChangeWhichResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseForRunResult = CloseForRunResult.T
    object CloseForRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CloseForRunWhy = CloseForRunWhy.T
    object CloseForRunWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideResult = DecideResult.T
    object DecideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideId = DecideId.T
    object DecideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecideNote = DecideNote.T
    object DecideNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleId = SettleId.T
    object SettleId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleResult = SettleResult.T
    object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
