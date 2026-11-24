package com.histmersion
package domain.library.workflows

import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState}
import com.histmersion.domain.library.{Edge, Source}
import com.histmersion.domain.automation.AutomationState
import com.histmersion.domain.automation.agents.{ApprovalRecord, ApprovalService}
import com.histmersion.util.database.Transactor
import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.jobs.Job
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.Jsonb
import com.histmersion.domain.automation.Approval
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait SourceApprovalService:
  def record(
      jobId: Option[Job.Id],
      kind: ApprovalKind,
      citekey: Citekey,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[Citekey],
      payload: Jsonb.JsonText,
      state: ApprovalState,
      result: Option[ApprovalRecord.Result]
  )(using WritePermit)(using Creator.New): Option[ApprovalInfo]

  def proposedBefore(
      kind: ApprovalKind,
      citekey: Citekey,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[Citekey]
  ): Boolean

object SourceApprovalService:
  def apply(db: Transactor, approvals: ApprovalService, clock: Clock): SourceApprovalService =
    Implementation(db, approvals, clock)

  final private class Implementation(db: Transactor, approvals: ApprovalService, clock: Clock)
      extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def record(
        jobId: Option[Job.Id],
        kind: ApprovalKind,
        citekey: Citekey,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[Citekey],
        payload: Jsonb.JsonText,
        state: ApprovalState,
        result: Option[ApprovalRecord.Result]
    )(using WritePermit)(using Creator.New): Option[ApprovalInfo] =
      db.write(
        SourceApprovalRepo
          .record(
            jobId,
            kind,
            citekey,
            status,
            current,
            edgeKind,
            target,
            payload.value,
            state,
            result.map(_.value),
            now
          )
          .flatMap(id => approvals.approvalIn(Approval.Id(id.value)))
      )

    def proposedBefore(
        kind: ApprovalKind,
        citekey: Citekey,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[Citekey]
    ): Boolean = db.read(SourceApprovalRepo.proposedBefore(kind, citekey, status, edgeKind, target))

  final class InMemory(state: AtomicReference[AutomationState], approvals: ApprovalService, clock: Clock)
      extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private val Open = ApprovalRecord.State(ApprovalState.Open.wire.value)
    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def record(
        jobId: Option[Job.Id],
        kind: ApprovalKind,
        citekey: Citekey,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[Citekey],
        payload: Jsonb.JsonText,
        state: ApprovalState,
        result: Option[ApprovalRecord.Result]
    )(using WritePermit)(using Creator.New): Option[ApprovalInfo] =
      val (st, ek) = (
        status.map(s => ApprovalRecord.Status(s.wire.value)),
        edgeKind.map(k => ApprovalRecord.EdgeKind(Edge.Kind.wireOf(k).value))
      )
      def same(r: ApprovalRecord) =
        state == ApprovalState.Open && r.state == Open && r.kind.value == kind.wire.value && r.citekey.contains(
          citekey
        ) &&
          r.status.fold("")(_.value) == st.fold("")(_.value) && r.edgeKind.fold("")(_.value) == ek.fold("")(_.value) &&
          r.target.fold("")(_.value) == target.fold("")(_.value)
      val at = now
      AutomationState
        .insertApproval(this.state)(same)(id =>
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
            decidedAt(state),
            Some(summon[Creator.New])
          )
        )
        .flatMap(approvals.approval)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def proposedBefore(
        kind: ApprovalKind,
        citekey: Citekey,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[Citekey]
    ): Boolean =
      rows.exists(r =>
        r.kind.value == kind.wire.value && r.citekey.contains(citekey) &&
          r.status.fold("")(_.value) == status.fold("")(_.wire.value) &&
          r.edgeKind.fold("")(_.value) == edgeKind.fold("")(k => Edge.Kind.wireOf(k).value) &&
          r.target.fold("")(_.value) == target.fold("")(_.value)
      )
