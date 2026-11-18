package com.histmersion
package domain.library.workflows

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.domain.library.{Edge, Source}
import com.histmersion.util.jobs.Job
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.ManagerPolicy

private[workflows] object SourceApprovalRepo:

  def record(
      jobId: Option[Job.Id],
      kind: ApprovalKind,
      citekey: Citekey,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[Citekey],
      payload: String,
      state: ApprovalState,
      result: Option[String],
      now: java.time.Instant
  )(using DbCon): Option[ManagerPolicy.ApprovalId] =
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
      .map(ManagerPolicy.ApprovalId(_))

  def proposedBefore(
      kind: ApprovalKind,
      citekey: Citekey,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[Citekey]
  )(using DbCon): Boolean =
    sql"""SELECT 1 FROM approvals WHERE kind = ${kind.wire} AND citekey = $citekey
          AND coalesce(status, '') = ${status.fold("")(_.wire.value)}
          AND coalesce(edge_kind, '') = ${edgeKind.fold("")(Edge.Kind.wireOf(_).value)}
          AND coalesce(target, '') = ${target.fold("")(_.value)} LIMIT 1""".query[Int].run().nonEmpty
