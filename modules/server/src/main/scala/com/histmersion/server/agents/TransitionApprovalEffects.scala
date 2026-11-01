package com.histmersion.server.agents

import com.histmersion.api.Agents.ApprovalInfo
import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.domain.automation.approvals.ApprovalEffects
import com.histmersion.server.Handlers
import com.histmersion.values.ApprovalKind

import javax.sql.DataSource

/** What approving a source's proposal (the library's) and an article's inferred filter (research's) does, registered by
  * this transition adapter until those domains contribute their own.
  */
final class TransitionApprovalEffects(h: Handlers, ds: DataSource) extends ApprovalEffects:
  def proposal(payload: String): Option[() => Either[String, String]] =
    com.histmersion.db.Jsonb
      .read[Proposal](payload)
      .map(p => () => h.library.sources.patch(p.citekey, p.patch).left.map(_.message).map(_ => p.describe))

  val subjectKinds: Set[ApprovalKind] = Set(ApprovalKind.ArticleFilter)

  def applySubject(a: ApprovalInfo): Either[String, String] =
    a.filter
      .toRight("the approval carries no readable filter")
      .flatMap(InferFilterPipeline.applyProposal(h, ds, _))
