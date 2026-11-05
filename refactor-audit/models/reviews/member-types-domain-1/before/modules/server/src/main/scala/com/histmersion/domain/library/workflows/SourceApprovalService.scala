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
      jobId: Option[String],
      kind: ApprovalKind,
      citekey: String,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[String],
      payload: String,
      state: ApprovalState,
      result: Option[String]
  ): Option[ApprovalInfo]

  def proposedBefore(
      kind: ApprovalKind,
      citekey: String,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[String]
  ): Boolean

object SourceApprovalService:
  def apply(db: Transactor, approvals: ApprovalService, clock: Clock): SourceApprovalService = Implementation(db, approvals, clock)

  private final class Implementation(db: Transactor, approvals: ApprovalService, clock: Clock) extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    def record(
        jobId: Option[String],
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String],
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): Option[ApprovalInfo] =
      db.transact(
        SourceApprovalRepo.record(jobId, kind, citekey, status, current, edgeKind, target, payload, state, result, now).flatMap(approvals.approvalIn(_))
      )

    def proposedBefore(
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String]
    ): Boolean = db.read(SourceApprovalRepo.proposedBefore(kind, citekey, status, edgeKind, target))

  final class InMemory(state: AtomicReference[AutomationState], approvals: ApprovalService, clock: Clock) extends SourceApprovalService:
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalState.Open.wire
    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)
    def record(
        jobId: Option[String],
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String],
        payload: String,
        state: ApprovalState,
        result: Option[String]
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
        citekey: String,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String]
    ): Boolean =
      rows.exists(r =>
        r.kind == kind.wire && r.citekey.contains(citekey) && r.status.getOrElse("") == status.fold("")(_.wire) &&
          r.edgeKind.getOrElse("") == edgeKind.fold("")(Edge.Kind.wireOf) && r.target.getOrElse("") == target
            .getOrElse("")
      )
