package com.histmersion
package api.automation.listfilters

import sttp.tapir.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.ListFilters.many
import com.histmersion.api.automation.ListFilters.OptionLabel
import com.histmersion.api.automation.ListFilters.single
import com.histmersion.api.automation.ListFilters.Term
import com.histmersion.api.automation.ListFilters.words
import com.histmersion.api.automation.ListFilters.Qualifier
import com.histmersion.api.automation.ListFilters.entry
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Agents
import com.histmersion.Newtype
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.agents.Origin

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RunFilter(
    status: List[RunFilter.Status] = Nil,
    pipeline: List[RunFilter.Pipeline] = Nil,
    manager: List[RunFilter.Manager] = Nil,
    goal: Option[Goal.Slug] = None,
    model: List[RunFilter.Model] = Nil,
    provider: List[RunFilter.Provider] = Nil,
    origin: List[RunFilter.Origin] = Nil,
    since: Option[RunFilter.Since] = None,
    until: Option[RunFilter.Until] = None,
    subject: Option[RunFilter.Subject] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def qualifier: Option[Qualifier] = {
    val n = normalized;
    words(
      (n.status).map(refactorValue5 => (refactorValue5).value),
      (n.pipeline).map(refactorValue27 => (refactorValue27).value)
    )
  }
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def normalized: RunFilter =
    RunFilter(
      many(status, true)(_.value, RunFilter.Status(_)),
      many(pipeline, true)(_.value, RunFilter.Pipeline(_)),
      many(manager)(_.value, RunFilter.Manager(_)),
      single(goal)(_.value, Goal.Slug(_)),
      many(model)(_.value, RunFilter.Model(_)),
      many(provider, true)(_.value, RunFilter.Provider(_)),
      many(origin, true)(_.value, RunFilter.Origin(_)),
      single(since)(_.value, RunFilter.Since(_)),
      single(until)(_.value, RunFilter.Until(_)),
      single(subject)(_.value, RunFilter.Subject(_))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def options: List[(Term, Option[OptionLabel])] =
    val n = normalized
    n.status.map(v => entry("status", Some(v.value))) ++ n.pipeline.map(v =>
      entry("pipeline", Some(v.value))
    ) ++ n.manager.map(v => entry("manager", Some(v.value))) ++
      List(entry("goal", n.goal.map(_.value))) ++ n.model.map(v => entry("model", Some(v.value))) ++ n.provider.map(v =>
        entry("provider", Some(v.value))
      ) ++
      n.origin.map(v => entry("origin", Some(v.value))) ++ List(
        entry("since", n.since.map(_.value)),
        entry("until", n.until.map(_.value)),
        entry("subject", n.subject.map(_.value))
      )

object RunFilter:

  type Status = Status.T
  object Status extends Newtype[String, Pure]
  export RunInfo.Pipeline
  type Manager = Manager.T
  object Manager extends Newtype[String, Pure]
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  export Origin.Wire as Origin
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
