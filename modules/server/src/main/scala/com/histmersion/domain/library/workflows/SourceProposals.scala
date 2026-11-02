package com.histmersion.domain.library.workflows

import com.histmersion.api.Agents.ApprovalState
import com.histmersion.api.ListFilters
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.AgentProposals
import com.histmersion.domain.AgentProposals.{Proposal, Route}
import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.Approvals
import com.histmersion.values.{ApprovalKind, SourceStatus}
import io.circe.syntax.*

/** What a run proposes about a source — a status, a disagreement edge — routed by the library's rules
  * ([[AgentProposals.route]]): applied by code (and recorded, already approved), recorded open for a person, or
  * skipped. `status` reads a source's status now; `apply` applies a proposal as a person's patch would.
  */
final class SourceProposals(
    approvals: Approvals,
    cfg: => AgentsConfig,
    status: String => Option[SourceStatus],
    apply: Proposal => Either[String, String]
):
  import SourceProposals.Outcome

  /** [[propose]] inside a run, through its write gate: a dry run records the proposal on the step and neither applies
    * nor records it.
    */
  def propose(ctx: RunCtx, p: Proposal): Outcome =
    ctx.write(s"${p.kind.wire} proposal: ${p.describe}", p.asJson)(propose(Some(ctx.run.id), p))(
      Outcome.NotProposed(p.describe)
    )

  private def record(
      runId: Option[String],
      p: Proposal,
      now: Option[SourceStatus],
      state: ApprovalState,
      result: Option[String]
  ) =
    approvals.record(
      runId,
      p.kind,
      p.citekey,
      p.status,
      now,
      p.edgeKind,
      p.target,
      com.histmersion.db.Jsonb.write(p),
      state,
      result
    )

  /** Route one verified proposal: apply it now (and record it, already approved), or record it open. */
  def propose(runId: Option[String], p: Proposal): Outcome =
    // the status it had matters to a status change only (an edge leaves the status alone)
    val now = if p.kind == ApprovalKind.StatusChange then status(p.citekey) else None
    AgentProposals.route(p, now, cfg.approvals.statusMode, cfg.approvals.edgeMode) match
      case Route.Skip(why) => Outcome.Skipped(s"${p.describe} ($why)")
      case Route.Apply     =>
        apply(p) match
          case Left(m)  => Outcome.Failed(s"${p.describe}: $m")
          case Right(w) =>
            val _ = record(runId, p, now, ApprovalState.Approved, Some(s"${ListFilters.DecidedBy.AutoResult}: $w"))
            Outcome.Applied(w)
      case Route.Ask =>
        record(runId, p, now, ApprovalState.Open, None) match
          case Some(a) => Outcome.Recorded(a.id, p.describe)
          case None    => Outcome.AlreadyOpen(p.describe)

object SourceProposals:
  /** What became of one proposal, for the run's log and warnings. */
  enum Outcome:
    case Applied(what: String)
    case Recorded(id: Long, what: String)
    case AlreadyOpen(what: String)
    case Skipped(why: String)
    case Failed(why: String)

    /** A dry run's proposal: neither recorded nor applied (#992). */
    case NotProposed(what: String)

    def line: String = this match
      case Applied(w)     => s"applied: $w"
      case Recorded(i, w) => s"approval $i waits for a person: $w"
      case AlreadyOpen(w) => s"already waiting for a person: $w"
      case Skipped(w)     => s"nothing to do: $w"
      case Failed(w)      => s"not applied: $w"
      case NotProposed(w) => s"${com.histmersion.api.AgentsMarkdown.DryRunNote}: $w"
