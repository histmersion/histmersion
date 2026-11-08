package com.histmersion
package domain.automation.agents

import java.time.Instant
import com.histmersion.util.jobs.Job
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An approval as [[ApprovalService.InMemory]] holds it: its columns, the payload as written, and its times. */
final case class ApprovalRecord(
    id: ApprovalRecord.Id,
    jobId: Option[Job.Id],
    seq: Option[ApprovalRecord.Seq],
    kind: ApprovalRecord.Kind,
    citekey: Option[Citekey],
    status: Option[ApprovalRecord.Status],
    current: Option[ApprovalRecord.Current],
    edgeKind: Option[ApprovalRecord.EdgeKind],
    target: Option[ApprovalRecord.Target],
    subject: Option[ApprovalRecord.Subject],
    proposal: ApprovalRecord.Proposal,
    state: ApprovalRecord.State,
    note: Option[ApprovalRecord.Note],
    result: Option[ApprovalRecord.Result],
    createdAt: Instant,
    decidedAt: Option[Instant]
)

object ApprovalRecord:
  type Id = Id.T
  object Id extends RefinedType[Long, Pure]
  type Seq = Seq.T
  object Seq extends RefinedType[Int, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Current = Current.T
  object Current extends RefinedType[String, Pure]
  type EdgeKind = EdgeKind.T
  object EdgeKind extends RefinedType[String, Pure]
  type Target = Target.T
  object Target extends RefinedType[String, Pure]
  type Subject = Subject.T
  object Subject extends RefinedType[String, Pure]
  type Proposal = Proposal.T
  object Proposal extends RefinedType[String, Pure]
  type State = State.T
  object State extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  type Result = Result.T
  object Result extends RefinedType[String, Pure]
