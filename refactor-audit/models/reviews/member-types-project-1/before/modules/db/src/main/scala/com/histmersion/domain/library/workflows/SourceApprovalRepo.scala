package com.histmersion.domain.library.workflows

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.domain.library.{Source, Edge}

private[workflows] object SourceApprovalRepo:
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
      result: Option[String],
      now: java.time.Instant
  )(using DbCon): Option[Long] =
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
      citekey: String,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[String]
  )(using DbCon): Boolean =
    sql"""SELECT 1 FROM approvals WHERE kind = ${kind.wire} AND citekey = $citekey
          AND coalesce(status, '') = ${status.fold("")(_.wire)}
          AND coalesce(edge_kind, '') = ${edgeKind.fold("")(Edge.Kind.wireOf)}
          AND coalesce(target, '') = ${target.getOrElse("")} LIMIT 1""".query[Int].run().nonEmpty
