package com.histmersion.domain.automation.approvals

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.api.{Paging, Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*

import javax.sql.DataSource
import com.histmersion.api.automation.{AgentsApi, ListFilters, Managers}


import com.histmersion.api.Api

/** The approval gate (docs/research-embedded-agents.md §5.2): what an agent run PROPOSES — a status, a disagreement
  * edge, a filter, a plan — becomes an `approvals` row a person decides from the web UI or the CLI (the library routes
  * a source's proposals first: `domain.library.workflows.SourceProposals`). Approving applies it through the domain's
  * [[ApprovalEffects]], exactly the patch a person would send.
  *
  * A run does not wait for its approvals: it finishes, and the proposal stays open until decided.
  */
final class Approvals(
    store: ApprovalService,
    cfg: => AgentsConfig,
    effects: => ApprovalEffects,
    applyHint: Managers.HintProposal => Either[Approvals.ApplyHintResultPart1, Approvals.ApplyHintResultPart2] = _ => Left(com.histmersion.domain.automation.approvals.Approvals.ApplyHintResultPart1("no managers to apply it"))
):




  def list(state: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.State], limit: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Limit], page: Int): Either[Api.Error, List[ApprovalInfo]] =
    filter(state).map(st => store.approvals(st, page))

  /** One page of the approvals ([[Paging]]) that match `f` ([[ListFilters.ApprovalFilter]]: no state = the open ones),
    * with the per-state and the kind / decidedBy counts under the same filter.
    */
  def page(
      f: ListFilters.ApprovalFilter,
      limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
      before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
      upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
  ): Either[Api.Error, (List[ApprovalInfo], Paging.Page)] =
    for
      q <- com.histmersion.server.ListQueries.approvals(f)
      page <- store.approvalPage(q, limit, before, upTo).left.map(Api.Error(_, Some(Write.CodeNotFound)))
    yield
      val (rows, more) = page
      val (counts, facets) = store.approvalFacets(q)
      (rows, Paging.Page.filtered(counts, q.state.map(_.wire).toList, Option.when(more)(rows.last.id.toString), facets))

  /** The `state` query: none = the open ones, `all` = every state (None), else one state. */
  private def filter(state: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.State]): Either[Api.Error, Option[ApprovalState]] =
    state.map(_.trim).filter(_.nonEmpty) match
      case None        => Right(Some(ApprovalState.Open))
      case Some("all") => Right(None)
      case Some(s)     =>
        ApprovalState
          .parse(s)
          .map(Some(_))
          .toRight(
            Api.Error(s"unknown state '$s' (one of: ${(ApprovalState.values.map(_.wire) :+ "all").mkString(", ")})")
          )

  /** Approve (apply, then mark approved — or failed, with why) or reject an OPEN approval. 404 for an unknown id, 409
    * for one already decided. A BLOCKING approval (a goal's plan, an article to publish) is only decided here: the run
    * waiting on it applies it when it resumes (the caller wakes it).
    */
  def decide(id: com.histmersion.domain.automation.Approval.Id, decision: com.histmersion.domain.automation.Approval.Outcome, note: Option[com.histmersion.domain.automation.Approval.Note]): Either[Api.Error, ApprovalInfo] =
    val clean = note.map(_.trim).filter(_.nonEmpty).map(_.take(2000))
    for
      d <- Decision.parse(decision).toRight(Api.Error(s"unknown decision '$decision' (approve or reject)"))
      a <- store.approval(id).toRight(Api.Error(s"No approval $id", Some(Write.CodeNotFound)))
      _ <- Either.cond(
        a.state == ApprovalState.Open.wire,
        (),
        Api.Error(s"approval $id is already ${a.state}", Some(AgentsApi.CodeConflict))
      )
      out <- a.approvalKind match
        case Some(k) if k.blocking     => decideBlocking(a, d, clean)
        case Some(k) if k.aboutSubject => decideSubject(a, k, d, clean)
        case _                         => decideSource(id, d, clean)
    yield out

  /** A proposal about an article or a goal that no run waits for (managers, phase 2): an inferred coverage filter is
    * recorded on the article when approved; an approved plan of articles is the seeding manager's to compile (the
    * caller tells it). The decision is claimed first, as for a source.
    */
  private def decideSubject(
      a: ApprovalInfo,
      k: ApprovalKind,
      d: Decision,
      note: Option[com.histmersion.domain.automation.Approval.Note]
  ): Either[Api.Error, ApprovalInfo] =
    val to = if d == Decision.Approve then ApprovalState.Approved else ApprovalState.Rejected
    for
      _ <- Either.cond(
        store.decide(a.id, to, note, None),
        (),
        Api.Error(s"approval ${a.id} was decided meanwhile", Some(AgentsApi.CodeConflict))
      )
      _ = (d, k) match
        case (Decision.Reject, _)                        => store.settle(a.id, ApprovalState.Rejected, "rejected")
        case (Decision.Approve, ApprovalKind.HintEffect) =>
          a.hintEffect.toRight("the approval carries no readable action").flatMap(applyHint) match
            case Right(w) => store.settle(a.id, ApprovalState.Approved, s"applied: $w".take(1000))
            case Left(m)  => store.settle(a.id, ApprovalState.Failed, m.take(1000))
        case (Decision.Approve, k)
            if effects.subjectKinds(k.transformInto[com.histmersion.domain.automation.Approval.Kind]) =>
          effects.applySubject(a) match
            case Right(w) => store.settle(a.id, ApprovalState.Approved, s"applied: $w")
            case Left(m)  => store.settle(a.id, ApprovalState.Failed, m.take(1000))
        case (Decision.Approve, _) =>
          store.settle(
            a.id,
            ApprovalState.Approved,
            s"approved — the seeding manager ${Managers.seedId(a.subject.getOrElse("?"))} compiles " +
              Plural.count(a.articles.fold(0)(_.articles.size), "article")
          )
      out <- store.approval(a.id).toRight(Api.Error(s"No approval ${a.id}", Some(Write.CodeNotFound)))
    yield out

  private def decideBlocking(a: ApprovalInfo, d: Decision, note: Option[com.histmersion.domain.automation.Approval.Note]): Either[Api.Error, ApprovalInfo] =
    val (to, result) = d match
      case Decision.Approve => (ApprovalState.Approved, "approved — the run that proposed it continues and applies it")
      case Decision.Reject  => (ApprovalState.Rejected, "rejected — the run finishes without applying it")
    for
      _ <- Either.cond(
        store.decide(a.id, to, note, Some(result)),
        (),
        Api.Error(s"approval ${a.id} was decided meanwhile", Some(AgentsApi.CodeConflict))
      )
      out <- store.approval(a.id).toRight(Api.Error(s"No approval ${a.id}", Some(Write.CodeNotFound)))
    yield out

  private def decideSource(id: com.histmersion.domain.automation.Approval.Id, d: Decision, clean: Option[String]): Either[Api.Error, ApprovalInfo] =
    for
      apply <- store
        .payload(id)
        .flatMap(effects.proposal)
        .toRight(Api.Error(s"approval $id carries no readable proposal"))
      // claim the decision FIRST (compare-and-set on `open`), so two people approving at once apply
      // the change once; then apply it and record what happened
      to = if d == Decision.Approve then ApprovalState.Approved else ApprovalState.Rejected
      _ <- Either.cond(
        store.decide(id, to, clean, None),
        (),
        Api.Error(s"approval $id was decided meanwhile", Some(AgentsApi.CodeConflict))
      )
      _ = d match
        case Decision.Reject  => store.settle(id, ApprovalState.Rejected, "rejected")
        case Decision.Approve =>
          apply() match
            case Right(w) => store.settle(id, ApprovalState.Approved, s"applied: $w")
            case Left(m)  => store.settle(id, ApprovalState.Failed, m.take(1000))
      out <- store.approval(id).toRight(Api.Error(s"No approval $id", Some(Write.CodeNotFound)))
    yield out

object Approvals:
  type ApplyHintResultPart1 = ApplyHintResultPart1.T
  object ApplyHintResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyHintResultPart2 = ApplyHintResultPart2.T
  object ApplyHintResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

