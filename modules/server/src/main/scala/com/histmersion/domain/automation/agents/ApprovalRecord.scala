package com.histmersion
package domain.automation.agents

import java.time.Instant
import com.histmersion.domain.automation.Approval
import com.histmersion.util.jobs.Job
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** An approval as [[ApprovalService.InMemory]] holds it: its columns, the payload as written, and its times. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
    decidedAt: Option[Instant],
    creator: Option[Creator.New] = None
)

object ApprovalRecord:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type Seq = Seq.T
  object Seq extends Newtype[Int, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Current = Current.T
  object Current extends Newtype[String, Pure]
  type EdgeKind = EdgeKind.T
  object EdgeKind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type Proposal = Proposal.T
  object Proposal extends Newtype[String, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]
  export Approval.Note
  export Approval.Outcome as Result
