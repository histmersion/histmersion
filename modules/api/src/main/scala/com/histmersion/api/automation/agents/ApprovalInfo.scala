package com.histmersion
package api.automation.agents

import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Creation
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.managers.HintProposal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Display
import com.histmersion.Newtype
import com.histmersion.api

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ApprovalInfo(
    id: ApprovalInfo.Id,
    runId: Option[RunInfo.Id],
    kind: ApprovalInfo.Kind,
    citekey: Citekey,
    status: Option[ApprovalInfo.Status] = None,
    current: Option[ApprovalInfo.Current] = None,
    edgeKind: Option[ApprovalInfo.EdgeKind] = None,
    target: Option[ApprovalInfo.Target] = None,
    evidence: ApprovalInfo.Evidence = ApprovalInfo.Evidence(""),
    quote: Option[ApprovalInfo.Quote] = None,
    state: ApprovalInfo.State = Approval.State.Open.wire,
    note: Option[ApprovalInfo.Note] = None,
    result: Option[ApprovalInfo.Result] = None,
    createdAt: ApprovalInfo.CreatedAt = ApprovalInfo.CreatedAt(""),
    decidedAt: Option[ApprovalInfo.DecidedAt] = None,
    subject: Option[ApprovalInfo.Subject] = None,
    plan: Option[Goal.Plans.Plan] = None,
    article: Option[Goal.Plans.ArticleDraft] = None,
    filter: Option[Article.Plans.FilterProposal] = None,
    articles: Option[Article.Plans.ArticlePlan] = None,
    hintEffect: Option[HintProposal] = None,
    creator: Option[Creation] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema,
      CanEqual:
  def approvalKind: Option[Approval.Kind] = Approval.Kind.parse(kind)

  /** The approval with its payload read as the article proposal its kind carries (an article's coverage filter, a plan
    * of articles), its rationale as the evidence; None for a kind that carries none.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def withArticleProposal(payload: Option[Json]): Option[ApprovalInfo] =
    if approvalKind.exists((refactorCallback => ((refactorCallback.proposesFilter) == (true)))) then
      val f = payload.flatMap(_.as[Article.Plans.FilterProposal].toOption)
      Some(
        this
          .modify(_.filter)
          .setTo(f)
          .modify(_.evidence)
          .setTo(ApprovalInfo.Evidence(f.map(_.rationale.value).getOrElse("")))
      )
    else if approvalKind.exists((refactorCallback => ((refactorCallback.proposesArticles) == (true)))) then
      val p = payload.flatMap(_.as[Article.Plans.ArticlePlan].toOption)
      Some(
        this
          .modify(_.articles)
          .setTo(p)
          .modify(_.evidence)
          .setTo(ApprovalInfo.Evidence(p.map(_.rationale.value).getOrElse("")))
      )
    else None

object ApprovalInfo:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]:
    given Display[T] = Display(id => s"${id.value}")
  export Approval.Kind.Wire as Kind
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Current = Current.T
  object Current extends Newtype[String, Pure]
  export api.library.Edge.Kind.Wire as EdgeKind
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Evidence = Evidence.T
  object Evidence extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  export Approval.State.Wire as State
  export Approval.Note
  type Result = Result.T
  object Result extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type DecidedAt = DecidedAt.T
  object DecidedAt extends Newtype[String, Pure]
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
