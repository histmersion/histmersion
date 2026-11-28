package com.histmersion
package api.automation.listfilters

import sttp.tapir.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.ListFilters.many
import com.histmersion.api.automation.ListFilters.single
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.ListFilters.Term
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.ListFilters.OptionLabel
import com.histmersion.api.automation.ListFilters.words
import com.histmersion.api.automation.ListFilters.Qualifier
import com.histmersion.api.automation.ListFilters.entry
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Approval
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ApprovalFilter(
    state: Option[ApprovalFilter.State] = None,
    kind: List[ApprovalFilter.Kind] = Nil,
    subject: Option[ApprovalFilter.Subject] = None,
    goal: Option[Goal.Slug] = None,
    decidedBy: List[ApprovalFilter.DecidedBy] = Nil
) derives CanEqual:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def normalized: ApprovalFilter =
    ApprovalFilter(
      single(state, true)(_.value, ApprovalFilter.State(_)),
      many(kind, true)(_.value, ApprovalFilter.Kind(_)),
      single(subject)(_.value, ApprovalFilter.Subject(_)),
      single(goal)(_.value, Goal.Slug(_)),
      many(decidedBy, true)(_.value, ApprovalFilter.DecidedBy(_))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def qualifier: Option[Qualifier] =
    val n = normalized
    words(
      n.state.orElse(Some(Agents.ApprovalState.Open.wire)).map(_.value).filter(_ != "all").toList,
      (n.kind).map(refactorValue3 => (refactorValue3).value)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def options: List[(Term, Option[OptionLabel])] =
    val n = normalized
    List(entry("state", n.state.map(_.value))) ++ n.kind.map(k => entry("kind", Some(k.value))) ++ List(
      entry("subject", n.subject.map(_.value)),
      entry("goal", n.goal.map(_.value))
    ) ++
      n.decidedBy.map(d => entry("decided-by", Some(d.value)))

object ApprovalFilter:

  export Approval.State.Wire as State
  export Approval.Kind.Wire as Kind
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  export Approval.DecidedBy.Wire as DecidedBy
