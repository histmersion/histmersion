package com.histmersion.domain.library.workflows

import io.scalaland.chimney.dsl.*

import com.histmersion.api.automation.Agents.ApprovalState
import com.histmersion.api.automation.ListFilters
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.AgentProposals
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.AgentProposals.Route
import com.histmersion.domain.automation.agents.*

import io.circe.syntax.*
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Source

/** What a run proposes about a source — a status, a disagreement edge — routed by the library's rules
  * ([[AgentProposals.route]]): applied by code (and recorded, already approved), recorded open for a person, or
  * skipped. `status` reads a source's status now; `apply` applies a proposal as a person's patch would.
  */
final class SourceProposals(
    approvals: SourceApprovalService,
    cfg: => AgentsConfig,
    status: com.histmersion.domain.library.Citekey => Option[Source.Status],
    apply: Proposal => Either[SourceProposals.Outcome.Failed.Why, SourceProposals.Outcome.Applied.What]
):
  def proposedBefore(p: Proposal): Boolean = approvals.proposedBefore(p.kind, p.citekey, p.status.transformInto[Option[com.histmersion.domain.library.Source.Status]], p.edgeKind.transformInto[Option[com.histmersion.domain.library.Edge.Kind]], p.target)

  import SourceProposals.Outcome

  /** [[propose]] inside a run, through its write gate: a dry run records the proposal on the step and neither applies
    * nor records it.
    */
  def propose(ctx: RunCtx, p: Proposal): Outcome =
    ctx.write(s"${p.kind.wire} proposal: ${p.describe}", p.asJson)(propose(Some(com.histmersion.domain.automation.Run.Id.assume(ctx.run.id.value)), p))(
      Outcome.NotProposed(Outcome.NotProposed.What(p.describe.value))
    )

  private def record(
      runId: Option[com.histmersion.domain.automation.Run.Id],
      p: Proposal,
      now: Option[Source.Status],
      state: ApprovalState,
      result: Option[ApprovalRecord.Result]
  ) =
    approvals.record(
      runId.map(r => com.histmersion.util.jobs.Job.Id(r.value)),
      p.kind,
      p.citekey,
      p.status.transformInto[Option[com.histmersion.domain.library.Source.Status]],
      now.transformInto[Option[com.histmersion.domain.library.Source.Status]],
      p.edgeKind.transformInto[Option[com.histmersion.domain.library.Edge.Kind]],
      p.target,
      com.histmersion.util.database.Jsonb.write(p),
      state,
      result
    )

  /** Route one verified proposal: apply it now (and record it, already approved), or record it open. */
  def propose(runId: Option[com.histmersion.domain.automation.Run.Id], p: Proposal): Outcome =
    // the status it had matters to a status change only (an edge leaves the status alone)
    val now = if p.kind == Approval.Kind.StatusChange then status(p.citekey) else None
    AgentProposals.route(p, now.transformInto[Option[com.histmersion.api.library.Source.Status]], cfg.approvals.statusMode.transformInto[com.histmersion.api.automation.Approval.Mode], cfg.approvals.edgeMode.transformInto[com.histmersion.api.automation.Approval.Mode]) match
      case Route.Skip(why) => Outcome.Skipped(Outcome.Skipped.Why(s"${p.describe} ($why)"))
      case Route.Apply     =>
        apply(p) match
          case Left(m)  => Outcome.Failed(Outcome.Failed.Why(s"${p.describe}: $m"))
          case Right(w) =>
            val _ = record(runId, p, now, ApprovalState.Approved, Some(ApprovalRecord.Result(s"${ListFilters.DecidedBy.AutoResult}: $w")))
            Outcome.Applied(w)
      case Route.Ask =>
        record(runId, p, now, ApprovalState.Open, None) match
          case Some(a) => Outcome.Recorded(Outcome.Recorded.Id(a.id.value), Outcome.Recorded.What(p.describe.value))
          case None    => Outcome.AlreadyOpen(Outcome.AlreadyOpen.What(p.describe.value))

object SourceProposals:
  type ApprovalRecordId = ApprovalRecordId.T
  object ApprovalRecordId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutcomeWhat = OutcomeWhat.T
  object OutcomeWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutcomeWhy = OutcomeWhy.T
  object OutcomeWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What became of one proposal, for the run's log and warnings. */
  enum Outcome:
    case Applied(what: Outcome.Applied.What)
    case Recorded(id: Outcome.Recorded.Id, what: Outcome.Recorded.What)
    case AlreadyOpen(what: Outcome.AlreadyOpen.What)
    case Skipped(why: Outcome.Skipped.Why)
    case Failed(why: Outcome.Failed.Why)

    /** A dry run's proposal: neither recorded nor applied (#992). */
    case NotProposed(what: Outcome.NotProposed.What)

    def line: String = this match
      case Applied(w)     => s"applied: $w"
      case Recorded(i, w) => s"approval $i waits for a person: $w"
      case AlreadyOpen(w) => s"already waiting for a person: $w"
      case Skipped(w)     => s"nothing to do: $w"
      case Failed(w)      => s"not applied: $w"
      case NotProposed(w) => s"${com.histmersion.api.automation.AgentsMarkdown.DryRunNote}: $w"

  object Outcome:
    object Applied:
      type What = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat
      val What: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat

    object Recorded:
      type Id = com.histmersion.domain.library.workflows.SourceProposals.ApprovalRecordId
      val Id: _root_.com.histmersion.domain.library.workflows.SourceProposals.ApprovalRecordId.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.ApprovalRecordId
      type What = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat
      val What: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat

    object AlreadyOpen:
      type What = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat
      val What: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat

    object Skipped:
      type Why = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy
      val Why: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy

    object Failed:
      type Why = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy
      val Why: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhy

    object NotProposed:
      type What = com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat
      val What: _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat.type = _root_.com.histmersion.domain.library.workflows.SourceProposals.OutcomeWhat

