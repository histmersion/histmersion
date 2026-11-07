package com.histmersion.domain.library.workflows

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, ApprovalInfo}
import com.histmersion.domain.library.{Source, Edge}
import com.histmersion.domain.automation.AutomationState
import com.histmersion.domain.automation.agents.{ApprovalService, ApprovalRecord}
import com.histmersion.util.database.Transactor
import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference

trait SourceApprovalService:
  def record(
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalKind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalTarget],
      payload: com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalPayload,
      state: ApprovalState,
      result: Option[com.histmersion.domain.library.workflows.SourceApprovalService.RecordResult]
  ): Option[ApprovalInfo]

  def proposedBefore(
      kind: ApprovalKind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalTarget]
  ): Boolean

object SourceApprovalService:
  type ApprovalPayload = ApprovalPayload.T
  object ApprovalPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalTarget = ApprovalTarget.T
  object ApprovalTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResult = RecordResult.T
  object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, approvals: ApprovalService, clock: Clock): SourceApprovalService = Implementation(db, approvals, clock)

  private final class Implementation(db: Transactor, approvals: ApprovalService, clock: Clock) extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    def record(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalTarget],
        payload: com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.library.workflows.SourceApprovalService.Implementation.RecordResult]
    ): Option[ApprovalInfo] =
      db.transact(
        SourceApprovalRepo.record(jobId, kind, citekey, status, current, edgeKind, target, payload, state, result, now).flatMap(approvals.approvalIn(_))
      )

    def proposedBefore(
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalTarget]
    ): Boolean = db.read(SourceApprovalRepo.proposedBefore(kind, citekey, status, edgeKind, target))

  object Implementation:
    type RecordResult = RecordResult.T
    object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class InMemory(state: AtomicReference[AutomationState], approvals: ApprovalService, clock: Clock) extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalState.Open.wire
    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)
    def record(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.InMemory.RecordTarget],
        payload: com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalPayload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.library.workflows.SourceApprovalService.InMemory.RecordResult]
    ): Option[ApprovalInfo] =
      val (st, ek) = (status.map(_.wire), edgeKind.map(Edge.Kind.wireOf))
      def same(r: ApprovalRecord) =
        state == ApprovalState.Open && r.state == Open && r.kind == kind.wire && r.citekey.contains(citekey) &&
          r.status.getOrElse("") == st.getOrElse("") && r.edgeKind.getOrElse("") == ek.getOrElse("") &&
          r.target.getOrElse("") == target.getOrElse("")
      val at = now
      AutomationState.insertApproval(this.state)(same)(id =>
        ApprovalRecord(
          id,
          jobId,
          None,
          kind.wire,
          Some(citekey),
          st,
          current.map(_.wire),
          ek,
          target,
          None,
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      ).flatMap(approvals.approval)

    def proposedBefore(
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.workflows.SourceApprovalService.ApprovalTarget]
    ): Boolean =
      rows.exists(r =>
        r.kind == kind.wire && r.citekey.contains(citekey) && r.status.getOrElse("") == status.fold("")(_.wire) &&
          r.edgeKind.getOrElse("") == edgeKind.fold("")(Edge.Kind.wireOf) && r.target.getOrElse("") == target
            .getOrElse("")
      )

  object InMemory:
    type RecordTarget = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Target
    val RecordTarget: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Target.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Target
    type RecordResult = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    val RecordResult: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
