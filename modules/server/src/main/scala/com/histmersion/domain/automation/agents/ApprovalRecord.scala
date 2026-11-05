package com.histmersion.domain.automation.agents

import java.time.Instant

/** An approval as [[ApprovalService.InMemory]] holds it: its columns, the payload as written, and its times. */
final case class ApprovalRecord(
    id: ApprovalRecord.Id,
    jobId: Option[com.histmersion.util.jobs.Job.Id],
    seq: Option[ApprovalRecord.Seq],
    kind: ApprovalRecord.Kind,
    citekey: Option[com.histmersion.domain.library.Citekey],
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
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Seq = Seq.T
  object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Current = Current.T
  object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeKind = EdgeKind.T
  object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Subject = Subject.T
  object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Proposal = Proposal.T
  object Proposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Result = Result.T
  object Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
