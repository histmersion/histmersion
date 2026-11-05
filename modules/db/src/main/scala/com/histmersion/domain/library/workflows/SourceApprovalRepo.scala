package com.histmersion.domain.library.workflows

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.domain.library.{Source, Edge}

private[workflows] object SourceApprovalRepo:
  type RecordResult = RecordResult.T
  object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordTarget = RecordTarget.T
  object RecordTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordPayload = RecordPayload.T
  object RecordPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposedBeforeResult = ProposedBeforeResult.T
  object ProposedBeforeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposedBeforeTarget = ProposedBeforeTarget.T
  object ProposedBeforeTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def record(
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalKind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[com.histmersion.domain.library.workflows.SourceApprovalRepo.RecordTarget],
      payload: com.histmersion.domain.library.workflows.SourceApprovalRepo.RecordPayload,
      state: ApprovalState,
      result: Option[String],
      now: java.time.Instant
  )(using DbCon): Option[com.histmersion.domain.library.workflows.SourceApprovalRepo.RecordResult] =
    val decided = Option.when(state != ApprovalState.Open)(now.toString)
    sql"""INSERT INTO approvals (job_id, kind, citekey, status, current_status, edge_kind, target, proposal, state,
                                 result, decided_at)
          VALUES ($jobId, ${kind.wire}, $citekey, ${status.map(_.wire)}, ${current.map(_.wire)},
                  ${edgeKind.map(Edge.Kind.wireOf)}, $target, $payload::jsonb, ${state.wire}, $result,
                  $decided::timestamptz)
          ON CONFLICT DO NOTHING RETURNING id"""
      .query[Long]
      .run()
      .headOption

  def proposedBefore(
      kind: ApprovalKind,
      citekey: com.histmersion.domain.library.Citekey,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[com.histmersion.domain.library.workflows.SourceApprovalRepo.ProposedBeforeTarget]
  )(using DbCon): com.histmersion.domain.library.workflows.SourceApprovalRepo.ProposedBeforeResult =
    sql"""SELECT 1 FROM approvals WHERE kind = ${kind.wire} AND citekey = $citekey
          AND coalesce(status, '') = ${status.fold("")(_.wire)}
          AND coalesce(edge_kind, '') = ${edgeKind.fold("")(Edge.Kind.wireOf)}
          AND coalesce(target, '') = ${target.getOrElse("")} LIMIT 1""".query[Int].run().nonEmpty
