package com.histmersion
package domain.automation.approvals
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.api.{Paging, Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*

import javax.sql.DataSource
import com.histmersion.api.automation.{AgentsApi, Goal, ListFilters, Managers}
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.domain.automation.Approval as DomainApproval

import com.histmersion.api.Api
import com.histmersion.server.ListQueries
import com.histmersion.domain.automation.Approval
import com.histmersion.util.database.WritePermit

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
    applyHint: Managers.HintProposal => Either[Problems, String] = _ =>
      Left(Problem.Config.Unavailable(Problem.Config.Capability.Agents))
):

  def list(
      state: Option[ApprovalQuery.State],
      limit: Option[ApprovalQuery.Limit],
      page: ApprovalQuery.Limit
  ): Either[Problems, List[ApprovalInfo]] =
    filter(state).map(st => store.approvals(st, page))

  /** One page of the approvals ([[Paging]]) that match `f` ([[ListFilters.ApprovalFilter]]: no state = the open ones),
    * with the per-state and the kind / decidedBy counts under the same filter.
    */
  def page(
      f: ListFilters.ApprovalFilter,
      limit: ApprovalQuery.Limit,
      before: Option[ApprovalQuery.Cursor],
      upTo: Option[ApprovalQuery.UpTo]
  ): Either[Api.Error, (List[ApprovalInfo], Paging.Page)] =
    for
      q <- ListQueries.approvals(f)
      page <- store
        .approvalPage(q, limit, before, upTo)
        .left
        .map(m => Api.Error(m, Some(Api.Error.Code(Write.CodeNotFound))))
    yield
      val (rows, more) = page
      val (counts, facets) = store.approvalFacets(q)
      (
        rows,
        Paging.Page.filtered(
          counts.map((s, n) => (Paging.Page.CountState(s.value), Paging.Count(n.value))),
          q.state.map(s => Paging.Page.CountState(s.wire.value)).toList,
          Option.when(more)(Filter.Last(rows.last.id.toString)),
          facets.map((name, vs) => (Facet.Name(name), vs.map((v, n) => (Facet.Value(v), Facet.Count(n.value)))))
        )
      )

  /** The `state` query: none = the open ones, `all` = every state (None), else one state. */
  private def filter(state: Option[ApprovalQuery.State]): Either[Problems, Option[ApprovalState]] =
    state.map(_.value.trim).filter(_.nonEmpty) match
      case None        => Right(Some(ApprovalState.Open))
      case Some("all") => Right(None)
      case Some(s)     =>
        ApprovalState
          .parse(ApprovalState.Wire(s))
          .map(Some(_))
          .toRight(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(ApprovalState.Wire),
              Problem.Raw(s),
              ApprovalState.values.toVector.map(st => Problem.Raw.of(st.wire)) :+ Problem.Raw("all")
            )
          )

  /** An approval whose stored payload this server cannot read. */
  private def unreadable(id: Problem.Raw): Problem =
    Problem.Io.Corrupt(Problem.Io.Format.Json, Problem.Where(s"approval $id"), Problem.Io.Damage.Undecodable)

  /** Approve (apply, then mark approved — or failed, with why) or reject an OPEN approval. 404 for an unknown id, 409
    * for one already decided. A BLOCKING approval (a goal's plan, an article to publish) is only decided here: the run
    * waiting on it applies it when it resumes (the caller wakes it).
    */
  def decide(
      id: Approval.Id,
      decision: Approval.Outcome,
      note: Option[Approval.Note]
  )(using WritePermit): Either[Api.Error, ApprovalInfo] =
    val clean = note.map(_.value.trim).filter(_.nonEmpty).map(n => DomainApproval.Note(n.take(2000)))
    for
      d <- Decision
        .parse(Decision.Wire(decision.value))
        .toRight(
          Api.Error(
            Problem.Invalid.Unknown.here(
              Problem.TypeName.of(Decision.Wire),
              Problem.Raw.of(decision),
              Decision.values.toVector.map(d => Problem.Raw.of(d.wire))
            )
          )
        )
      a <- store
        .approval(id)
        .toRight(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Approval.Id), Problem.Raw.of(id)),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      _ <- Either.cond(
        a.state == ApprovalState.Open.wire,
        (),
        Api.Error(
          Problem.Conflict.WrongState(
            Problem.TypeName.of(Approval.Id),
            Problem.Raw.of(id),
            Problem.Conflict.State.of(a.state),
            Vector(Problem.Conflict.State.of(ApprovalState.Open.wire))
          ),
          Some(Api.Error.Code(AgentsApi.CodeConflict))
        )
      )
      out <- a.approvalKind match
        case Some(k) if k.blocking.value => decideBlocking(a, d, clean)
        case Some(k) if k.aboutSubject   => decideSubject(a, k, d, clean)
        case _                           => decideSource(id, d, clean)
    yield out

  /** A proposal about an article or a goal that no run waits for (managers, phase 2): an inferred coverage filter is
    * recorded on the article when approved; an approved plan of articles is the seeding manager's to compile (the
    * caller tells it). The decision is claimed first, as for a source.
    */
  private def decideSubject(
      a: ApprovalInfo,
      k: ApprovalKind,
      d: Decision,
      note: Option[Approval.Note]
  )(using WritePermit): Either[Api.Error, ApprovalInfo] =
    val to = if d == Decision.Approve then ApprovalState.Approved else ApprovalState.Rejected
    val id = DomainApproval.Id(a.id.value)
    for
      _ <- Either.cond(
        store.decide(id, to, note, None),
        (),
        Api.Error(
          Problem.Conflict.WrongState(
            Problem.TypeName.of(Approval.Id),
            Problem.Raw.of(a.id),
            Problem.Conflict.State("decided"),
            Vector(Problem.Conflict.State.of(ApprovalState.Open.wire))
          ),
          Some(Api.Error.Code(AgentsApi.CodeConflict))
        )
      )
      _ = (d, k) match
        case (Decision.Reject, _) => store.settle(id, ApprovalState.Rejected, DomainApproval.Outcome("rejected"))
        case (Decision.Approve, ApprovalKind.HintEffect) =>
          a.hintEffect
            .toRight(unreadable(Problem.Raw.of(a.id)))
            .flatMap(applyHint) match
            case Right(w) => store.settle(id, ApprovalState.Approved, DomainApproval.Outcome(s"applied: $w".take(1000)))
            case Left(m)  =>
              store.settle(id, ApprovalState.Failed, DomainApproval.Outcome(m.sentences.mkString("; ").take(1000)))
        case (Decision.Approve, k) if effects.subjectKinds(k.transformInto[Approval.Kind]) =>
          effects.applySubject(a) match
            case Right(w) => store.settle(id, ApprovalState.Approved, DomainApproval.Outcome(s"applied: $w"))
            case Left(m)  => store.settle(id, ApprovalState.Failed, DomainApproval.Outcome(m.sentence.take(1000)))
        case (Decision.Approve, _) =>
          store.settle(
            id,
            ApprovalState.Approved,
            DomainApproval.Outcome(
              s"approved — the seeding manager ${Managers.seedId(Goal.Ref(a.subject.fold("?")(_.value)))} compiles " +
                Plural.count(
                  Plural.CountN(a.articles.fold(0)(_.articles.size).toLong),
                  Plural.CountSingular("article")
                )
            )
          )
      out <- store
        .approval(id)
        .toRight(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Approval.Id), Problem.Raw.of(a.id)),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
    yield out

  private def decideBlocking(
      a: ApprovalInfo,
      d: Decision,
      note: Option[Approval.Note]
  )(using WritePermit): Either[Api.Error, ApprovalInfo] =
    val (to, result) = d match
      case Decision.Approve => (ApprovalState.Approved, "approved — the run that proposed it continues and applies it")
      case Decision.Reject  => (ApprovalState.Rejected, "rejected — the run finishes without applying it")
    val id = DomainApproval.Id(a.id.value)
    for
      _ <- Either.cond(
        store.decide(id, to, note, Some(result)),
        (),
        Api.Error(
          Problem.Conflict.WrongState(
            Problem.TypeName.of(Approval.Id),
            Problem.Raw.of(a.id),
            Problem.Conflict.State("decided"),
            Vector(Problem.Conflict.State.of(ApprovalState.Open.wire))
          ),
          Some(Api.Error.Code(AgentsApi.CodeConflict))
        )
      )
      out <- store
        .approval(id)
        .toRight(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Approval.Id), Problem.Raw.of(a.id)),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
    yield out

  private def decideSource(
      id: Approval.Id,
      d: Decision,
      clean: Option[Approval.Note]
  )(using WritePermit): Either[Api.Error, ApprovalInfo] =
    for
      apply <- store
        .payload(id)
        .flatMap(p => effects.proposal(ApprovalEffects.Payload(p.value)))
        .toRight(Api.Error(unreadable(Problem.Raw.of(id))))
      // claim the decision FIRST (compare-and-set on `open`), so two people approving at once apply
      // the change once; then apply it and record what happened
      to = if d == Decision.Approve then ApprovalState.Approved else ApprovalState.Rejected
      _ <- Either.cond(
        store.decide(id, to, clean, None),
        (),
        Api.Error(
          Problem.Conflict.WrongState(
            Problem.TypeName.of(Approval.Id),
            Problem.Raw.of(id),
            Problem.Conflict.State("decided"),
            Vector(Problem.Conflict.State.of(ApprovalState.Open.wire))
          ),
          Some(Api.Error.Code(AgentsApi.CodeConflict))
        )
      )
      _ = d match
        case Decision.Reject  => store.settle(id, ApprovalState.Rejected, DomainApproval.Outcome("rejected"))
        case Decision.Approve =>
          apply() match
            case Right(w) => store.settle(id, ApprovalState.Approved, DomainApproval.Outcome(s"applied: $w"))
            case Left(m)  =>
              store.settle(id, ApprovalState.Failed, DomainApproval.Outcome(m.sentences.mkString("; ").take(1000)))
      out <- store
        .approval(id)
        .toRight(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Approval.Id), Problem.Raw.of(id)),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
    yield out
