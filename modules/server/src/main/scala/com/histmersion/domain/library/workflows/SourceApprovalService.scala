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
      target: Option[com.histmersion.domain.library.Citekey],
      payload: com.histmersion.util.database.Jsonb.JsonText,
      state: ApprovalState,
      result: Option[ApprovalRecord.Result]
  ): Option[ApprovalInfo]

  def proposedBefore(
      kind: ApprovalKind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[com.histmersion.domain.library.Citekey]
  ): Boolean

object SourceApprovalService:
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
        target: Option[com.histmersion.domain.library.Citekey],
        payload: com.histmersion.util.database.Jsonb.JsonText,
        state: ApprovalState,
        result: Option[ApprovalRecord.Result]
    ): Option[ApprovalInfo] =
      db.transact(
        SourceApprovalRepo.record(jobId, kind, citekey, status, current, edgeKind, target, payload.value, state, result.map(_.value), now).flatMap(id => approvals.approvalIn(com.histmersion.domain.automation.Approval.Id(id.value)))
      )

    def proposedBefore(
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.Citekey]
    ): Boolean = db.read(SourceApprovalRepo.proposedBefore(kind, citekey, status, edgeKind, target))

  final class InMemory(state: AtomicReference[AutomationState], approvals: ApprovalService, clock: Clock) extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalRecord.State(ApprovalState.Open.wire.value)
    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)
    def record(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        citekey: com.histmersion.domain.library.Citekey,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[com.histmersion.domain.library.Citekey],
        payload: com.histmersion.util.database.Jsonb.JsonText,
        state: ApprovalState,
        result: Option[ApprovalRecord.Result]
    ): Option[ApprovalInfo] =
      val (st, ek) = (
        status.map(s => ApprovalRecord.Status(s.wire.value)),
        edgeKind.map(k => ApprovalRecord.EdgeKind(Edge.Kind.wireOf(k).value))
      )
      def same(r: ApprovalRecord) =
        state == ApprovalState.Open && r.state == Open && r.kind.value == kind.wire.value && r.citekey.contains(citekey) &&
          r.status.fold("")(_.value) == st.fold("")(_.value) && r.edgeKind.fold("")(_.value) == ek.fold("")(_.value) &&
          r.target.fold("")(_.value) == target.fold("")(_.value)
      val at = now
      AutomationState.insertApproval(this.state)(same)(id =>
        ApprovalRecord(
          ApprovalRecord.Id(id.value),
          jobId,
          None,
          ApprovalRecord.Kind(kind.wire.value),
          Some(citekey),
          st,
          current.map(c => ApprovalRecord.Current(c.wire.value)),
          ek,
          target.map(t => ApprovalRecord.Target(t.value)),
          None,
          ApprovalRecord.Proposal(payload.value),
          ApprovalRecord.State(state.wire.value),
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
        target: Option[com.histmersion.domain.library.Citekey]
    ): Boolean =
      rows.exists(r =>
        r.kind.value == kind.wire.value && r.citekey.contains(citekey) &&
          r.status.fold("")(_.value) == status.fold("")(_.wire.value) &&
          r.edgeKind.fold("")(_.value) == edgeKind.fold("")(k => Edge.Kind.wireOf(k).value) &&
          r.target.fold("")(_.value) == target.fold("")(_.value)
      )
