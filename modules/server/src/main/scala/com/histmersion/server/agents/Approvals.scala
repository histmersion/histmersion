package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.AgentProposals.{Proposal, Route}
import com.histmersion.db.AgentRepo
import com.histmersion.server.Handlers

import javax.sql.DataSource

/** The approval gate (docs/research-embedded-agents.md §5.2): what an agent run PROPOSES — a status,
  * a disagreement edge — is applied by code only under the rules of [[AgentProposals.route]];
  * otherwise it becomes an `approvals` row a person decides from the web UI or the CLI. Approving
  * applies it through [[Handlers.patchSource]], exactly the patch a person would send.
  *
  * A run does not wait for its approvals: it finishes, and the proposal stays open until decided. */
final class Approvals(h: Handlers, ds: DataSource, cfg: AgentsConfig):
  import Approvals.Outcome


  private def current(ck: String): Option[SourceStatus] = h.getSource(ck, body = false).toOption.map(_.source.status)

  private def apply(p: Proposal): Either[String, String] =
    h.patchSource(p.citekey, p.patch).left.map(_.message).map(_ => p.describe)

  /** [[propose]] inside a run, through its write gate: a dry run records the proposal on the step and
    * neither applies nor records it. */
  def propose(ctx: RunCtx, p: Proposal): Outcome =
    import io.circe.syntax.*
    ctx.write(s"${p.kind.wire} proposal: ${p.describe}", p.asJson)(propose(Some(ctx.run.id), p))(Outcome.NotProposed(p.describe))

  /** Route one verified proposal: apply it now (and record it, already approved), or record it open. */
  def propose(runId: Option[String], p: Proposal): Outcome =
    // the status it had matters to a status change only (an edge leaves the status alone)
    val now = if p.kind == ApprovalKind.StatusChange then current(p.citekey) else None
    AgentProposals.route(p, now, cfg.approvals.statusMode, cfg.approvals.edgeMode) match
      case Route.Skip(why) => Outcome.Skipped(s"${p.describe} ($why)")
      case Route.Apply =>
        apply(p) match
          case Left(m) => Outcome.Failed(s"${p.describe}: $m")
          case Right(w) =>
            val _ = AgentRepo.propose(ds, runId, p, now, ApprovalState.Approved, Some(s"applied automatically: $w"))
            Outcome.Applied(w)
      case Route.Ask =>
        AgentRepo.propose(ds, runId, p, now) match
          case Some(a) => Outcome.Recorded(a.id, p.describe)
          case None    => Outcome.AlreadyOpen(p.describe)

  def list(state: Option[String], limit: Option[Int], page: Int): Either[ApiError, List[ApprovalInfo]] =
    filter(state).map(st => AgentRepo.approvals(ds, st, page))

  /** One page of the approvals ([[Paging]]) in `state` (as [[list]]), with every state's count. */
  def page(state: Option[String], limit: Int, before: Option[String], upTo: Option[String])
      : Either[ApiError, (List[ApprovalInfo], Paging.Page)] =
    for
      st   <- filter(state)
      page <- AgentRepo.approvalPage(ds, st, limit, before, upTo).left.map(ApiError(_, Some(Write.CodeNotFound)))
    yield
      val (rows, more) = page
      (rows, Paging.Page.of(AgentRepo.approvalCounts(ds), st.map(_.wire), Option.when(more)(rows.last.id.toString)))

  /** The `state` query: none = the open ones, `all` = every state (None), else one state. */
  private def filter(state: Option[String]): Either[ApiError, Option[ApprovalState]] =
    state.map(_.trim).filter(_.nonEmpty) match
      case None        => Right(Some(ApprovalState.Open))
      case Some("all") => Right(None)
      case Some(s) =>
        ApprovalState.parse(s).map(Some(_))
          .toRight(ApiError(s"unknown state '$s' (one of: ${(ApprovalState.values.map(_.wire) :+ "all").mkString(", ")})"))

  /** Approve (apply, then mark approved — or failed, with why) or reject an OPEN approval. 404 for an
    * unknown id, 409 for one already decided. A BLOCKING approval (a goal's plan, an article to
    * publish) is only decided here: the run waiting on it applies it when it resumes (the caller
    * wakes it). */
  def decide(id: Long, decision: String, note: Option[String]): Either[ApiError, ApprovalInfo] =
    val clean = note.map(_.trim).filter(_.nonEmpty).map(_.take(2000))
    for
      d <- Decision.parse(decision).toRight(ApiError(s"unknown decision '$decision' (approve or reject)"))
      a <- AgentRepo.approval(ds, id).toRight(ApiError(s"No approval $id", Some(Write.CodeNotFound)))
      _ <- Either.cond(a.state == ApprovalState.Open.wire, (),
             ApiError(s"approval $id is already ${a.state}", Some(AgentsApi.CodeConflict)))
      out <- if a.approvalKind.exists(_.blocking) then decideBlocking(a, d, clean) else decideSource(id, d, clean)
    yield out

  private def decideBlocking(a: ApprovalInfo, d: Decision, note: Option[String]): Either[ApiError, ApprovalInfo] =
    val (to, result) = d match
      case Decision.Approve => (ApprovalState.Approved, "approved — the run that proposed it continues and applies it")
      case Decision.Reject  => (ApprovalState.Rejected, "rejected — the run finishes without applying it")
    for
      _ <- Either.cond(AgentRepo.decide(ds, a.id, to, note, Some(result)), (),
             ApiError(s"approval ${a.id} was decided meanwhile", Some(AgentsApi.CodeConflict)))
      out <- AgentRepo.approval(ds, a.id).toRight(ApiError(s"No approval ${a.id}", Some(Write.CodeNotFound)))
    yield out

  private def decideSource(id: Long, d: Decision, clean: Option[String]): Either[ApiError, ApprovalInfo] =
    for
      p <- AgentRepo.proposal(ds, id).toRight(ApiError(s"approval $id carries no readable proposal"))
      // claim the decision FIRST (compare-and-set on `open`), so two people approving at once apply
      // the change once; then apply it and record what happened
      to = if d == Decision.Approve then ApprovalState.Approved else ApprovalState.Rejected
      _ <- Either.cond(AgentRepo.decide(ds, id, to, clean, None), (),
             ApiError(s"approval $id was decided meanwhile", Some(AgentsApi.CodeConflict)))
      _ = d match
            case Decision.Reject => AgentRepo.settle(ds, id, ApprovalState.Rejected, "rejected")
            case Decision.Approve =>
              apply(p) match
                case Right(w) => AgentRepo.settle(ds, id, ApprovalState.Approved, s"applied: $w")
                case Left(m)  => AgentRepo.settle(ds, id, ApprovalState.Failed, m.take(1000))
      out <- AgentRepo.approval(ds, id).toRight(ApiError(s"No approval $id", Some(Write.CodeNotFound)))
    yield out

object Approvals:
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
      case NotProposed(w) => s"${com.histmersion.core.AgentsMarkdown.DryRunNote}: $w"
