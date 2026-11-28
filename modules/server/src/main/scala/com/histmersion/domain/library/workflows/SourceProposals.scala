package com.histmersion
package domain.library.workflows

import com.histmersion.domain.Proposal
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import io.scalaland.chimney.dsl.*

import com.histmersion.api.automation.Agents.ApprovalState
import com.histmersion.api.automation.ListFilters
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.automation.agents.*

import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Edge
import com.histmersion.domain.automation.Run
import com.histmersion.util.jobs.Job
import com.histmersion.util.database.Jsonb
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What a run proposes about a source — a status, a disagreement edge — routed by the library's rules
  * ([[AgentProposals.route]]): applied by code (and recorded, already approved), recorded open for a person, or
  * skipped. `status` reads a source's status now; `apply` applies a proposal as a person's patch would.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class SourceProposals(
    approvals: SourceApprovalService,
    cfg: => AgentsConfig,
    status: Citekey => Option[Source.Status],
    apply: Proposal => Creator.New ?=> Either[Problems, SourceProposals.Outcome.Applied.What]
):
  def proposedBefore(p: Proposal): Boolean = approvals.proposedBefore(
    p.kind,
    p.citekey,
    p.status.transformInto[Option[domain.library.Source.Status]],
    p.edgeKind.transformInto[Option[Edge.Kind]],
    p.target
  )

  import SourceProposals.Outcome

  /** [[propose]] inside a run, through its write gate: a dry run records the proposal on the step and neither applies
    * nor records it.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def propose(ctx: RunCtx, p: Proposal)(using WritePermit)(using Creator.New): Outcome =
    ctx.write(s"${p.kind.wire} proposal: ${p.describe}", p.asJson)(propose(Some(Run.Id.assume(ctx.run.id.value)), p))(
      Outcome.NotProposed(Outcome.NotProposed.What(p.describe.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def record(
      runId: Option[Run.Id],
      p: Proposal,
      now: Option[Source.Status],
      state: ApprovalState,
      result: Option[ApprovalRecord.Result]
  )(using WritePermit)(using Creator.New) =
    approvals.record(
      runId.map(r => Job.Id(r.value)),
      p.kind,
      p.citekey,
      p.status.transformInto[Option[domain.library.Source.Status]],
      now.transformInto[Option[domain.library.Source.Status]],
      p.edgeKind.transformInto[Option[Edge.Kind]],
      p.target,
      Jsonb.write(p),
      state,
      result
    )

  /** Route one verified proposal: apply it now (and record it, already approved), or record it open. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def propose(runId: Option[Run.Id], p: Proposal)(using WritePermit)(using Creator.New): Outcome =
    // the status it had matters to a status change only (an edge leaves the status alone)
    val now = if p.kind == Approval.Kind.StatusChange then status(p.citekey) else None
    p.route(
      now.transformInto[Option[Source.Status]],
      cfg.approvals.statusMode.transformInto[Approval.Mode],
      cfg.approvals.edgeMode.transformInto[Approval.Mode]
    ) match
      case Proposal.Route.Skip(why) =>
        val said = why match
          case Proposal.Unneeded.Already(status)    => s"already ${status.fold("?")(_.wire)}"
          case Proposal.Unneeded.NotSourceChange(k) => s"${k.wire} is not a source change"
        Outcome.Skipped(Problem.Unclassified(Legacy.Text(s"${p.describe} ($said)")))
      case Proposal.Route.Apply =>
        apply(p) match
          case Left(m) =>
            Outcome.Failed(Problem.Unclassified(Legacy.Text(s"${p.describe}: ${m.sentences.mkString("; ")}")))
          case Right(w) =>
            Unused(
              record(
                runId,
                p,
                now,
                ApprovalState.Approved,
                Some(ApprovalRecord.Result(s"${ListFilters.DecidedBy.AutoResult}: $w"))
              ),
              Unused.Why.Reported
            )
            Outcome.Applied(w)
      case Proposal.Route.Ask =>
        record(runId, p, now, ApprovalState.Open, None) match
          case Some(a) => Outcome.Recorded(Outcome.Recorded.Id(a.id.value), Outcome.Recorded.What(p.describe.value))
          case None    => Outcome.AlreadyOpen(Outcome.AlreadyOpen.What(p.describe.value))

object SourceProposals:
  type ApprovalRecordId = ApprovalRecordId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ApprovalRecordId extends Newtype[Long, Pure]
  type OutcomeWhat = OutcomeWhat.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object OutcomeWhat extends Newtype[String, Pure]

  /** What became of one proposal, for the run's log and warnings. */
  enum Outcome:
    case Applied(what: Outcome.Applied.What)
    case Recorded(id: Outcome.Recorded.Id, what: Outcome.Recorded.What)
    case AlreadyOpen(what: Outcome.AlreadyOpen.What)
    case Skipped(why: Problem)
    case Failed(why: Problem)

    /** A dry run's proposal: neither recorded nor applied (#992). */
    case NotProposed(what: Outcome.NotProposed.What)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def line: String = this match
      case Applied(w)     => s"applied: $w"
      case Recorded(i, w) => s"approval $i waits for a person: $w"
      case AlreadyOpen(w) => s"already waiting for a person: $w"
      case Skipped(w)     => s"nothing to do: ${w.sentence}"
      case Failed(w)      => s"not applied: ${w.sentence}"
      case NotProposed(w) => s"${AgentsMarkdown.DryRunNote}: $w"

  object Outcome:
    object Applied:
      export SourceProposals.OutcomeWhat as What

    object Recorded:
      export SourceProposals.{ApprovalRecordId as Id, OutcomeWhat as What}

    object AlreadyOpen:
      export SourceProposals.OutcomeWhat as What

    object NotProposed:
      export SourceProposals.OutcomeWhat as What
